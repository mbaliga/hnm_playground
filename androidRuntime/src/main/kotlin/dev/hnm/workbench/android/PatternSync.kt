package dev.hnm.workbench.android

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.google.android.gms.wearable.*
import dev.hnm.workbench.core.companion.PatternTransfer
import dev.hnm.workbench.core.companion.PatternSafety
import dev.hnm.workbench.core.ir.HapticAudioPattern
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** Explicit durable revision delivery; availability is not a receipt and receipt is not playback. */
class PatternSync(private val context: Context) {
    val connection = MutableStateFlow("Checking companion…")
    val status = MutableStateFlow("Ready to send a saved revision")
    private val prefs = context.getSharedPreferences("hafx_delivery", Context.MODE_PRIVATE)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshStatus() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener); refreshStatus() }
    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }

    suspend fun send(pattern: HapticAudioPattern) {
        val bytes = PatternTransfer.encode(pattern)
        val revision = pattern.metadata.getValue(PatternTransfer.REVISION)
        val request = PutDataMapRequest.create(PatternTransfer.PATTERN_PATH + revision).apply {
            dataMap.putAsset("pattern", Asset.createFromBytes(bytes))
            dataMap.putString("sha256", sha256(bytes))
            dataMap.putString("senderRole", role(context))
        }.asPutDataRequest().setUrgent()
        status.value = "Queueing revision…"
        try {
            Wearable.getDataClient(context).putDataItem(request).await()
            prefs.edit().putString("lastSent", revision).apply()
            refreshStatus()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("HAfxSync", "Could not queue companion revision", e)
            val message=if(e is com.google.android.gms.common.api.ApiException)
                "Companion service unavailable. Check Wear OS pairing and Google Play services, then try again."
                else "Could not send this effect. Check the connection and try again."
            status.value=message
            throw IllegalStateException(message,e)
        }
    }

    /** Also recovers items delivered before installation or while a listener was unavailable. */
    suspend fun reconcile() {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            connection.value = if(nodes.isEmpty()) "No companion connected. Pair your devices with Wear OS; queued effects can arrive later."
                else "Connected: " + nodes.joinToString { it.displayName }
            val buffer = Wearable.getDataClient(context).dataItems.await()
            val items = try { buffer.map { it.uri.path.orEmpty() to DataMapItem.fromDataItem(it).dataMap } } finally { buffer.release() }
            for ((path, map) in items) process(context,path,map)
            refreshStatus()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { connection.value = "Companion unavailable. Local creation and saves still work. Check Wear OS pairing and Google Play services." }
    }

    fun refreshStatus() {
        val revision = prefs.getString("lastSent", null) ?: return
        status.value = prefs.getString("receipt.$revision", null) ?: "Queued — waiting for the other device to save it"
    }

    companion object {
        private fun role(context: Context) = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) "watch" else "phone"
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        suspend fun process(context: Context, path: String, data: DataMap) {
            val prefs = context.getSharedPreferences("hafx_delivery", Context.MODE_PRIVATE)
            if (path.startsWith(PatternTransfer.RECEIPT_PATH)) {
                val revision = data.getString("revision") ?: return
                val expectedRole = if(role(context) == "watch") "phone" else "watch"
                if (!PatternTransfer.validId(revision) || path != PatternTransfer.RECEIPT_PATH + revision + "/" + expectedRole) return
                val result = data.getString("result")?.take(180) ?: return
                prefs.edit().putString("receipt.$revision", result).apply()
                return
            }
            if (!path.startsWith(PatternTransfer.PATTERN_PATH)) return
            val revision = path.removePrefix(PatternTransfer.PATTERN_PATH)
            if (!PatternTransfer.validId(revision) || data.getString("senderRole") == role(context)) return
            val asset = data.getAsset("pattern") ?: return
            val result = try {
                withTimeout(15_000) {
                    val response = Wearable.getDataClient(context).getFdForAsset(asset).await()
                    val bytes = withContext(Dispatchers.IO) { response.inputStream.use { it.readBounded(PatternSafety.MAX_BYTES) } }
                    require(bytes.size <= PatternSafety.MAX_BYTES) { "Effect exceeds 64 KiB" }
                    require(sha256(bytes) == data.getString("sha256")) { "Transfer checksum mismatch" }
                    val pattern = PatternTransfer.decode(bytes)
                    require(pattern.metadata[PatternTransfer.REVISION] == revision) { "Revision mismatch" }
                    withContext(Dispatchers.Main.immediate) {
                        // A locally deleted received revision stays deleted when Data Layer replays it.
                        if (!prefs.getBoolean("received.$revision", false)) {
                            AndroidWorkspaceStore.get(context).library.receive(pattern)
                            check(prefs.edit().putBoolean("received.$revision", true).commit()) { "Could not record delivery" }
                        }
                    }
                    "Saved on ${role(context)}"
                }
            } catch (e: TimeoutCancellationException) { "Not saved — transfer timed out; reopen HAfx to retry" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { "Rejected — ${e.message?.take(120)}" }
            val receipt = PutDataMapRequest.create(PatternTransfer.RECEIPT_PATH + revision + "/" + role(context)).apply {
                dataMap.putString("revision", revision); dataMap.putString("result", result)
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(receipt).await()
        }
    }
}

class PatternSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // WearableListenerService invokes this on its background thread. Finish before releasing
        // the callback; launching an orphan coroutine could lose the write when the service stops.
        runBlocking {
            events.filter { it.type == DataEvent.TYPE_CHANGED }.forEach { event ->
                runCatching { PatternSync.process(this@PatternSyncService, event.dataItem.uri.path.orEmpty(), DataMapItem.fromDataItem(event.dataItem).dataMap) }
            }
        }
    }
}

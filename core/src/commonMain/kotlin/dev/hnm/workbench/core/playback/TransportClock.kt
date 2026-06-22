package dev.hnm.workbench.core.playback

import dev.hnm.workbench.core.ir.Seconds
import kotlin.time.TimeSource

/**
 * One shared clock keeps audio and haptics coincident (§6, the simultaneity test). Every backend
 * schedules against the same transport and applies its own [latencyComp] so actions land aligned at
 * the *output*, not at the call site.
 */
interface TransportClock {
    val positionSeconds: Seconds
    val isPlaying: Boolean
    fun start(at: Seconds = 0.0)
    fun stop()

    /** latencyComp shifts a backend's action earlier so audio + haptics align at the output. */
    fun scheduleAt(time: Seconds, latencyComp: Seconds, action: () -> Unit)
}

/**
 * A transport with a pluggable time source so it is deterministic in tests and wall-clock-driven in
 * production. Call [pump] from a host loop (audio callback, coroutine, or a test) to fire due actions.
 */
class DefaultTransportClock(
    private val now: () -> Seconds = WallClock,
) : TransportClock {

    private class Scheduled(val fireAt: Seconds, val action: () -> Unit, var fired: Boolean = false)

    private var startWall: Seconds = 0.0
    private var playing: Boolean = false
    private val scheduled = mutableListOf<Scheduled>()

    override val positionSeconds: Seconds
        get() = if (playing) now() - startWall else 0.0

    override val isPlaying: Boolean get() = playing

    override fun start(at: Seconds) {
        startWall = now() - at
        playing = true
        // Re-arm anything already scheduled relative to the new start.
        scheduled.forEach { it.fired = false }
    }

    override fun stop() {
        playing = false
        scheduled.clear()
    }

    override fun scheduleAt(time: Seconds, latencyComp: Seconds, action: () -> Unit) {
        // Compensate by firing earlier; never schedule before zero.
        val fireAt = maxOf(0.0, time - latencyComp)
        scheduled.add(Scheduled(fireAt, action))
    }

    /**
     * Fire every action whose compensated time has passed. Idempotent per action.
     * @return number of actions fired on this pump.
     */
    fun pump(): Int {
        if (!playing) return 0
        val pos = positionSeconds
        var count = 0
        for (s in scheduled) {
            if (!s.fired && pos >= s.fireAt) {
                s.fired = true
                s.action()
                count++
            }
        }
        return count
    }

    /** True once every scheduled action has fired. */
    fun isDrained(): Boolean = scheduled.all { it.fired }

    companion object {
        /** Monotonic wall clock in seconds, multiplatform via kotlin.time. */
        val WallClock: () -> Seconds = run {
            val origin = TimeSource.Monotonic.markNow()
            ({ origin.elapsedNow().inWholeMicroseconds / 1_000_000.0 })
        }
    }
}

/**
 * A manual clock for deterministic tests and offline rendering: [advanceTo]/[advanceBy] move time
 * forward explicitly and fire any due actions. No wall-clock dependency.
 */
class ManualTransportClock : TransportClock {
    private var pos: Seconds = 0.0
    private var playing = false
    private val scheduled = mutableListOf<Pair<Seconds, () -> Unit>>()
    private val fired = mutableSetOf<Int>()

    override val positionSeconds: Seconds get() = pos
    override val isPlaying: Boolean get() = playing

    override fun start(at: Seconds) {
        pos = at
        playing = true
    }

    override fun stop() {
        playing = false
        scheduled.clear()
        fired.clear()
        pos = 0.0
    }

    override fun scheduleAt(time: Seconds, latencyComp: Seconds, action: () -> Unit) {
        scheduled.add(maxOf(0.0, time - latencyComp) to action)
    }

    fun advanceTo(t: Seconds): Int {
        pos = t
        var count = 0
        scheduled.forEachIndexed { i, (fireAt, action) ->
            if (i !in fired && pos >= fireAt) {
                fired.add(i)
                action()
                count++
            }
        }
        return count
    }

    fun advanceBy(dt: Seconds): Int = advanceTo(pos + dt)
}

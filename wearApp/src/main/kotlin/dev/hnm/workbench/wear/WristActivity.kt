package dev.hnm.workbench.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.*
import dev.hnm.workbench.core.companion.PatternSafety
import dev.hnm.workbench.core.library.StarterEffects
import dev.hnm.workbench.core.library.EffectAttribution
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WristActivity : ComponentActivity() {
    private lateinit var state: WristState
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = WristState(this)
        setContent { WristApp(state) }
    }
    override fun onResume() { super.onResume(); if (::state.isInitialized) state.refreshSync() }
    override fun onStop() { if (::state.isInitialized) state.pause(); super.onStop() }
    override fun onDestroy() { if (::state.isInitialized) state.close(); super.onDestroy() }
}

@Composable
private fun WristApp(state: WristState) {
    val nav = rememberSwipeDismissableNavController()
    val uri = LocalUriHandler.current
    val playback by state.player.status.collectAsState()
    val libraryVersion by state.store.version.collectAsState()
    val connection by state.sync.connection.collectAsState()
    val syncStatus by state.sync.status.collectAsState()
    MaterialTheme(colors = Colors(primary = Color(0xFFB9EEE0), onPrimary = Color(0xFF0A3028), background = Color(0xFF080E12))) {
        CompositionLocalProvider(LocalPlaying provides playback.active) {
        Box(Modifier.fillMaxSize()) {
        SwipeDismissableNavHost(nav, startDestination = "home") {
            composable("home") {
                WatchList("HAfx") {
                    item { Action("Resume", state.pattern.name) { nav.navigate("effect") } }
                    item { Action("Starter effects", "Unlock, lock, search and more") { nav.navigate("starters") } }
                    item { Action("Make a pulse", "Tune a sensation") { state.makePulse(); nav.navigate("effect") } }
                    item { Action("Tap a rhythm", "Create on your wrist") { nav.navigate("record") } }
                    item { Action("Saved effects", "Available offline") { nav.navigate("library") } }
                    item { Action("Search lab", "Try your cue in context") { nav.navigate("search") } }
                    if(state.store.needsRecovery) item { Action("Recover library", "Archive damaged file first") { nav.navigate("recovery") } }
                    item { Text(state.message, textAlign = TextAlign.Center) }
                }
            }
            composable("effect") {
                WatchList(state.pattern.name) {
                    item { Text("This watch · ${"%.1f".format(PatternSafety.duration(state.pattern))} s", textAlign=TextAlign.Center) }
                    item { Action(if(playback.active) "Stop" else state.auditionLabel, playback.message) { if(playback.active) state.player.stop() else state.audition() } }
                    if(state.hasSound) item { Action(if(state.soundOn) "Sound on" else "Sound off", "Preview · uses media volume") { state.toggleSound() } }
                    if(state.hasSound && state.canTune) item { Action("Sound level", "Edit the saved sound layer") { nav.navigate("sound-level") } }
                    else if(state.canTune) item { Action("Add paired sound", "A soft tone with each pulse") { state.addSound() } }
                    if(state.canTune) {
                        item { Action("Spacing", "${(state.spacing*100).roundToInt()}%") { nav.navigate("spacing") } }
                        if(state.capabilities.hasAmplitudeControl) item { Action("Strength", "${(state.strength*100).roundToInt()}%") { nav.navigate("strength") } }
                        else item { Text("This motor uses device-tuned strength.",textAlign=TextAlign.Center) }
                    } else item { Text("Use Studio for full track editing.",textAlign=TextAlign.Center) }
                    state.comparison?.let { original -> item { Action("Compare original", "Compare before keeping") { state.audition(original) } } }
                    item { Action("Keep", "Save on this watch") { if(state.keep())nav.navigate("saved") } }
                    if(state.isSavedUser) {
                        item { Action("Rename") { state.editName=state.pattern.name;nav.navigate("rename") } }
                        item { Action("Delete") { nav.navigate("delete") } }
                    }
                    if (EffectAttribution.from(state.pattern) != null) item { Action("About & source", "Design references") { nav.navigate("reference") } }
                    item { Text(state.message,textAlign=TextAlign.Center) }
                }
            }
            listOf("spacing", "strength").forEach { parameter ->
                composable(parameter) {
                    val spacing = parameter=="spacing"
                    val current = if(spacing)state.spacing else state.strength
                    fun adjust(value: Float) { if(spacing)state.adjust(spacing=value.coerceIn(.5f,2f)) else state.adjust(strength=value.coerceIn(.1f,1f)) }
                    WatchList(if(spacing)"Spacing" else "Strength", rotary = { delta -> adjust(current + if(delta>0) .05f else -.05f) }) {
                        item { Text("${(current*100).roundToInt()}%", style=MaterialTheme.typography.title1) }
                        item { InlineSlider(value=current, onValueChange=::adjust, steps=if(spacing)29 else 17,
                            valueRange=if(spacing).5f..2f else .1f..1f,
                            decreaseIcon={Text("−")}, increaseIcon={Text("+")}) }
                        item { Action(if(playback.active)"Stop" else state.auditionLabel) { if(playback.active)state.player.stop() else state.audition() } }
                        item { Action("Done") { state.flushDraft();nav.popBackStack() } }
                    }
                }
            }
            composable("sound-level") {
                val level=dev.hnm.workbench.core.companion.WristPatterns.soundLevel(state.pattern).toFloat()
                WatchList("Sound level") {
                    item { Text("${(level*100).roundToInt()}% · uses media volume",textAlign=TextAlign.Center) }
                    item { InlineSlider(value=level,onValueChange={state.setSoundLevel(it)},steps=18,valueRange=.05f..1f,
                        decreaseIcon={Text("−")},increaseIcon={Text("+")}) }
                    item { Action(if(playback.active) "Stop" else state.auditionLabel) { if(playback.active)state.player.stop() else state.audition() } }
                    item { Action("Done") { state.flushDraft();nav.popBackStack() } }
                }
            }
            composable("record") {
                DisposableEffect(Unit) { onDispose { state.cancelCapture() } }
                WatchList("Your rhythm") {
                    item { Text(if(state.recording) "${state.taps.size} / 6 taps" else state.message,textAlign=TextAlign.Center) }
                    item { Button(onClick={if(state.recording)state.tap() else state.record()}, modifier=Modifier.size(88.dp)) { Text(if(state.recording)"Tap" else "Record") } }
                    item { Action(if(state.recording)"Finish" else "Review", enabled=state.recording || state.taps.isNotEmpty()) { state.finishRecording();if(state.taps.isNotEmpty())nav.navigate("effect") } }
                    item { Text("Up to 6 taps · 4 seconds",textAlign=TextAlign.Center) }
                }
            }
            composable("starters") {
                WatchList("Starter effects") {
                    item { Text("Original HAfx designs with references", textAlign=TextAlign.Center) }
                    items(StarterEffects.ALL) { pattern ->
                        Action(pattern.name, EffectAttribution.from(pattern)?.description) { state.open(pattern);nav.navigate("effect") }
                    }
                }
            }
            composable("reference") {
                val info = EffectAttribution.from(state.pattern)
                WatchList("About this effect") {
                    info?.let { reference ->
                        item { Text(reference.description,textAlign=TextAlign.Center) }
                        item { Text(reference.note,fontSize=13.sp,textAlign=TextAlign.Start) }
                        reference.sources.forEach { source ->
                            item { Text(source.title,fontSize=13.sp,textAlign=TextAlign.Center) }
                            if (source.quote.isNotBlank()) item { Text("“${source.quote}”",fontSize=14.sp,textAlign=TextAlign.Center) }
                            item { Action("Open source") { runCatching { uri.openUri(source.url) }.onFailure { state.message="Open this effect's Reference in Studio for its source link." } } }
                        }
                        item { Text("References travel with your effect to Studio. Your edits can change the feel.",fontSize=12.sp,textAlign=TextAlign.Center) }
                        item { Text(state.message,fontSize=12.sp,textAlign=TextAlign.Center) }
                    }
                }
            }
            composable("rename") {
                WatchList("Name your effect") {
                    item { BasicTextField(value=state.editName,onValueChange={state.editName=it.take(160)},singleLine=true,
                        textStyle=TextStyle(color=Color.White,fontSize=18.sp),modifier=Modifier.fillMaxWidth().background(Color(0xFF25383E)).padding(12.dp)) }
                    item { Action("Save name") { if(state.rename())nav.popBackStack() } }
                    item { Text(state.message,textAlign=TextAlign.Center) }
                }
            }
            composable("delete") {
                WatchList("Delete effect?") {
                    item { Text(state.pattern.name,textAlign=TextAlign.Center) }
                    item { Text("This removes the saved copy on this watch. It cannot be undone.",textAlign=TextAlign.Center) }
                    item { Action("Delete permanently") { if(state.delete())nav.navigate("library") { popUpTo("home") } } }
                    item { Action("Cancel") { nav.popBackStack() } }
                }
            }
            composable("recovery") {
                WatchList("Recover library") {
                    item { Text("Archive the unreadable file and start fresh. Your draft is kept. Restore a backup in Studio and send it here.",textAlign=TextAlign.Center) }
                    item { Action("Archive & recover") { if(state.recover())nav.popBackStack() } }
                    item { Action("Cancel") { nav.popBackStack() } }
                    item { Text(state.message,textAlign=TextAlign.Center) }
                }
            }
            composable("library") {
                val patterns = remember(libraryVersion) { state.store.library.all().filter { it !in dev.hnm.workbench.core.library.BuiltInPatterns.ALL }.asReversed() }
                WatchList("Saved effects") {
                    if(patterns.isEmpty()) item { Text("Keep an effect to find it here. Starter effects are on Home.",textAlign=TextAlign.Center) }
                    items(patterns) { pattern -> Action(pattern.name) { state.open(pattern);nav.navigate("effect") } }
                }
            }
            composable("saved") {
                WatchList("Saved on watch") {
                    item { Text(state.pattern.name,textAlign=TextAlign.Center) }
                    item { Action("Use in Search lab") { nav.navigate("search") } }
                    item { Action("Send to Studio", "Save a revision on your phone") { state.send() } }
                    item { Text(connection,textAlign=TextAlign.Center) }
                    item { Text(syncStatus,textAlign=TextAlign.Center) }
                    item { Action("Check delivery") { state.refreshSync() } }
                    item { Action("Home") { nav.navigate("home") { popUpTo("home") { inclusive=true } } } }
                }
            }
            composable("search") {
                DisposableEffect(Unit) { onDispose { state.player.stop() } }
                val index = state.searchIndex
                WatchList("Search lab", rotary={delta->state.scrub(state.position + if(delta>0)12 else -12)}) {
                    item { Action("Query: ${state.query}", "${index.matches.size} matches in text") { nav.navigate("query") } }
                    item { Text(index.excerpt(state.position),fontSize=15.sp,textAlign=TextAlign.Start) }
                    item { Text("Position ${state.position} / ${state.searchText.lastIndex}") }
                    if(state.searchText.length>1) item { InlineSlider(value=state.position.toFloat(),onValueChange={state.scrub(it.toInt())},steps=99,
                        valueRange=0f..state.searchText.lastIndex.toFloat(),decreaseIcon={Text("−")},increaseIcon={Text("+")}) }
                    item { Action("Next match",enabled=index.matches.isNotEmpty()) { state.nextMatch() } }
                    item { Action("Previous match",enabled=index.matches.isNotEmpty()) { state.previousMatch() } }
                    item { Action(if(state.cues)"Cues on" else "Cues off", "Tap to compare") { state.cues=!state.cues;state.player.stop() } }
                    item { Action("Stop", enabled=playback.active) { state.player.stop() } }
                    item { Text("Haptic cue: ${state.searchCue().name} · sound off in Search lab",textAlign=TextAlign.Center) }
                    item { Text(if(state.pattern.metadata.containsKey("hafx.search.text")) "Searching your attached text, stored with this effect." else "Searching the included sample. Attach your own text in Studio → Device.",textAlign=TextAlign.Center) }
                    item { Text("No reading-performance claims.",textAlign=TextAlign.Center) }
                }
            }
            composable("query") {
                WatchList("Search for") {
                    item { BasicTextField(value=state.query,onValueChange={state.query=it.take(60)},singleLine=true,
                        textStyle=TextStyle(color=Color.White,fontSize=18.sp),modifier=Modifier.fillMaxWidth().background(Color(0xFF25383E)).padding(12.dp)) }
                    item { Action("Search") { state.scrub(0);nav.popBackStack() } }
                }
            }
        }
        if (playback.active) {
            Button(onClick = { state.player.stop() }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).width(100.dp).height(48.dp)) { Text("Stop") }
        }
        }
        }
    }
}

private val LocalPlaying = compositionLocalOf { false }

@Composable
private fun Action(label: String, secondary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Chip(onClick=onClick,enabled=enabled,label={Text(label)},secondaryLabel=secondary?.let { { Text(it) } },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp))
}

@Composable
private fun WatchList(title: String, rotary: ((Float) -> Unit)? = null, content: ScalingLazyListScope.() -> Unit) {
    val list = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(timeText={TimeText()},positionIndicator={PositionIndicator(scalingLazyListState=list)}) {
        ScalingLazyColumn(state=list,modifier=Modifier.fillMaxSize().onRotaryScrollEvent {
            if(rotary!=null)rotary(it.verticalScrollPixels) else scope.launch { list.scrollBy(it.verticalScrollPixels) }; true
        }.focusRequester(focus).focusable(),contentPadding=PaddingValues(start=14.dp,end=14.dp,top=32.dp,bottom=if(LocalPlaying.current)80.dp else 32.dp)) {
            item { Text(title,style=MaterialTheme.typography.title3,textAlign=TextAlign.Center) }
            content()
        }
    }
}

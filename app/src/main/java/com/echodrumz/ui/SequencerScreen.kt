package com.echodrumz.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echodrumz.AppViewModel
import com.echodrumz.data.PAD_COUNT
import com.echodrumz.data.STEP_COUNT
import kotlin.math.roundToInt

@Composable
fun SequencerScreen(vm: AppViewModel) {
    val p = vm.project
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.togglePlay() }, modifier = Modifier.width(96.dp)) { Text(if (vm.playing) "STOP" else "PLAY") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { vm.setBpm(p.bpm - 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("−") }
            Text("${p.bpm.roundToInt()} BPM", Modifier.padding(horizontal = 10.dp), fontSize = 15.sp)
            OutlinedButton(onClick = { vm.setBpm(p.bpm + 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("+") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.clearPattern() }) { Text("Clear") }
        }
        LabeledSlider("Tempo", p.bpm, 40f..240f, "${p.bpm.roundToInt()}") { vm.setBpm(it.roundToInt().toFloat()) }
        LabeledSlider("Swing", p.swing, 0f..0.4f, "${(p.swing * 100).roundToInt()}%") { vm.setSwing(it) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            for (pad in 0 until PAD_COUNT) StepRow(vm, pad)
        }
        Text("Tap a step to toggle · long-press to change velocity", fontSize = 11.sp,
            color = LocalEcho.current.text.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StepRow(vm: AppViewModel, pad: Int) {
    val echo = LocalEcho.current
    val cfg = vm.project.pads[pad]
    val row = vm.project.pattern[pad]
    Row(Modifier.fillMaxWidth().height(34.dp).padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(cfg.name.ifBlank { "Pad ${pad + 1}" }, Modifier.width(54.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp, color = echo.padColor(pad).copy(alpha = if (cfg.mute) 0.4f else 1f))
        for (s in 0 until STEP_COUNT) StepCell(vm, pad, s, row[s], vm.playhead == s)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.StepCell(vm: AppViewModel, pad: Int, step: Int, vel: Int, isHead: Boolean) {
    val echo = LocalEcho.current
    val shape = RoundedCornerShape(4.dp)
    val off = if ((step / 4) % 2 == 0) echo.surfaceHigh else echo.surfaceHigh.copy(alpha = 0.55f)
    val bg = if (vel > 0) echo.padColor(pad).copy(alpha = 0.35f + 0.65f * vel / 127f) else off
    Box(
        Modifier.weight(1f).fillMaxHeight().padding(1.dp).clip(shape).background(bg)
            .then(if (isHead) Modifier.border(1.5.dp, echo.text, shape) else Modifier)
            .combinedClickable(onClick = { vm.toggleStep(pad, step) }, onLongClick = { vm.cycleVelocity(pad, step) })
    )
}

package com.echodrumz.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echodrumz.AppViewModel
import kotlin.math.roundToInt

@Composable
fun PadsScreen(vm: AppViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                PadGrid(vm, Modifier.weight(1f).fillMaxHeight())
                PadEditor(vm, Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp).verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PadGrid(vm, Modifier.fillMaxWidth().weight(1f))
                PadEditor(vm, Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
            }
        }
    }
}

@Composable
private fun PadGrid(vm: AppViewModel, modifier: Modifier) {
    Column(modifier) {
        for (r in 0 until 4) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0 until 4) PadButton(vm, r * 4 + c, Modifier.weight(1f).fillMaxHeight().padding(3.dp))
        }
    }
}

@Composable
private fun PadButton(vm: AppViewModel, index: Int, modifier: Modifier) {
    val echo = LocalEcho.current
    var pressed by remember { mutableStateOf(false) }
    val pad = vm.project.pads[index]
    val base = echo.padColor(index)
    val shape = RoundedCornerShape(14.dp)
    val fill = when {
        pressed -> base
        pad.samplePath == null -> base.copy(alpha = 0.22f)
        pad.mute -> base.copy(alpha = 0.25f)
        else -> base.copy(alpha = 0.6f)
    }
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .then(if (vm.selectedPad == index) Modifier.border(2.dp, echo.text, shape) else Modifier)
            .pointerInput(index) {
                detectTapGestures(onPress = {
                    pressed = true
                    vm.padDown(index)
                    tryAwaitRelease()
                    pressed = false
                    vm.padUp(index)
                })
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(pad.name.ifBlank { "Pad ${index + 1}" }, Modifier.padding(8.dp), maxLines = 1,
            overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = echo.text)
    }
}

@Composable
private fun PadEditor(vm: AppViewModel, modifier: Modifier) {
    val i = vm.selectedPad
    val pad = vm.project.pads[i]
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSample(uri, i)
    }
    Column(modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PAD ${i + 1}  ${pad.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Button(onClick = { picker.launch(arrayOf("audio/*", "application/octet-stream")) }) { Text("Load WAV") }
            TextButton(onClick = { vm.clearPadSample(i) }, enabled = pad.samplePath != null) { Text("Clear") }
        }
        LabeledSlider("Volume", pad.volume, 0f..1.5f, "${(pad.volume * 100).roundToInt()}%") { v -> vm.updatePad(i) { it.copy(volume = v) } }
        LabeledSlider("Pitch", pad.pitch, -12f..12f, "%+.1f st".format(pad.pitch)) { v -> vm.updatePad(i) { it.copy(pitch = (v * 2).roundToInt() / 2f) } }
        LabeledSlider("Pan", pad.pan, -1f..1f, if (pad.pan == 0f) "C" else "%+d".format((pad.pan * 100).roundToInt())) { v -> vm.updatePad(i) { it.copy(pan = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(!pad.gate, { vm.updatePad(i) { it.copy(gate = false) } }, { Text("One-shot") })
            Spacer(Modifier.width(8.dp))
            FilterChip(pad.gate, { vm.updatePad(i) { it.copy(gate = true) } }, { Text("Gate") })
            Spacer(Modifier.width(8.dp))
            FilterChip(pad.mute, { vm.updatePad(i) { it.copy(mute = !it.mute) } }, { Text("Mute") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Choke", fontSize = 12.sp, modifier = Modifier.width(56.dp))
            for (g in 0..3) {
                FilterChip(pad.choke == g, { vm.updatePad(i) { it.copy(choke = g) } }, { Text(if (g == 0) "Off" else "$g") })
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), fontSize = 12.sp)
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, Modifier.weight(1f), valueRange = range)
        Text(display, Modifier.width(60.dp), fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

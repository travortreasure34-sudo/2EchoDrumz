package com.echodrumz

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echodrumz.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent { EchoApp(vm) }
    }

    override fun onStart() { super.onStart(); if (!vm.engineRunning) vm.engineStart() }
    override fun onStop() { vm.engineStop(); super.onStop() }
}

@Composable
fun EchoApp(vm: AppViewModel) {
    val theme = resolveTheme(vm.themeId, vm.customHue)
    EchoMaterialTheme(theme) {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
        var tab by rememberSaveable { mutableIntStateOf(0) }  // PADS is the default home screen
        val tabs = listOf("PADS", "SEQUENCER", "PROJECT")

        Scaffold(containerColor = theme.bg, snackbarHost = { SnackbarHost(snack) }) { inner ->
            Column(Modifier.padding(inner).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ECHODRUMZ", fontWeight = FontWeight.Black, fontSize = 18.sp, color = theme.accent)
                    Spacer(Modifier.weight(1f))
                    Text(if (vm.engineRunning) vm.project.name else "Audio engine not running",
                        fontSize = 12.sp, color = if (vm.engineRunning) theme.text.copy(alpha = 0.6f) else Color(0xFFFF5252))
                }
                TabRow(selectedTabIndex = tab, containerColor = theme.surface, contentColor = theme.accent) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 13.sp) }) }
                }
                Box(Modifier.fillMaxSize().background(theme.bg)) {
                    when (tab) {
                        0 -> PadsScreen(vm)
                        1 -> SequencerScreen(vm)
                        else -> ProjectScreen(vm)
                    }
                }
            }
        }
    }
}

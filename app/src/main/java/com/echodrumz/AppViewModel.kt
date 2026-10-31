package com.echodrumz

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echodrumz.audio.NativeEngine
import com.echodrumz.audio.WavDecoder
import com.echodrumz.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var project by mutableStateOf(Project("Untitled"))
        private set
    var selectedPad by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
        private set
    var playhead by mutableIntStateOf(-1)
        private set
    var themeId by mutableStateOf(storage.themeId)
        private set
    var customHue by mutableFloatStateOf(storage.customHue)
        private set
    var engineRunning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    val savedProjects = mutableStateListOf<String>()

    private var autosaveJob: Job? = null

    init {
        DefaultKit.ensureFiles(storage.samplesDir)
        engineStart()
        setProject(storage.loadAutosave() ?: DefaultKit.defaultProject(), save = false)  // session recovery
        refreshProjects()
        viewModelScope.launch {
            while (true) {
                val ph = if (playing) NativeEngine.getPlayhead() else -1
                if (ph != playhead) playhead = ph
                delay(16)
            }
        }
    }

    // ---------- engine lifecycle ----------
    fun engineStart() { engineRunning = NativeEngine.start() }
    fun engineStop() {
        NativeEngine.setPlaying(false); playing = false
        NativeEngine.stop(); engineRunning = false
        autosaveNow()
    }
    override fun onCleared() { NativeEngine.stop() }

    // ---------- project plumbing ----------
    private fun setProject(p: Project, save: Boolean = true) {
        project = p
        NativeEngine.setBpm(p.bpm); NativeEngine.setSwing(p.swing)
        p.pads.forEachIndexed { i, pad -> pushPad(i, pad) }
        p.pattern.forEachIndexed { pad, row -> row.forEachIndexed { s, v -> NativeEngine.setStep(pad, s, v) } }
        viewModelScope.launch(Dispatchers.IO) { p.pads.forEachIndexed { i, pad -> loadPadSample(i, pad.samplePath) } }
        if (save) scheduleAutosave()
    }

    private fun loadPadSample(i: Int, path: String?) {
        if (path == null) { NativeEngine.clearSample(i); return }
        try {
            val d = WavDecoder.decode(File(storage.samplesDir, path).readBytes())
            NativeEngine.loadSample(i, d.data, d.channels, d.sampleRate)
        } catch (e: Exception) {
            NativeEngine.clearSample(i)
            message = "Could not load sample \"$path\""
        }
    }

    private fun pushPad(i: Int, p: PadConfig) =
        NativeEngine.setPadParams(i, p.volume, p.pitch, p.pan, p.gate, p.choke, p.mute)

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) { delay(700); storage.autosave(project) }
    }
    fun autosaveNow() { autosaveJob?.cancel(); storage.autosave(project) }

    // ---------- pads ----------
    fun padDown(i: Int) { selectedPad = i; NativeEngine.triggerPad(i, 1f) }
    fun padUp(i: Int) = NativeEngine.releasePad(i)

    fun updatePad(i: Int, change: (PadConfig) -> PadConfig) {
        val np = change(project.pads[i])
        project = project.copy(pads = project.pads.toMutableList().also { it[i] = np })
        pushPad(i, np)
        scheduleAutosave()
    }

    fun clearPadSample(i: Int) {
        NativeEngine.clearSample(i)
        updatePad(i) { it.copy(samplePath = null, name = "") }
    }

    fun importSample(uri: Uri, pad: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val imp = storage.importSample(uri)
                NativeEngine.loadSample(pad, imp.audio.data, imp.audio.channels, imp.audio.sampleRate)
                withContext(Dispatchers.Main) {
                    updatePad(pad) { it.copy(name = imp.displayName, samplePath = imp.fileName) }
                    if (!project.pads[pad].gate) NativeEngine.triggerPad(pad, 1f)
                }
            } catch (e: Exception) {
                message = "Import failed: ${e.message ?: "unreadable file"}"
            }
        }
    }

    // ---------- sequencer ----------
    fun togglePlay() { playing = !playing; NativeEngine.setPlaying(playing) }

    fun setBpm(v: Float) {
        val b = v.coerceIn(40f, 240f)
        project = project.copy(bpm = b); NativeEngine.setBpm(b); scheduleAutosave()
    }
    fun setSwing(v: Float) {
        val s = v.coerceIn(0f, 0.4f)
        project = project.copy(swing = s); NativeEngine.setSwing(s); scheduleAutosave()
    }
    private fun setStep(pad: Int, step: Int, vel: Int) {
        val row = project.pattern[pad].toMutableList().also { it[step] = vel }
        project = project.copy(pattern = project.pattern.toMutableList().also { it[pad] = row })
        NativeEngine.setStep(pad, step, vel); scheduleAutosave()
    }
    fun toggleStep(pad: Int, step: Int) = setStep(pad, step, if (project.pattern[pad][step] > 0) 0 else 100)
    fun cycleVelocity(pad: Int, step: Int) {
        val levels = listOf(127, 100, 70, 40)
        val cur = project.pattern[pad][step]
        val next = if (cur == 0) 100 else levels.firstOrNull { it < cur } ?: 127
        setStep(pad, step, next)
    }
    fun clearPattern() {
        project = project.copy(pattern = List(PAD_COUNT) { List(STEP_COUNT) { 0 } })
        for (p in 0 until PAD_COUNT) for (s in 0 until STEP_COUNT) NativeEngine.setStep(p, s, 0)
        scheduleAutosave()
    }

    // ---------- projects ----------
    fun refreshProjects() { savedProjects.clear(); savedProjects.addAll(storage.listProjects()) }

    fun newProject() {
        setProject(DefaultKit.defaultProject().copy(name = "Untitled", pattern = List(PAD_COUNT) { List(STEP_COUNT) { 0 } }))
        message = "New project"
    }
    fun saveProject() {
        storage.saveProject(project); refreshProjects(); message = "Saved \"${project.name}\""
    }
    fun saveAs(name: String) {
        val n = storage.safeName(name)
        project = project.copy(name = n); storage.saveProject(project); refreshProjects()
        scheduleAutosave(); message = "Saved \"$n\""
    }
    fun openProject(name: String) {
        val p = storage.loadProject(name)
        if (p == null) { message = "Could not open \"$name\""; return }
        setProject(p); message = "Opened \"$name\""
    }
    fun renameCurrent(newName: String) {
        val n = storage.safeName(newName); val old = project.name
        if (n == old) return
        if (storage.projectExists(n)) { message = "\"$n\" already exists"; return }
        if (storage.projectExists(old)) storage.renameProject(old, n)
        project = project.copy(name = n); refreshProjects(); scheduleAutosave()
    }
    fun duplicateProject(name: String) {
        val p = storage.loadProject(name) ?: return
        storage.saveProject(p.copy(name = storage.uniqueProjectName("$name copy"))); refreshProjects()
    }
    fun deleteProject(name: String) { storage.deleteProject(name); refreshProjects() }

    // ---------- theme ----------
    fun setTheme(id: String) { themeId = id; storage.themeId = id }
    fun setCustomHue(h: Float) { customHue = h; storage.customHue = h }
}

package com.echodrumz.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.echodrumz.audio.DecodedAudio
import com.echodrumz.audio.WavDecoder
import java.io.File

class Imported(val fileName: String, val displayName: String, val audio: DecodedAudio)

/** Everything lives in app-private internal storage (filesDir). No external storage, no network. */
class Storage(private val context: Context) {
    private val root = context.filesDir
    val samplesDir = File(root, "samples").also { it.mkdirs() }
    private val projectsDir = File(root, "projects").also { it.mkdirs() }
    private val autosaveFile = File(root, "autosave.json")
    private val prefs = context.getSharedPreferences("echodrumz_settings", Context.MODE_PRIVATE)

    var themeId: String
        get() = prefs.getString("theme", "midnight") ?: "midnight"
        set(v) { prefs.edit().putString("theme", v).apply() }
    var customHue: Float
        get() = prefs.getFloat("customHue", 190f)
        set(v) { prefs.edit().putFloat("customHue", v).apply() }

    fun safeName(n: String): String = n.trim().replace(Regex("[^A-Za-z0-9 _-]"), "_").take(48).ifBlank { "Untitled" }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun projectFile(name: String) = File(projectsDir, safeName(name) + ".json")

    fun listProjects(): List<String> =
        projectsDir.listFiles { f -> f.extension == "json" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

    fun projectExists(name: String) = projectFile(name).exists()

    fun saveProject(p: Project) {
        val n = safeName(p.name)
        writeAtomic(projectFile(n), ProjectCodec.toJson(p.copy(name = n)))
    }

    fun loadProject(name: String): Project? =
        runCatching { ProjectCodec.fromJson(projectFile(name).readText()) }.getOrNull()

    fun deleteProject(name: String) { projectFile(name).delete() }

    fun renameProject(old: String, new: String): Boolean {
        val p = loadProject(old) ?: return false
        saveProject(p.copy(name = new))
        deleteProject(old)
        return true
    }

    fun uniqueProjectName(base: String): String {
        var n = safeName(base); var i = 2
        while (projectExists(n)) { n = safeName("$base $i"); i++ }
        return n
    }

    fun autosave(p: Project) = runCatching { writeAtomic(autosaveFile, ProjectCodec.toJson(p)) }
    fun loadAutosave(): Project? = runCatching { ProjectCodec.fromJson(autosaveFile.readText()) }.getOrNull()

    /** Copies a picked sample into private storage after validating it decodes. */
    fun importSample(uri: Uri): Imported {
        val cr = context.contentResolver
        val display = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "sample.wav"
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open file")
        require(bytes.size <= 32 * 1024 * 1024) { "File too large (max 32 MB)" }
        val audio = WavDecoder.decode(bytes)
        val base = safeName(display.substringBeforeLast('.'))
        var f = File(samplesDir, "$base.wav"); var i = 1
        while (f.exists()) { f = File(samplesDir, "$base-$i.wav"); i++ }
        f.writeBytes(bytes)
        return Imported(f.name, base, audio)
    }
}

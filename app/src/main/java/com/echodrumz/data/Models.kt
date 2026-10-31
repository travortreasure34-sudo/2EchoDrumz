package com.echodrumz.data

import org.json.JSONArray
import org.json.JSONObject

const val PAD_COUNT = 16
const val STEP_COUNT = 16

data class PadConfig(
    val name: String = "",
    val samplePath: String? = null,  // file name inside app-private samples dir
    val volume: Float = 1f,
    val pitch: Float = 0f,           // semitones
    val pan: Float = 0f,
    val gate: Boolean = false,
    val choke: Int = 0,
    val mute: Boolean = false,
)

data class Project(
    val name: String,
    val bpm: Float = 120f,
    val swing: Float = 0f,
    val pads: List<PadConfig> = List(PAD_COUNT) { PadConfig() },
    val pattern: List<List<Int>> = List(PAD_COUNT) { List(STEP_COUNT) { 0 } },  // [pad][step] velocity 0..127
)

object ProjectCodec {
    fun toJson(p: Project): String = JSONObject().apply {
        put("version", 1)
        put("name", p.name)
        put("bpm", p.bpm.toDouble())
        put("swing", p.swing.toDouble())
        put("pads", JSONArray().also { arr ->
            p.pads.forEach {
                arr.put(JSONObject().apply {
                    put("name", it.name)
                    put("sample", it.samplePath ?: JSONObject.NULL)
                    put("volume", it.volume.toDouble()); put("pitch", it.pitch.toDouble()); put("pan", it.pan.toDouble())
                    put("gate", it.gate); put("choke", it.choke); put("mute", it.mute)
                })
            }
        })
        put("pattern", JSONArray().also { arr -> p.pattern.forEach { arr.put(JSONArray(it)) } })
    }.toString()

    fun fromJson(text: String): Project {
        val o = JSONObject(text)
        val pa = o.getJSONArray("pads")
        val pads = List(PAD_COUNT) { i ->
            if (i >= pa.length()) PadConfig() else pa.getJSONObject(i).let {
                PadConfig(
                    name = it.optString("name", ""),
                    samplePath = if (it.isNull("sample")) null else it.getString("sample"),
                    volume = it.optDouble("volume", 1.0).toFloat(),
                    pitch = it.optDouble("pitch", 0.0).toFloat(),
                    pan = it.optDouble("pan", 0.0).toFloat(),
                    gate = it.optBoolean("gate", false),
                    choke = it.optInt("choke", 0),
                    mute = it.optBoolean("mute", false),
                )
            }
        }
        val pt = o.getJSONArray("pattern")
        val pattern = List(PAD_COUNT) { i ->
            List(STEP_COUNT) { s -> if (i < pt.length() && s < pt.getJSONArray(i).length()) pt.getJSONArray(i).getInt(s) else 0 }
        }
        return Project(o.optString("name", "Untitled"), o.optDouble("bpm", 120.0).toFloat(),
            o.optDouble("swing", 0.0).toFloat(), pads, pattern)
    }
}

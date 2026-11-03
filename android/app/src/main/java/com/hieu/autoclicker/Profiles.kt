package com.hieu.autoclicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Một bước trong kịch bản: chạm tại (x, y) hoặc vuốt từ (x, y) đến (x2, y2). Toạ độ là góc trên-trái của điểm. */
data class StepData(val swipe: Boolean, val x: Int, val y: Int, val x2: Int = 0, val y2: Int = 0)

data class Profile(
    val name: String,
    val steps: List<StepData>,
    val intervalMs: Long,
    val repeatCount: Int,
    val pressMs: Long,
    val jitterPx: Int,
    val swipeMs: Long,
    val stopAfterMin: Int,
)

/** Lưu các kịch bản dưới dạng JSON trong SharedPreferences. */
class Profiles(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> {
        val arr = runCatching { JSONArray(prefs.getString("list", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).mapNotNull { runCatching { decode(arr.getJSONObject(it)) }.getOrNull() }
    }

    fun add(p: Profile) = save(all() + p)

    fun remove(index: Int) = save(all().filterIndexed { i, _ -> i != index })

    /** Tên mặc định chưa trùng: "Kịch bản 1", "Kịch bản 2"… */
    fun nextName(): String {
        val names = all().map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { "Kịch bản $it" }.first { it !in names }
    }

    private fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(encode(it)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun encode(p: Profile) = JSONObject().apply {
        put("name", p.name)
        put("interval", p.intervalMs)
        put("repeat", p.repeatCount)
        put("press", p.pressMs)
        put("jitter", p.jitterPx)
        put("swipe", p.swipeMs)
        put("stopAfter", p.stopAfterMin)
        put("steps", JSONArray().apply {
            p.steps.forEach { s ->
                put(JSONObject().apply {
                    put("t", if (s.swipe) "swipe" else "tap")
                    put("x", s.x); put("y", s.y)
                    if (s.swipe) { put("x2", s.x2); put("y2", s.y2) }
                })
            }
        })
    }

    private fun decode(o: JSONObject): Profile {
        val stepsArr = o.getJSONArray("steps")
        val steps = (0 until stepsArr.length()).map {
            val s = stepsArr.getJSONObject(it)
            StepData(s.optString("t") == "swipe", s.getInt("x"), s.getInt("y"), s.optInt("x2"), s.optInt("y2"))
        }
        return Profile(
            name = o.getString("name"),
            steps = steps,
            intervalMs = o.optLong("interval", 500),
            repeatCount = o.optInt("repeat", 0),
            pressMs = o.optLong("press", 20),
            jitterPx = o.optInt("jitter", 0),
            swipeMs = o.optLong("swipe", 300),
            stopAfterMin = o.optInt("stopAfter", 0),
        )
    }
}

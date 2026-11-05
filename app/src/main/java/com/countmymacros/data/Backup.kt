package com.countmymacros.data

import org.json.JSONException
import org.json.JSONObject

/** The file isn't a Platewise backup, or is from a newer version of the app than this one. */
class BackupException(message: String) : Exception(message)

/**
 * The export file: the database tables plus the profile, as one JSON document. Pure (no Android),
 * so the format can be tested; [Db.dumpAll] and [Db.restoreAll] do the table work.
 */
object Backup {
    const val FORMAT = 1
    private const val APP = "platewise"

    fun encode(tables: JSONObject, profile: Profile, exportedAtMs: Long): String =
        JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("exportedAt", exportedAtMs)
            .put("profile", profileToJson(profile))
            .put("tables", tables)
            .toString()

    /** The tables and profile in [json]; throws [BackupException] when it isn't a usable Platewise backup. */
    fun decode(json: String): Pair<JSONObject, Profile> {
        try {
            val o = JSONObject(json)
            if (o.optString("app") != APP) throw BackupException("not a Platewise backup")
            if (o.optInt("format") > FORMAT) throw BackupException("made by a newer version of the app")
            val tables = o.optJSONObject("tables") ?: throw BackupException("no data in the file")
            return tables to profileFromJson(o.optJSONObject("profile"))
        } catch (e: JSONException) {
            throw BackupException("unreadable file")
        }
    }

    fun profileToJson(p: Profile): JSONObject = JSONObject()
        .put("sex", p.sex.name).put("age", p.age).put("heightCm", p.heightCm).put("weightKg", p.weightKg)
        .put("activity", p.activity.name).put("goal", p.goal.name).put("pace", p.pace).put("preset", p.preset.name)
        .put(
            "custom",
            JSONObject().put("kcal", p.custom.kcal).put("protein", p.custom.protein)
                .put("carbs", p.custom.carbs).put("fat", p.custom.fat),
        )

    /** Missing or unknown fields fall back to the defaults, so an older or hand-edited file still loads. */
    fun profileFromJson(o: JSONObject?): Profile {
        val d = Profile()
        if (o == null) return d
        val c = o.optJSONObject("custom")
        return Profile(
            sex = enumOr(o.optString("sex"), d.sex),
            age = o.optInt("age", d.age),
            heightCm = o.optDouble("heightCm", d.heightCm),
            weightKg = o.optDouble("weightKg", d.weightKg),
            activity = enumOr(o.optString("activity"), d.activity),
            goal = enumOr(o.optString("goal"), d.goal),
            pace = o.optDouble("pace", d.pace),
            preset = enumOr(o.optString("preset"), d.preset),
            custom = if (c == null) d.custom else Macros(
                c.optDouble("kcal", d.custom.kcal), c.optDouble("protein", d.custom.protein),
                c.optDouble("carbs", d.custom.carbs), c.optDouble("fat", d.custom.fat),
            ),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}

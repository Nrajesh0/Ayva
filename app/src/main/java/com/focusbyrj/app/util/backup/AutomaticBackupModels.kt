package com.focusbyrj.app.util.backup

import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit

internal data class BackupRequestScope(val stream: String, val keyId: String, val epoch: String) {
    fun matches(config: AutomaticBackupConfig, currentEpoch: String) = stream == config.stream && keyId == config.keyId && epoch == currentEpoch
}

internal data class BackupFrequency(val unit: String = "DAYS", val every: Int = 1, val hour: Int = 3, val minute: Int = 0,
    // Calendar modes encode a date at UTC midnight, avoiding day drift when the device changes time zone.
    val anchor: Long = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) {
    fun validate() {
        require(unit in setOf("HOURS", "DAYS", "WEEKS", "MONTHS"))
        require(every in 1..when(unit) { "HOURS" -> 24; "DAYS" -> 365; "WEEKS" -> 52; else -> 12 })
        require(hour in 0..23 && minute in 0..59 && anchor > 0)
    }
    /** Calendar months keep the original day (Jan31 -> Feb28 -> Mar31). DST gaps move forward. */
    fun nextAfter(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        validate()
        if (unit == "HOURS") {
            if (now < anchor) return anchor
            val step = every * 3_600_000L
            return Math.addExact(anchor, Math.multiplyExact(((now - anchor).coerceAtLeast(0) / step) + 1, step))
        }
        val origin = Instant.ofEpochMilli(anchor).atZone(ZoneOffset.UTC).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun date(index: Long) = if (unit == "MONTHS") {
            val month = YearMonth.from(origin).plusMonths(index * every)
            month.atDay(minOf(origin.dayOfMonth, month.lengthOfMonth()))
        } else origin.plusDays(index * every * if (unit == "WEEKS") 7 else 1)
        val distance = if (unit == "MONTHS") ChronoUnit.MONTHS.between(YearMonth.from(origin), YearMonth.from(today))
            else ChronoUnit.DAYS.between(origin, today) / if (unit == "WEEKS") 7 else 1
        var index = (distance / every).coerceAtLeast(0)
        var next = date(index).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (next <= now) next = date(++index).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        return next
    }
    fun json() = JSONObject().put("unit", unit).put("every", every).put("hour", hour).put("minute", minute).put("anchor", anchor)
    companion object { fun read(j: JSONObject) = BackupFrequency(j.getString("unit"), j.getInt("every"), j.getInt("hour"), j.getInt("minute"), j.getLong("anchor")).also { it.validate() } }
}

internal data class AutomaticBackupConfig(
    val enabled: Boolean = false, val tree: String = "", val keyId: String = "", val stream: String = "",
    val keyExported: Boolean = false, val keyAcknowledged: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency(), val keep: Int = 7, val chargingOnly: Boolean = false,
    val nextDue: Long = 0, val lastSuccess: Long = 0, val lastAttempt: Long = 0,
    val problem: String = "", val detail: String = "", val lastFile: String = "", val scheduleZone: String = ZoneId.systemDefault().id
) {
    fun validate() { frequency.validate(); ZoneId.of(scheduleZone); require(keep in 1..365); require(tree.length <= 4096)
        require(stream.isEmpty() || stream.matches(Regex("[a-f0-9-]{36}"))); require(keyId.isEmpty() || keyId.matches(Regex("[A-F0-9]{32}")))
        require(!enabled || tree.isNotBlank() && keyId.isNotBlank() && stream.isNotBlank() && keyExported && keyAcknowledged) }
    fun json() = JSONObject().put("version", 1).put("enabled", enabled).put("tree", tree).put("keyId", keyId).put("stream", stream)
        .put("keyExported", keyExported).put("keyAcknowledged", keyAcknowledged).put("frequency", frequency.json()).put("keep", keep)
        .put("chargingOnly", chargingOnly).put("nextDue", nextDue).put("lastSuccess", lastSuccess).put("lastAttempt", lastAttempt)
        .put("problem", problem).put("detail", detail).put("lastFile", lastFile).put("scheduleZone", scheduleZone)
    companion object {
        fun read(raw: String): AutomaticBackupConfig {
            require(raw.length <= 16000); BackupArchiveLimits.checkJsonNesting(raw)
            val j = JSONObject(raw); require(j.getInt("version") == 1)
            return AutomaticBackupConfig(j.getBoolean("enabled"), j.getString("tree"), j.getString("keyId"), j.getString("stream"),
                j.getBoolean("keyExported"), j.getBoolean("keyAcknowledged"), BackupFrequency.read(j.getJSONObject("frequency")), j.getInt("keep"),
                j.getBoolean("chargingOnly"), j.getLong("nextDue"), j.getLong("lastSuccess"), j.getLong("lastAttempt"),
                j.getString("problem"), j.getString("detail"), j.getString("lastFile"), j.optString("scheduleZone", ZoneId.systemDefault().id)).also { it.validate() }
        }
    }
}

internal data class ManagedBackup(val uri: String, val name: String, val stream: String, val keyId: String, val created: Long, val size: Long, val sha256: String, val vaultAuthorization: String = "none", val requestId: String = "") {
    fun json() = JSONObject().put("uri", uri).put("name", name).put("stream", stream).put("keyId", keyId).put("created", created).put("size", size).put("sha256", sha256).put("vaultAuthorization", vaultAuthorization).put("requestId", requestId)
    companion object { fun read(j: JSONObject) = ManagedBackup(j.getString("uri"), j.getString("name"), j.getString("stream"), j.getString("keyId"), j.getLong("created"), j.getLong("size"), j.getString("sha256"), j.optString("vaultAuthorization", "none"), j.optString("requestId", "")).also {
        require(it.uri.length <= 4096 && it.name.length <= 256 && it.stream.matches(Regex("[a-f0-9-]{36}")) && it.keyId.matches(Regex("[A-F0-9]{32}")) && it.created > 0 && it.size in 1..BackupArchiveLimits.ENCRYPTED_BYTES && it.sha256.matches(Regex("[a-f0-9]{64}")))
        require(it.requestId.isEmpty() || it.requestId.matches(Regex("[a-f0-9-]{36}")))
    } }
}

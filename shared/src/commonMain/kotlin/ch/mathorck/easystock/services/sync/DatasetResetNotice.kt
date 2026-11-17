package ch.mathorck.easystock.services.sync

/**
 * The server replaced its data set: the phone erased its copy and pulled the new one.
 * [lostChanges]: local changes that were not sent yet, lost with the wipe.
 * [backupFile]: name of the automatic backup of the phone written before the wipe ([AutoBackups]), null
 * when none was written. [backupFailed]: the backup was tried and failed (the reset went on anyway).
 */
data class DatasetResetNotice(
    val lostChanges: Int,
    val backupFile: String? = null,
    val backupFailed: Boolean = false,
) {
    /** Text kept by a [ResetNoticeStore] until the user dismisses the notice. */
    fun encode(): String = listOf(lostChanges.toString(), if (backupFailed) "1" else "0", backupFile.orEmpty()).joinToString("\n")

    companion object {
        /** Opposite of [encode]. Null for nothing stored or text that is not a notice. */
        fun decode(text: String?): DatasetResetNotice? {
            val parts = text?.split("\n") ?: return null
            if (parts.size != 3) return null
            val lost = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val failed = when (parts[1]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            return DatasetResetNotice(lost, parts[2].ifEmpty { null }, failed)
        }
    }
}

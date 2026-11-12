package ch.mathorck.easystock.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatasetResetNoticeTest {
    @Test
    fun aNoticeSurvivesEncodeAndDecode() {
        for (notice in listOf(
            DatasetResetNotice(0),
            DatasetResetNotice(5, "auto-easystock-20260101-1200.easystock"),
            DatasetResetNotice(2, null, backupFailed = true),
        )) {
            assertEquals(notice, DatasetResetNotice.decode(notice.encode()))
        }
    }

    @Test
    fun textThatIsNotANoticeIsIgnored() {
        assertNull(DatasetResetNotice.decode(null))
        assertNull(DatasetResetNotice.decode(""))
        assertNull(DatasetResetNotice.decode("1\n0"))
        assertNull(DatasetResetNotice.decode("x\n0\n"))
        assertNull(DatasetResetNotice.decode("-1\n0\n"))
        assertNull(DatasetResetNotice.decode("1\n2\n"))
    }

    @Test
    fun onlyTheNewestThreeAutomaticBackupsStay() {
        val names = listOf(
            "auto-easystock-20260103-0900.easystock",
            "auto-easystock-20260101-0900.easystock",
            "auto-easystock-20260104-0900.easystock",
            "auto-easystock-20260102-0900.easystock",
            "easystock-20250101-0900.easystock",
        )
        assertEquals(
            listOf("auto-easystock-20260101-0900.easystock"),
            AutoBackups.toDelete(names),
        )
        assertEquals(emptyList(), AutoBackups.toDelete(names.take(3)))
        assertEquals(emptyList(), AutoBackups.toDelete(emptyList()))
    }
}

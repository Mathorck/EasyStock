package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ServerImportMode
import ch.mathorck.easystock.services.server.LocalFile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerImportFormTest {
    private val file = LocalFile("/c/b", "b.easystock", 10, "application/octet-stream")

    @Test
    fun mergeNeedsNoWord() = assertTrue(ServerImportForm(file).canImport("REPLACE"))

    @Test
    fun replaceNeedsTheTypedWord() {
        val form = ServerImportForm(file, ServerImportMode.REPLACE)
        assertFalse(form.canImport("REMPLACER"))
        assertFalse(form.copy(typed = "REMPLACE").canImport("REMPLACER"))
        assertTrue(form.copy(typed = " remplacer ").canImport("REMPLACER"))
        assertFalse(form.copy(typed = "").canImport(""))
    }
}

package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import kotlin.test.Test
import kotlin.test.assertEquals

class DateAlertFormTest {
    private val form = AttributeForm(label = "Expiry", dataType = AttributeDataType.DATE)

    @Test
    fun notifyIsForcedOffWithoutTheFlag() {
        val form = form.copy(flagWhenPast = false, notifyWhenPast = true)
        assertEquals(false, form.normalizedDateAlerts().notifyWhenPast)
        assertEquals(true, form.copy(flagWhenPast = true).normalizedDateAlerts().notifyWhenPast)
    }

    @Test
    fun otherTypesNeverKeepTheDateToggles() {
        val text = form.copy(dataType = AttributeDataType.TEXT, flagWhenPast = true, notifyWhenPast = true).normalizedDateAlerts()
        assertEquals(false, text.flagWhenPast)
        assertEquals(false, text.notifyWhenPast)
    }

    @Test
    fun turningTheFlagOffTurnsNotifyOff() {
        val on = form.copy(flagWhenPast = true, notifyWhenPast = true)
        assertEquals(false, on.withFlagWhenPast(false).notifyWhenPast)
        assertEquals(true, on.withFlagWhenPast(true).notifyWhenPast)
    }

    @Test
    fun theFormReadsTheStoredToggles() {
        val stored = AttributeDefinition(
            id = "d", key = "e", label = "E", dataType = AttributeDataType.DATE, flagWhenPast = true, notifyWhenPast = true, createdAt = 0, updatedAt = 0,
        )
        assertEquals(true, stored.toForm().notifyWhenPast)
        assertEquals(true, stored.toForm().flagWhenPast)
    }
}

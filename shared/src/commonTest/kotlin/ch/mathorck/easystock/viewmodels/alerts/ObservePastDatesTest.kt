package ch.mathorck.easystock.viewmodels.alerts

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.enums.attributes.AttributeDataType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ObservePastDatesTest {
    private val today = flowOf(LocalDate(2026, 11, 18))
    private val values = JsonObject(mapOf("expiry" to JsonPrimitive("2026-01-01")))
    private val article = Article(id = "a1", name = "A", attributes = values, createdAt = 0, updatedAt = 0)

    private fun date(flag: Boolean) = AttributeDefinition(
        id = "d", key = "expiry", label = "Expiry", dataType = AttributeDataType.DATE, flagWhenPast = flag, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun doesNotReadArticlesWhenNoDefinitionFlags() = runTest {
        var read = false
        val result = observePastDates(
            definitions = flowOf(listOf(date(flag = false))),
            articles = { read = true; flowOf(listOf(article)) },
            items = { read = true; flowOf(emptyList<Item>()) },
            today = today,
        ).first()
        assertEquals(emptyList(), result)
        assertFalse(read)
    }

    @Test
    fun readsTheRowsOnceADefinitionFlags() = runTest {
        val definitions = MutableStateFlow(listOf(date(flag = false)))
        val result = observePastDates(definitions, { flowOf(listOf(article)) }, { flowOf(emptyList()) }, today)
        assertEquals(emptyList(), result.first())
        definitions.value = listOf(date(flag = true))
        assertEquals(listOf("a1"), result.first().map { it.articleId })
    }
}

package ch.mathorck.easystock.composables.server

import ch.mathorck.easystock.composables.server.admin.message
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ProblemTypes
import ch.mathorck.easystock.sync.ConflictCodes
import ch.mathorck.easystock.sync.PushDecision
import ch.mathorck.easystock.sync.PushResult
import ch.mathorck.easystock.sync.PushStatus
import ch.mathorck.easystock.sync.RejectReasons
import ch.mathorck.easystock.sync.decidePush
import ch.mathorck.easystock.viewmodels.admin.ActionFailure
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProblemMessagesTest {
    @Test
    fun everyProblemTypeOfTheServerHasAMessage() {
        ProblemTypes.ALL.forEach { type -> assertNotNull(problemMessage(type), type) }
        assertEquals(ProblemTypes.ALL.size, ProblemTypes.ALL.toSet().size)
        // The 39 types of the server's ProblemTypesHelper
        assertEquals(39, ProblemTypes.ALL.size)
    }

    @Test
    fun anUnknownTypeFallsBackToTheStatusMessage() {
        assertNull(problemMessage("a-future-type"))
        assertNull(problemMessage(null))
        assertEquals(ApiError.CONFLICT.message(), ActionFailure(ApiError.CONFLICT, "a-future-type").message())
        assertEquals(ApiError.SERVER.message(), ActionFailure(ApiError.SERVER).message())
    }

    @Test
    fun aKnownTypeBeatsTheStatusMessage() {
        assertEquals(problemMessage(ProblemTypes.ROLE_NOT_ALLOWED), ActionFailure(ApiError.CONFLICT, ProblemTypes.ROLE_NOT_ALLOWED).message())
    }

    @Test
    fun everyRejectReasonOfTheServerHasAMessage() {
        RejectReasons.ALL.forEach { assertNotNull(rejectReasonMessage(it), it) }
        assertNull(rejectReasonMessage("a-future-reason"))
    }

    @Test
    fun everyConflictCodeOfAChangedAnswerIsTakenWithoutAFailure() {
        val sent = JsonObject(mapOf("id" to JsonPrimitive("r1"), "name" to JsonPrimitive("Mine")))
        val server = JsonObject(mapOf("name" to JsonPrimitive("Mine (2)")))
        ConflictCodes.ALL.forEach { code ->
            val result = PushResult("category", "r1", PushStatus.CHANGED, conflict = code, row = server)
            val decision = decidePush(result, sent, sent)
            // Never a Keep (retried forever) or a Gone: the row is written, merged, dropped or offered to the user
            assertIs<PushDecision>(decision)
            assert(decision != PushDecision.Keep && decision != PushDecision.Gone) { code }
        }
        assertEquals(9, ConflictCodes.ALL.size)
    }
}

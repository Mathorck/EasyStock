package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.auth.Role

/** The promotion form of a Guest: the code the Admin types and the role to give. */
data class PromoteForm(
    val expectedCode: String?,
    val typedCode: String = "",
    val role: Role = Role.EDITOR,
) {
    val codeMatches: Boolean get() = shortCodesMatch(expectedCode, typedCode)

    /** Only when the codes match: the Admin promotes the person standing in front of them. */
    val canPromote: Boolean get() = codeMatches && role in PROMOTION_ROLES

    companion object {
        val PROMOTION_ROLES = listOf(Role.EDITOR, Role.VIEWER, Role.ADMIN)
    }
}

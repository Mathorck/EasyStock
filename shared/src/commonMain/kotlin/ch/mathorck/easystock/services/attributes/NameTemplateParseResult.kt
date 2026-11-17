package ch.mathorck.easystock.services.attributes

sealed interface NameTemplateParseResult {
    data class Valid(val template: NameTemplate) : NameTemplateParseResult
    data class Invalid(val error: NameTemplateError) : NameTemplateParseResult
}

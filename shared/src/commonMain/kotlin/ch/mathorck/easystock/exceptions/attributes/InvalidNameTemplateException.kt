package ch.mathorck.easystock.exceptions.attributes

import ch.mathorck.easystock.services.attributes.NameTemplateError

/** The name template does not match the attributes of the category. Nothing is saved. */
class InvalidNameTemplateException(val error: NameTemplateError) : IllegalArgumentException("Invalid name template: $error")

package ch.mathorck.easystock.exceptions.attributes

/** The attribute definition was deleted (or never existed). */
class AttributeNotFoundException(val id: String) : IllegalStateException("No active attribute: $id")

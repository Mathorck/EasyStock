package ch.mathorck.easystock.exceptions.location

import ch.mathorck.easystock.enums.location.LocationError

/** A location write was refused for [error]. The transaction is rolled back. */
class LocationException(val error: LocationError) : IllegalStateException("Location write refused: $error")

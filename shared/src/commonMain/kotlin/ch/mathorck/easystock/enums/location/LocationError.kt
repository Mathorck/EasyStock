package ch.mathorck.easystock.enums.location

/** Why a location write was refused. Nothing is saved. */
enum class LocationError { REQUIRED, TOO_LONG, DUPLICATE, CYCLE, NOT_FOUND, PARENT_NOT_FOUND, HAS_STOCK }

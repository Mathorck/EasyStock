package ch.mathorck.easystock.exceptions.stock

/** The serial number is already used by an item, deleted ones included (unique index). */
class DuplicateSerialException(val serial: String) : IllegalStateException("Serial number already used: $serial")

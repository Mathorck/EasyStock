package ch.mathorck.easystock.enums.article

/** Errors of the action sheet. Separate from [FieldError] so other forms are not affected. */
enum class StockActionError { REQUIRED, INVALID_QUANTITY, NOT_POSITIVE, EXCEEDS_STOCK, TOO_LONG, DUPLICATE_SERIAL, SAME_LOCATION }

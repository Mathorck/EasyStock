package ch.mathorck.easystock.exceptions.stock

/** The article, item or location is gone (deleted meanwhile) or does not match. */
class StockTargetNotFoundException(what: String) : IllegalStateException(what)

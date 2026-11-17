package ch.mathorck.easystock.exceptions.stock

// ---------- Errors ----------

/** Removing or moving more than the stock at the location. Nothing is saved. */
class InsufficientStockException(val availableMilli: Long) : IllegalStateException("Only $availableMilli in stock")

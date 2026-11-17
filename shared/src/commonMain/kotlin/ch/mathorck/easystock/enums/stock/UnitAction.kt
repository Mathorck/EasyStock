package ch.mathorck.easystock.enums.stock

/** Status changes of one serial unit. A move is separate ([StockActionDao.moveItem]). */
enum class UnitAction { LEND, RETURN, REPAIR, END_REPAIR, LOSE, FOUND }

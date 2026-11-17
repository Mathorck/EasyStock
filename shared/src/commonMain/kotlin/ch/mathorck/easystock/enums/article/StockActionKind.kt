package ch.mathorck.easystock.enums.article

/** What the action sheet does. The first three are for quantity-tracked articles, the others for serial units. */
enum class StockActionKind { ADD, REMOVE, MOVE, ADD_UNIT, MOVE_UNIT, UNIT_STATUS, EDIT_UNIT }

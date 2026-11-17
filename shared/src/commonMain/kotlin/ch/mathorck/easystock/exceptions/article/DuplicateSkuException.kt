package ch.mathorck.easystock.exceptions.article

/** The SKU is already used by an article, deleted ones included (the unique index covers them). */
class DuplicateSkuException(val sku: String) : IllegalStateException("SKU already used: $sku")

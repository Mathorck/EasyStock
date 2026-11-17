package ch.mathorck.easystock.exceptions.review

/** The review is not open any more (finished or cancelled), or the line or review is gone. Nothing is saved. */
class ReviewClosedException(what: String) : IllegalStateException(what)

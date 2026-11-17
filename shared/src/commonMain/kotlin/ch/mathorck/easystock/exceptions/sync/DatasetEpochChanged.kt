package ch.mathorck.easystock.exceptions.sync

/** A pull answer with another epoch than the one this phone holds: the server's data set was replaced. */
internal class DatasetEpochChanged : Exception()

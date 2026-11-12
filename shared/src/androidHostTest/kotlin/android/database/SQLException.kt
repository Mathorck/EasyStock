package android.database

/**
 * Host tests only (E2E tests): the Android SQLite driver throws this class, whose android.jar stub drops
 * the message and makes some transactions fail on the JVM (a pull right after a push). This copy, first
 * on the test classpath, behaves like the real one.
 */
open class SQLException : RuntimeException {
    constructor() : super()

    constructor(error: String?) : super(error)

    constructor(error: String?, cause: Throwable?) : super(error, cause)
}

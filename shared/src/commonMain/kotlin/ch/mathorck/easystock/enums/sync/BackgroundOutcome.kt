package ch.mathorck.easystock.enums.sync

/** What a background run tells the scheduler. */
enum class BackgroundOutcome {
    /** Done, or nothing to do now: wait for the next period. */
    SUCCESS,

    /** No network answer: retry with backoff. */
    RETRY,

    /** The session is gone: cancel the job, silently. */
    STOP,
}

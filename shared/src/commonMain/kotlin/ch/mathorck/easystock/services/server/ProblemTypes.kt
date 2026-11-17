package ch.mathorck.easystock.services.server

/**
 * Every Problem `type` the server answers (server Helpers/ProblemTypesHelper.cs, 03-server-spec.md). Bare slugs.
 * A type missing here is a newer server: the screens fall back to the message of the HTTP status.
 */
object ProblemTypes {
    const val VALIDATION_FAILED = "validation-failed"
    const val SHORT_CODE_MISMATCH = "short-code-mismatch"
    const val HASH_MISMATCH = "hash-mismatch"
    const val NOT_A_JPEG = "not-a-jpeg"
    const val INVALID_CREDENTIALS = "invalid-credentials"
    const val ACCOUNT_LOCKED = "account-locked"
    const val ACCOUNT_DISABLED = "account-disabled"
    const val UNAUTHORIZED = "unauthorized"
    const val TOKEN_EXPIRED = "token-expired"
    const val SESSION_REVOKED = "session-revoked"
    const val NOT_ALLOWED = "not-allowed"
    const val GUEST_SIGNUP_OFF = "guest-signup-off"
    const val NOT_FOUND = "not-found"
    const val INVITE_INVALID = "invite-invalid"
    const val NO_PROMOTION = "no-promotion"
    const val SETUP_DONE = "setup-done"
    const val USERNAME_TAKEN = "username-taken"
    const val LAST_ADMIN = "last-admin"
    const val ROLE_NOT_ALLOWED = "role-not-allowed"
    const val USE_PROMOTION = "use-promotion"
    const val GUEST_LIMIT_REACHED = "guest-limit-reached"
    const val BATCH_ID_REUSED = "batch-id-reused"
    const val DATASET_RESET = "dataset-reset"
    const val INVITE_EXPIRED = "invite-expired"
    const val INVITE_USED = "invite-used"
    const val RESET_EXPIRED = "reset-expired"
    const val RESET_USED = "reset-used"
    const val PROMOTION_EXPIRED = "promotion-expired"
    const val IMAGE_TOO_LARGE = "image-too-large"
    const val IMAGE_QUOTA_EXCEEDED = "image-quota-exceeded"
    const val BATCH_TOO_LARGE = "batch-too-large"
    const val BACKUP_TOO_LARGE = "backup-too-large"
    const val UNSUPPORTED_MEDIA_TYPE = "unsupported-media-type"
    const val BACKUP_INVALID = "backup-invalid"
    const val BACKUP_ENCRYPTED = "backup-encrypted"
    const val BACKUP_NEWER_VERSION = "backup-newer-version"
    const val NOT_A_BACKUP = "not-a-backup"
    const val APP_TOO_OLD = "app-too-old"
    const val RATE_LIMITED = "rate-limited"
    const val INTERNAL_ERROR = "internal-error"

    /** All of them, for the tests that check every type has a message. */
    val ALL: List<String> = listOf(
        VALIDATION_FAILED,
        SHORT_CODE_MISMATCH,
        HASH_MISMATCH,
        NOT_A_JPEG,
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED,
        ACCOUNT_DISABLED,
        UNAUTHORIZED,
        TOKEN_EXPIRED,
        SESSION_REVOKED,
        NOT_ALLOWED,
        GUEST_SIGNUP_OFF,
        NOT_FOUND,
        INVITE_INVALID,
        NO_PROMOTION,
        SETUP_DONE,
        USERNAME_TAKEN,
        LAST_ADMIN,
        ROLE_NOT_ALLOWED,
        USE_PROMOTION,
        GUEST_LIMIT_REACHED,
        BATCH_ID_REUSED,
        DATASET_RESET,
        INVITE_EXPIRED,
        INVITE_USED,
        RESET_EXPIRED,
        RESET_USED,
        PROMOTION_EXPIRED,
        IMAGE_TOO_LARGE,
        IMAGE_QUOTA_EXCEEDED,
        BATCH_TOO_LARGE,
        BACKUP_TOO_LARGE,
        UNSUPPORTED_MEDIA_TYPE,
        BACKUP_INVALID,
        BACKUP_ENCRYPTED,
        BACKUP_NEWER_VERSION,
        NOT_A_BACKUP,
        APP_TOO_OLD,
        RATE_LIMITED,
        INTERNAL_ERROR,
    )
}

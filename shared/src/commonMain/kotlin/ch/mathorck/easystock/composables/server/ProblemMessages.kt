package ch.mathorck.easystock.composables.server

import ch.mathorck.easystock.server.ProblemTypes
import ch.mathorck.easystock.sync.RejectReasons
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_error_backup_too_large
import easystock.shared.generated.resources.admin_error_forbidden
import easystock.shared.generated.resources.admin_error_guest_limit
import easystock.shared.generated.resources.admin_error_invalid
import easystock.shared.generated.resources.admin_error_last_admin
import easystock.shared.generated.resources.admin_error_not_found
import easystock.shared.generated.resources.admin_error_rate_limited
import easystock.shared.generated.resources.admin_error_server
import easystock.shared.generated.resources.admin_error_session_revoked
import easystock.shared.generated.resources.admin_error_short_code
import easystock.shared.generated.resources.admin_error_too_old
import easystock.shared.generated.resources.problem_backup_encrypted
import easystock.shared.generated.resources.problem_backup_invalid
import easystock.shared.generated.resources.problem_backup_newer
import easystock.shared.generated.resources.problem_batch_reused
import easystock.shared.generated.resources.problem_batch_too_large
import easystock.shared.generated.resources.problem_dataset_reset
import easystock.shared.generated.resources.problem_hash_mismatch
import easystock.shared.generated.resources.problem_image_too_large
import easystock.shared.generated.resources.problem_not_a_backup
import easystock.shared.generated.resources.problem_not_a_jpeg
import easystock.shared.generated.resources.problem_role_not_allowed
import easystock.shared.generated.resources.problem_unsupported_media
import easystock.shared.generated.resources.problem_use_promotion
import easystock.shared.generated.resources.rejected_reason_conflict
import easystock.shared.generated.resources.rejected_reason_invalid
import easystock.shared.generated.resources.rejected_reason_missing_reference
import easystock.shared.generated.resources.rejected_reason_not_allowed
import easystock.shared.generated.resources.rejected_reason_review_open
import easystock.shared.generated.resources.server_error_account_disabled
import easystock.shared.generated.resources.server_error_account_locked
import easystock.shared.generated.resources.server_error_code_expired
import easystock.shared.generated.resources.server_error_code_invalid
import easystock.shared.generated.resources.server_error_code_used
import easystock.shared.generated.resources.server_error_no_promotion
import easystock.shared.generated.resources.server_error_setup_done
import easystock.shared.generated.resources.server_error_username_taken
import easystock.shared.generated.resources.server_error_wrong_credentials
import easystock.shared.generated.resources.server_guest_off
import org.jetbrains.compose.resources.StringResource

/**
 * The message of a server Problem Details `type`, null for a type this app doesn't know (a newer
 * server): the caller then uses the message of the HTTP status. Every type of [ProblemTypes.ALL] has one.
 */
internal fun problemMessage(type: String?): StringResource? = when (type) {
    ProblemTypes.VALIDATION_FAILED -> Res.string.admin_error_invalid
    ProblemTypes.SHORT_CODE_MISMATCH -> Res.string.admin_error_short_code
    ProblemTypes.HASH_MISMATCH -> Res.string.problem_hash_mismatch
    ProblemTypes.NOT_A_JPEG -> Res.string.problem_not_a_jpeg
    ProblemTypes.INVALID_CREDENTIALS -> Res.string.server_error_wrong_credentials
    ProblemTypes.ACCOUNT_LOCKED -> Res.string.server_error_account_locked
    ProblemTypes.ACCOUNT_DISABLED -> Res.string.server_error_account_disabled
    ProblemTypes.UNAUTHORIZED, ProblemTypes.TOKEN_EXPIRED, ProblemTypes.SESSION_REVOKED -> Res.string.admin_error_session_revoked
    ProblemTypes.NOT_ALLOWED -> Res.string.admin_error_forbidden
    ProblemTypes.GUEST_SIGNUP_OFF -> Res.string.server_guest_off
    ProblemTypes.NOT_FOUND -> Res.string.admin_error_not_found
    ProblemTypes.INVITE_INVALID -> Res.string.server_error_code_invalid
    ProblemTypes.NO_PROMOTION -> Res.string.server_error_no_promotion
    ProblemTypes.SETUP_DONE -> Res.string.server_error_setup_done
    ProblemTypes.USERNAME_TAKEN -> Res.string.server_error_username_taken
    ProblemTypes.LAST_ADMIN -> Res.string.admin_error_last_admin
    ProblemTypes.ROLE_NOT_ALLOWED -> Res.string.problem_role_not_allowed
    ProblemTypes.USE_PROMOTION -> Res.string.problem_use_promotion
    ProblemTypes.GUEST_LIMIT_REACHED -> Res.string.admin_error_guest_limit
    ProblemTypes.BATCH_ID_REUSED -> Res.string.problem_batch_reused
    ProblemTypes.DATASET_RESET -> Res.string.problem_dataset_reset
    ProblemTypes.INVITE_EXPIRED, ProblemTypes.RESET_EXPIRED, ProblemTypes.PROMOTION_EXPIRED -> Res.string.server_error_code_expired
    ProblemTypes.INVITE_USED, ProblemTypes.RESET_USED -> Res.string.server_error_code_used
    ProblemTypes.IMAGE_TOO_LARGE -> Res.string.problem_image_too_large
    ProblemTypes.BATCH_TOO_LARGE -> Res.string.problem_batch_too_large
    ProblemTypes.BACKUP_TOO_LARGE -> Res.string.admin_error_backup_too_large
    ProblemTypes.UNSUPPORTED_MEDIA_TYPE -> Res.string.problem_unsupported_media
    ProblemTypes.BACKUP_INVALID -> Res.string.problem_backup_invalid
    ProblemTypes.BACKUP_ENCRYPTED -> Res.string.problem_backup_encrypted
    ProblemTypes.BACKUP_NEWER_VERSION -> Res.string.problem_backup_newer
    ProblemTypes.NOT_A_BACKUP -> Res.string.problem_not_a_backup
    ProblemTypes.APP_TOO_OLD -> Res.string.admin_error_too_old
    ProblemTypes.RATE_LIMITED -> Res.string.admin_error_rate_limited
    ProblemTypes.INTERNAL_ERROR -> Res.string.admin_error_server
    else -> null
}

/** The message of a `reason` of a rejected row, null for a code this app doesn't know (shown as is). */
internal fun rejectReasonMessage(code: String): StringResource? = when (code) {
    RejectReasons.NOT_ALLOWED -> Res.string.rejected_reason_not_allowed
    RejectReasons.INVALID -> Res.string.rejected_reason_invalid
    RejectReasons.MISSING_REFERENCE -> Res.string.rejected_reason_missing_reference
    RejectReasons.REVIEW_OPEN -> Res.string.rejected_reason_review_open
    RejectReasons.CONFLICT -> Res.string.rejected_reason_conflict
    else -> null
}

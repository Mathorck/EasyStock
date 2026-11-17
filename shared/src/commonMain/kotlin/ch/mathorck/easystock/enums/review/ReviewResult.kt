package ch.mathorck.easystock.enums.review

import ch.mathorck.easystock.data.model.ReviewResultSerializer
import kotlinx.serialization.Serializable

/** What the user found for a review line. */
@Serializable(with = ReviewResultSerializer::class)
enum class ReviewResult {
    UNCHECKED,
    /** Quantity line: counted_quantity is set. */
    COUNTED,
    /** Serial unit found at the line location. */
    PRESENT,
    /** Serial unit not found. */
    MISSING,
    /** Serial unit found at found_location_id. */
    ELSEWHERE,
}

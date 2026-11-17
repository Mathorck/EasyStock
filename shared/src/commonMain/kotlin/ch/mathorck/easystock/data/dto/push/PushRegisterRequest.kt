package ch.mathorck.easystock.data.dto.push

import kotlinx.serialization.Serializable

// Push endpoints, see 03-server-spec.md "Push": the server keeps one UnifiedPush address per session.

/** `POST /push/register` body. [endpoint]: the UnifiedPush address the server sends "sync" to. */
@Serializable
data class PushRegisterRequest(val endpoint: String)

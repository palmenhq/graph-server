package app.superadmin.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreatePasswordResetRequest(
  val username: String,
)

@Serializable
data class CreatePasswordResetResponse(
  val requestId: String,
  val link: String,
  val expiresAt: String,
)

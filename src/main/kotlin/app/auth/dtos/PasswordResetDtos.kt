package app.auth.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CompletePasswordResetRequest(
  val requestId: String,
  val token: String,
  val password: String,
)

@Serializable
data class ValidatePasswordResetRequest(
  val requestId: String,
  val token: String,
)

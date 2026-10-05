package app.auth.dtos

import kotlinx.serialization.Serializable

@Serializable
data class TokenRequest(
  val username: String,
  val password: String,
)

@Serializable
data class TokenResponse(
  val token: String,
)

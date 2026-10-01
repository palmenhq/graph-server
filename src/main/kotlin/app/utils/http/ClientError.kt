package app.utils.http

import kotlinx.serialization.Serializable

@Serializable
data class ClientError(
  val description: String,
  val code: String,
)

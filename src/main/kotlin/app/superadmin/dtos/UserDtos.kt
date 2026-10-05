package app.superadmin.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
  val username: String,
  val password: String,
)

@Serializable
data class UserDto(
  val id: String,
  val username: String,
)

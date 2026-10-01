package app.utils.http

import kotlinx.serialization.Serializable

@Serializable
data class ListResponse<T>(
  val items: List<T>,
)

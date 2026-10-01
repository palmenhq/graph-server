package app.utils.http

open class ClientException(
  val code: String,
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause) {
  fun toError() = ClientError(description = message ?: code, code = code)
}

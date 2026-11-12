package app.utils

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

typealias ResourceId = String

fun UUID.toResourceId(resource: String): ResourceId = "$resource:${this.compress()}"

fun UUID.compress(): String {
  val bb = ByteBuffer.allocate(16)
  bb.putLong(this.mostSignificantBits)
  bb.putLong(this.leastSignificantBits)
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bb.array())
}

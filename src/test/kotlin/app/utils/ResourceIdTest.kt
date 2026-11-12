package app.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ResourceIdTest {
  @Test
  fun testCompress() {
    val uuid = UUID.fromString("df47044d-2413-4469-8707-7a56361eea20")
    val compressed = uuid.compress()
    assertEquals("30cETSQTRGmHB3pWNh7qIA", compressed)
  }
}

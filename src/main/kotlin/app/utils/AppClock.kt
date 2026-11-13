package app.utils

import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

object AppClock {
  private val clock = Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS))

  fun now(): OffsetDateTime = OffsetDateTime.now(clock)
}

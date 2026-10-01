package app.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

object InstantSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor
    get() = Long.serializer().descriptor

  override fun serialize(
    encoder: Encoder,
    value: Instant,
  ) {
    encoder.encodeLong(value.epochSeconds)
  }

  override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochSeconds(decoder.decodeLong())
}

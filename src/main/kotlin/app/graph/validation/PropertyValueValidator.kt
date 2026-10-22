package app.graph.validation

import db.enums.PropertyValueType

object PropertyValueValidator {
  private val instagramPrefixes = socialPrefixes("instagram.com/")
  private val tiktokPrefixes = socialPrefixes("tiktok.com/")
  private val linkPrefixes = listOf("https://", "http://")

  // A bare handle, optionally prefixed with @, e.g. "alice" or "@alice.b_"
  private val handleRegex = Regex("""^@?[A-Za-z0-9._]{1,30}$""")
  private val phoneRegex = Regex("""^\+?[0-9 ()\-]+$""")
  private val emailRegex = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

  /**
   * Throws [InvalidPropertyValueException] if [value] is not valid for [valueType]. A null value (unset) is always valid.
   */
  fun validate(
    valueType: PropertyValueType,
    value: String?,
  ) {
    if (value == null) return
    if (!isValid(valueType, value)) throw InvalidPropertyValueException(valueType.literal)
  }

  fun isValid(
    valueType: PropertyValueType,
    value: String,
  ): Boolean =
    when (valueType) {
      PropertyValueType.text, PropertyValueType.textarea -> true
      PropertyValueType.instagram -> handleRegex.matches(value) || hasPrefixAndRest(value, instagramPrefixes)
      PropertyValueType.tiktok -> handleRegex.matches(value) || hasPrefixAndRest(value, tiktokPrefixes)
      PropertyValueType.link -> hasPrefixAndRest(value, linkPrefixes) && value.none { it.isWhitespace() }
      PropertyValueType.phone -> phoneRegex.matches(value) && value.count { it.isDigit() } in 5..15
      PropertyValueType.email -> emailRegex.matches(value)
    }

  // e.g. "instagram.com/", "www.instagram.com/", "https://instagram.com/" and "https://www.instagram.com/"
  private fun socialPrefixes(domain: String): List<String> =
    listOf("", "https://").flatMap { scheme -> listOf("", "www.").map { www -> scheme + www + domain } }

  private fun hasPrefixAndRest(
    value: String,
    prefixes: List<String>,
  ): Boolean = prefixes.any { value.startsWith(it, ignoreCase = true) && value.length > it.length }
}

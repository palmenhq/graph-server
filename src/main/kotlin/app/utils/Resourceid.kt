package app.utils

import java.util.UUID

typealias ResourceId = String

fun UUID.toResourceId(resource: String): ResourceId = "$resource:$this"

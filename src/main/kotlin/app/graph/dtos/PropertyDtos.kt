package app.graph.dtos

import app.utils.ResourceId
import db.enums.PropertyValueType
import db.tables.pojos.NodeProperty
import db.tables.pojos.NodePropertyValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class CreatePropertyRequest(
  val nodeTypeId: ResourceId,
  val name: String,
  val valueType: PropertyValueTypeDto,
)

@Serializable
data class RenamePropertyRequest(
  val nodeTypeId: ResourceId,
  val propertyId: ResourceId,
  val name: String,
)

@Serializable
data class DeletePropertyRequest(
  val nodeTypeId: ResourceId,
  val propertyId: ResourceId,
)

@Serializable
data class SetPropertyValueRequest(
  val nodeId: ResourceId,
  val propertyId: ResourceId,
  val value: String? = null,
)

@Serializable
data class PropertyDto(
  val id: ResourceId,
  val nodeTypeId: ResourceId,
  val name: String,
  val valueType: String,
  val createdAt: Instant,
  val workspaceId: ResourceId,
)

@Serializable
enum class PropertyValueTypeDto {
  @SerialName("text")
  TEXT,

  @SerialName("textarea")
  TEXTAREA,

  @SerialName("instagram")
  INSTAGRAM,

  @SerialName("tiktok")
  TIKTOK,

  @SerialName("phone")
  PHONE,

  @SerialName("email")
  EMAIL,
}

fun PropertyValueType.toDto(): PropertyValueTypeDto =
  when (this) {
    PropertyValueType.text -> PropertyValueTypeDto.TEXT
    PropertyValueType.textarea -> PropertyValueTypeDto.TEXTAREA
    PropertyValueType.instagram -> PropertyValueTypeDto.INSTAGRAM
    PropertyValueType.tiktok -> PropertyValueTypeDto.TIKTOK
    PropertyValueType.phone -> PropertyValueTypeDto.PHONE
    PropertyValueType.email -> PropertyValueTypeDto.EMAIL
  }

fun PropertyValueTypeDto.toRecord(): PropertyValueType =
  when (this) {
    PropertyValueTypeDto.TEXT -> PropertyValueType.text
    PropertyValueTypeDto.TEXTAREA -> PropertyValueType.textarea
    PropertyValueTypeDto.INSTAGRAM -> PropertyValueType.instagram
    PropertyValueTypeDto.TIKTOK -> PropertyValueType.tiktok
    PropertyValueTypeDto.PHONE -> PropertyValueType.phone
    PropertyValueTypeDto.EMAIL -> PropertyValueType.email
  }

@Serializable
data class PropertyValueDto(
  val nodeId: ResourceId,
  val propertyId: ResourceId,
  val propertyName: String,
  val valueType: PropertyValueTypeDto,
  val value: String?,
  val updatedAt: Instant?,
)

fun NodeProperty.toDto() =
  PropertyDto(
    id = id!!,
    nodeTypeId = nodeTypeId!!,
    name = name!!,
    valueType = valueType!!.literal,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    workspaceId = workspaceId!!,
  )

fun NodePropertyValue.toDto(name: String) =
  PropertyValueDto(
    nodeId = nodeId!!,
    propertyId = propertyId!!,
    propertyName = name,
    valueType = valueType!!.toDto(),
    value = value,
    updatedAt = updatedAt?.toInstant()?.toKotlinInstant(),
  )

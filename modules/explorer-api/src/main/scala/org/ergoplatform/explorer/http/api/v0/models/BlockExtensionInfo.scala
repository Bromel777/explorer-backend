package org.ergoplatform.explorer.http.api.v0.models

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Json}
import org.ergoplatform.explorer.db.models.BlockExtension
import org.ergoplatform.explorer.{HexString, Id}
import sttp.tapir.{Schema, SchemaType, Validator}
import sttp.tapir.json.circe._

final case class BlockExtensionInfo(
  headerId: Id,
  digest: HexString,
  fields: Json
)

object BlockExtensionInfo {

  implicit val codec: Codec[BlockExtensionInfo] = deriveCodec

  implicit val schema: Schema[BlockExtensionInfo] =
    Schema
      .derive[BlockExtensionInfo]
      .modify(_.headerId)(_.description("ID of the corresponding header"))
      .modify(_.digest)(_.description("Hex-encoded extension digest"))

  implicit val validator: Validator[BlockExtensionInfo] = Validator.derive

  def apply(blockExtension: BlockExtension): BlockExtensionInfo =
    BlockExtensionInfo(
      blockExtension.headerId,
      blockExtension.digest,
      blockExtension.fields
    )
}

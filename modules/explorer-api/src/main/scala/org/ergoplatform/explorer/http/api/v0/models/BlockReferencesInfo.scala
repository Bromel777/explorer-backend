package org.ergoplatform.explorer.http.api.v0.models

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.Id
import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.Derived

final case class BlockReferencesInfo(previousId: Id, nextId: Option[Id])

object BlockReferencesInfo {

  implicit val codec: Codec[BlockReferencesInfo] = deriveCodec

  implicit val schema: Schema[BlockReferencesInfo] =
    Schema
      .derive[BlockReferencesInfo]
      .modify(_.previousId)(_.description("ID of the previous block"))
      .modify(_.nextId)(_.description("ID of the next block (if one exists)"))

  implicit val validator: Validator[BlockReferencesInfo] = Validator.derive
}

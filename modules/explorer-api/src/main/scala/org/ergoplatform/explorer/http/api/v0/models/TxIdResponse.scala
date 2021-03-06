package org.ergoplatform.explorer.http.api.v0.models

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.TxId
import sttp.tapir.{Schema, Validator}

final case class TxIdResponse(id: TxId)

object TxIdResponse {

  implicit val codec: Codec[TxIdResponse] = deriveCodec

  implicit val schema: Schema[TxIdResponse] =
    Schema
      .derive[TxIdResponse]
      .modify(_.id)(_.description("Id of submitted transaction"))

  implicit val validator: Validator[TxIdResponse] = Validator.derive
}

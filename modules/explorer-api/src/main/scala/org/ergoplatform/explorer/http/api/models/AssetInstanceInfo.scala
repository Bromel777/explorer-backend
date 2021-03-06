package org.ergoplatform.explorer.http.api.models

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.{TokenId, TokenType}
import org.ergoplatform.explorer.db.models.aggregates.{ExtendedAsset, ExtendedUAsset}
import sttp.tapir.{Schema, Validator}

final case class AssetInstanceInfo(
  tokenId: TokenId,
  index: Int,
  amount: Long,
  name: Option[String],
  decimals: Option[Int],
  `type`: Option[TokenType]
)

object AssetInstanceInfo {

  def apply(asset: ExtendedUAsset): AssetInstanceInfo =
    AssetInstanceInfo(asset.tokenId, asset.index, asset.amount, asset.name, asset.decimals, asset.`type`)

  def apply(asset: ExtendedAsset): AssetInstanceInfo =
    AssetInstanceInfo(asset.tokenId, asset.index, asset.amount, asset.name, asset.decimals, asset.`type`)

  implicit val codec: Codec[AssetInstanceInfo] = deriveCodec

  implicit val schema: Schema[AssetInstanceInfo] =
    Schema
      .derive[AssetInstanceInfo]
      .modify(_.tokenId)(_.description("Token ID"))
      .modify(_.index)(_.description("Index of the asset in an output"))
      .modify(_.amount)(_.description("Amount of tokens"))
      .modify(_.name)(_.description("Name of a token"))
      .modify(_.decimals)(_.description("Number of decimal places"))
      .modify(_.`type`)(_.description("Type of a token (token standard)"))

  implicit val validator: Validator[AssetInstanceInfo] = Validator.derive
}

package org.ergoplatform.explorer.http.api.v1.services

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Sync
import cats.syntax.list._
import cats.{Functor, Monad}
import fs2.{Chunk, Pipe, Stream}
import mouse.anyf._
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.explorer.Err.{RefinementFailed, RequestProcessingErr}
import org.ergoplatform.explorer._
import org.ergoplatform.explorer.db.Trans
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.models.Output
import org.ergoplatform.explorer.db.models.aggregates.ExtendedOutput
import org.ergoplatform.explorer.db.repositories.{AssetRepo, HeaderRepo, OutputRepo}
import org.ergoplatform.explorer.http.api.models.{Epochs, Items, Paging}
import org.ergoplatform.explorer.http.api.streaming.CompileStream
import org.ergoplatform.explorer.http.api.v1.models.{BoxQuery, OutputInfo}
import org.ergoplatform.explorer.protocol.sigma._
import org.ergoplatform.explorer.settings.ServiceSettings
import org.ergoplatform.explorer.syntax.stream._
import tofu.fs2Instances._
import tofu.syntax.monadic._
import tofu.syntax.streams.compile._

trait BoxesService[F[_]] {

  /** Get output by `boxId`.
    */
  def getOutputById(id: BoxId): F[Option[OutputInfo]]

  /** Get all outputs with the given `address` in proposition.
    */
  def getOutputsByAddress(address: Address, paging: Paging): F[Items[OutputInfo]]

  /** Get unspent outputs with the given `address` in proposition.
    */
  def getUnspentOutputsByAddress(address: Address, paging: Paging): F[Items[OutputInfo]]

  /** Get all outputs with the given `ergoTree` in proposition.
    */
  def getOutputsByErgoTree(ergoTree: HexString, paging: Paging): F[Items[OutputInfo]]

  /** Get unspent outputs with the given `ergoTree` in proposition.
    */
  def getUnspentOutputsByErgoTree(ergoTree: HexString, paging: Paging): F[Items[OutputInfo]]

  /** Get all outputs containing a given `tokenId`.
    */
  def getOutputsByErgoTreeTemplateHash(template: ErgoTreeTemplateHash, paging: Paging): F[Items[OutputInfo]]

  /** Get all unspent outputs containing a given `tokenId`.
    */
  def getUnspentOutputsByErgoTreeTemplateHash(template: ErgoTreeTemplateHash, paging: Paging): F[Items[OutputInfo]]

  /** Get all outputs containing a given `tokenId`.
    */
  def streamOutputsByErgoTreeTemplateHash(template: ErgoTreeTemplateHash, epochs: Epochs): Stream[F, OutputInfo]

  /** Get all unspent outputs containing a given `tokenId`.
    */
  def streamUnspentOutputsByErgoTreeTemplateHash(template: ErgoTreeTemplateHash, epochs: Epochs): Stream[F, OutputInfo]

  /** Get all unspent outputs appeared in the blockchain after `minHeight`.
    */
  def streamUnspentOutputs(epochs: Epochs): Stream[F, OutputInfo]

  /** Get all unspent outputs appeared in the blockchain within a given `lastEpochs`.
    */
  def streamUnspentOutputs(lastEpochs: Int): Stream[F, OutputInfo]

  /** Get all outputs containing a given `tokenId`.
    */
  def getOutputsByTokenId(tokenId: TokenId, paging: Paging): F[Items[OutputInfo]]

  /** Get all unspent outputs containing a given `tokenId`.
    */
  def getUnspentOutputsByTokenId(tokenId: TokenId, paging: Paging): F[Items[OutputInfo]]

  /** Get all outputs matching a given `boxQuery`.
    */
  def searchAll(boxQuery: BoxQuery, paging: Paging): F[Items[OutputInfo]]
}

object BoxesService {

  def apply[
    F[_]: Sync,
    D[_]: CRaise[*[_], RequestProcessingErr]: CRaise[*[_], RefinementFailed]: LiftConnectionIO: Monad
  ](serviceSettings: ServiceSettings)(trans: D Trans F)(implicit e: ErgoAddressEncoder): F[BoxesService[F]] =
    (HeaderRepo[F, D], OutputRepo[F, D], AssetRepo[F, D]).mapN(new Live(serviceSettings, _, _, _)(trans))

  final private class Live[
    F[_]: Functor: CompileStream,
    D[_]: CRaise[*[_], RequestProcessingErr]: CRaise[*[_], RefinementFailed]: Monad
  ](
    serviceSettings: ServiceSettings,
    headers: HeaderRepo[D],
    outputs: OutputRepo[D, Stream],
    assets: AssetRepo[D, Stream]
  )(trans: D Trans F)(implicit e: ErgoAddressEncoder)
    extends BoxesService[F] {

    def getOutputById(id: BoxId): F[Option[OutputInfo]] =
      (for {
        box    <- OptionT(outputs.getByBoxId(id))
        assets <- OptionT.liftF(assets.getAllByBoxId(box.output.boxId))
      } yield OutputInfo(box, assets)).value.thrushK(trans.xa)

    def getOutputsByAddress(address: Address, paging: Paging): F[Items[OutputInfo]] =
      (addressToErgoTreeHex(address).asStream >>= (outputs.streamAllByErgoTree(_, paging.offset, paging.limit)))
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getUnspentOutputsByAddress(address: Address, paging: Paging): F[Items[OutputInfo]] =
      (addressToErgoTreeHex(address).asStream >>= (outputs.streamUnspentByErgoTree(_, paging.offset, paging.limit)))
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getOutputsByErgoTree(ergoTree: HexString, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .streamAllByErgoTree(ergoTree, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getUnspentOutputsByErgoTree(ergoTree: HexString, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .streamUnspentByErgoTree(ergoTree, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getOutputsByErgoTreeTemplateHash(hash: ErgoTreeTemplateHash, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .streamAllByErgoTreeTemplateHash(hash, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getUnspentOutputsByErgoTreeTemplateHash(hash: ErgoTreeTemplateHash, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .streamUnspentByErgoTreeTemplateHash(hash, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toUnspentOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def streamOutputsByErgoTreeTemplateHash(hash: ErgoTreeTemplateHash, epochs: Epochs): Stream[F, OutputInfo] =
      outputs
        .streamAllByErgoTreeTemplateHashByEpochs(hash, epochs.minHeight, epochs.maxHeight)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)

    def streamUnspentOutputsByErgoTreeTemplateHash(hash: ErgoTreeTemplateHash, epochs: Epochs): Stream[F, OutputInfo] =
      outputs
        .streamUnspentByErgoTreeTemplateHashByEpochs(hash, epochs.minHeight, epochs.maxHeight)
        .chunkN(serviceSettings.chunkSize)
        .through(toUnspentOutputInfo)
        .thrushK(trans.xas)

    def streamUnspentOutputs(epochs: Epochs): Stream[F, OutputInfo] =
      outputs
        .getAllMainUnspent(epochs.minHeight, epochs.maxHeight)
        .chunkN(serviceSettings.chunkSize)
        .through(toUnspentOutputInfo)
        .thrushK(trans.xas)

    def streamUnspentOutputs(lastEpochs: Int): Stream[F, OutputInfo] =
      Stream
        .eval(headers.getBestHeight)
        .flatMap { bestHeight =>
          outputs.getAllMainUnspent(bestHeight - lastEpochs, bestHeight)
        }
        .chunkN(serviceSettings.chunkSize)
        .through(toUnspentOutputInfo)
        .thrushK(trans.xas)

    def getOutputsByTokenId(tokenId: TokenId, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .getAllByTokenId(tokenId, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def getUnspentOutputsByTokenId(tokenId: TokenId, paging: Paging): F[Items[OutputInfo]] =
      outputs
        .getUnspentByTokenId(tokenId, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toUnspentOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))

    def searchAll(boxQuery: BoxQuery, paging: Paging): F[Items[OutputInfo]] = {
      val registers = boxQuery.registers.flatMap(rs => NonEmptyList.fromList(rs.toList))
      val constants = boxQuery.constants.flatMap(cs => NonEmptyList.fromList(cs.toList))
      val assets    = boxQuery.assets.flatMap(NonEmptyList.fromList)
      outputs
        .searchAll(boxQuery.ergoTreeTemplateHash, registers, constants, assets, paging.offset, paging.limit)
        .chunkN(serviceSettings.chunkSize)
        .through(toOutputInfo)
        .thrushK(trans.xas)
        .to[List]
        .map(items => Items(items, items.size))
    }

    private def toOutputInfo: Pipe[D, Chunk[ExtendedOutput], OutputInfo] =
      for {
        outs   <- _
        outIds <- Stream.emit(outs.toList.map(_.output.boxId).toNel).unNone
        assets <- assets.getAllByBoxIds(outIds).map(_.groupBy(_.boxId)).asStream
        outsInfo = outs.map(out => OutputInfo(out, assets.getOrElse(out.output.boxId, Nil)))
        flattened <- Stream.emits(outsInfo.toList)
      } yield flattened

    private def toUnspentOutputInfo: Pipe[D, Chunk[Output], OutputInfo] =
      for {
        outs   <- _
        outIds <- Stream.emit(outs.toList.map(_.boxId).toNel).unNone
        assets <- assets.getAllByBoxIds(outIds).map(_.groupBy(_.boxId)).asStream
        outsInfo = outs.map(out => OutputInfo.unspent(out, assets.getOrElse(out.boxId, Nil)))
        flattened <- Stream.emits(outsInfo.toList)
      } yield flattened
  }
}

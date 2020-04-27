package org.ergoplatform.explorer.db.repositories

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import doobie.free.implicits._
import doobie.refined.implicits._
import doobie.util.log.LogHandler
import fs2.Stream
import org.ergoplatform.explorer.TxId
import org.ergoplatform.explorer.db.DoobieLogHandler
import org.ergoplatform.explorer.LiftConnectionIO
import org.ergoplatform.explorer.db.models.UInput
import org.ergoplatform.explorer.db.syntax.liftConnIO._
import org.ergoplatform.explorer.db.doobieInstances._

/** [[UInput]] data access operations.
  */
trait UInputRepo[D[_], S[_[_], _]] {

  /** Put a given `input` to persistence.
    */
  def insert(input: UInput): D[Unit]

  /** Put a given list of inputs to persistence.
    */
  def insetMany(inputs: List[UInput]): D[Unit]

  /** Get all inputs containing in unconfirmed transactions.
    */
  def getAll(offset: Int, limit: Int): S[D, UInput]

  /** Get all inputs related to transaction with a given `txId`.
    */
  def getAllByTxId(txId: TxId): D[List[UInput]]

  /** Get all inputs related to transaction with a given list of `txId`.
    */
  def getAllByTxIds(txIds: NonEmptyList[TxId]): D[List[UInput]]
}

object UInputRepo {

  def apply[F[_]: Sync, D[_]: LiftConnectionIO]: F[UInputRepo[D, Stream]] =
    DoobieLogHandler.create[F].map { implicit lh =>
      new Live[D]
    }

  final private class Live[D[_]: LiftConnectionIO](implicit lh: LogHandler)
    extends UInputRepo[D, Stream] {

    import org.ergoplatform.explorer.db.queries.{UInputQuerySet => QS}

    def insert(input: UInput): D[Unit] =
      QS.insert(input).void.liftConnIO

    def insetMany(inputs: List[UInput]): D[Unit] =
      QS.insertMany(inputs).void.liftConnIO

    def getAll(offset: Int, limit: Int): Stream[D, UInput] =
      QS.getAll(offset, limit).stream.translate(implicitly[LiftConnectionIO[D]].liftF)

    def getAllByTxId(txId: TxId): D[List[UInput]] =
      QS.getAllByTxId(txId).to[List].liftConnIO

    def getAllByTxIds(txIds: NonEmptyList[TxId]): D[List[UInput]] =
      QS.getAllByTxIxs(txIds).to[List].liftConnIO
  }
}

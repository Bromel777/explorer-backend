package org.ergoplatform.explorer.db.queries

import cats.data.NonEmptyList
import doobie.Fragments
import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.refined.implicits._
import fs2.Stream
import org.ergoplatform.explorer.db.models.aggregates.ExtendedOutput
import org.ergoplatform.explorer._

/** A set of queries for doobie implementation of [OutputRepo].
  */
object OutputQuerySet extends QuerySet {

  import org.ergoplatform.explorer.db.doobieInstances._

  val tableName: String = "node_outputs"

  val fields: List[String] = List(
    "box_id",
    "tx_id",
    "value",
    "creation_height",
    "index",
    "ergo_tree",
    "address",
    "additional_registers",
    "timestamp",
    "main_chain"
  )

  def getByBoxId(boxId: BoxId): ConnectionIO[Option[ExtendedOutput]] =
    sql"""
         |select
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.box_id = $boxId
         |""".stripMargin.query[ExtendedOutput].option

  def getAllByErgoTree(
    ergoTree: HexString
  ): ConnectionIO[List[ExtendedOutput]] =
    sql"""
         |select
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.ergo_tree = $ergoTree
         |""".stripMargin.query[ExtendedOutput].to[List]

  def getByErgoTree(
    ergoTree: HexString,
    offset: Int,
    limit: Int
  ): Stream[ConnectionIO, ExtendedOutput] =
    sql"""
         |select
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.ergo_tree = $ergoTree
         |offset $offset limit $limit
         |""".stripMargin.query[ExtendedOutput].stream

  def getAllMainUnspentByErgoTree(
    ergoTree: HexString
  ): ConnectionIO[List[ExtendedOutput]] =
    sql"""
         |select
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.main_chain = true
         |  and (i.box_id is null or i.main_chain = false)
         |  and o.ergo_tree = $ergoTree
         |""".stripMargin.query[ExtendedOutput].to[List]

  def getMainUnspentByErgoTree(
    ergoTree: HexString,
    offset: Int,
    limit: Int
  ): Stream[ConnectionIO, ExtendedOutput] =
    sql"""
         |select
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.main_chain = true
         |  and (i.box_id is null or i.main_chain = false)
         |  and o.ergo_tree = $ergoTree
         |offset $offset limit $limit
         |""".stripMargin.query[ExtendedOutput].stream

  def getAllByTxId(txId: TxId): ConnectionIO[List[ExtendedOutput]] =
    sql"""
         |select distinct on (i.box_id)
         |  o.box_id,
         |  o.tx_id,
         |  o.value,
         |  o.creation_height,
         |  o.index,
         |  o.ergo_tree,
         |  o.address,
         |  o.additional_registers,
         |  o.timestamp,
         |  o.main_chain,
         |  i.tx_id
         |from node_outputs o
         |left join node_inputs i on o.box_id = i.box_id
         |where o.tx_id = $txId
         |""".stripMargin.query[ExtendedOutput].to[List]

  def getAllByTxIds(
    txIds: NonEmptyList[TxId]
  ): ConnectionIO[List[ExtendedOutput]] = {
    val q =
      sql"""
           |select distinct on (i.box_id)
           |  o.box_id,
           |  o.tx_id,
           |  o.value,
           |  o.creation_height,
           |  o.index,
           |  o.ergo_tree,
           |  o.address,
           |  o.additional_registers,
           |  o.timestamp,
           |  o.main_chain,
           |  i.tx_id
           |from node_outputs o
           |left join node_inputs i on o.box_id = i.box_id
           |""".stripMargin
    (q ++ Fragments.in(fr"where o.tx_id", txIds))
      .query[ExtendedOutput]
      .to[List]
  }

  def searchAddressesBySubstring(substring: String): ConnectionIO[List[Address]] =
    sql"select address from node_outputs where address like ${"%" + substring + "%"}"
      .query[Address]
      .to[List]

  def updateChainStatusByHeaderId(
    headerId: Id
  )(newChainStatus: Boolean): ConnectionIO[Int] =
    sql"""
         |update node_outputs set main_chain = $newChainStatus from node_outputs o
         |left join node_transactions t on t.id = o.tx_id
         |left join node_headers h on t.header_id = h.id
         |where h.id = $headerId
         |""".stripMargin.update.run
}
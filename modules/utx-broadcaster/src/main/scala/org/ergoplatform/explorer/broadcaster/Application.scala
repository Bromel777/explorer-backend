package org.ergoplatform.explorer.broadcaster

import cats.effect.{ExitCode, Resource}
import cats.syntax.functor._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import monix.eval.{Task, TaskApp}
import org.ergoplatform.explorer.cache.Redis
import org.ergoplatform.explorer.clients.ergo.ErgoNetworkClient
import org.ergoplatform.explorer.settings.UtxBroadcasterSettings
import org.http4s.client.blaze.BlazeClientBuilder
import org.ergoplatform.explorer.settings.pureConfigInstances._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext.global

/** A service broadcasting new transactions to the network.
  */
object Application extends TaskApp {

  def run(args: List[String]): Task[ExitCode] =
    resources(args.headOption).use {
      case (logger, settings, client, redis) =>
        logger.info("Starting UtxBroadcaster service ..") >>
        ErgoNetworkClient[Task](client, settings.masterNodesAddresses)
          .flatMap { ns =>
            UtxBroadcaster[Task](settings, ns, redis)
              .flatMap(_.run.compile.drain)
              .as(ExitCode.Success)
          }
          .guarantee(logger.info("Stopping UtxBroadcaster service .."))
    }

  private def resources(configPathOpt: Option[String]) =
    for {
      logger   <- Resource.liftF(Slf4jLogger.create)
      settings <- Resource.liftF(UtxBroadcasterSettings.load(configPathOpt))
      client   <- BlazeClientBuilder[Task](global).resource
      redis    <- Redis[Task](settings.redis)
    } yield (logger, settings, client, redis)
}

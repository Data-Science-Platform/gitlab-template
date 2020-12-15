package de.frosner.gitlabtemplate

import java.util.concurrent._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import play.api.libs.ws.ahc._
import pureconfig._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Try}

object Main extends StrictLogging {

  type PublicKeyType = String
  type Username = String

  def main(args: Array[String]): Unit = {
    val conf = loadConfigOrThrow[GitlabTemplateConfig](ConfigFactory.load().getConfig("gitlab-template"))

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    sys.addShutdownHook {
      system.terminate()
    }

    run(conf) match {
      case Failure(throwable) =>
        throwable.printStackTrace()
        System.exit(1)
      case _ =>
        System.exit(0)
    }
  }

  def run(conf: GitlabTemplateConfig)(implicit actorMaterializer: ActorMaterializer,
                                      executionContext: ExecutionContext): Try[Unit] = {
    val wsClient = StandaloneAhcWSClient()
    val result = Try {
      val gitlabConf = conf.source.gitlab
      val filesystemConf = conf.sink.filesystem
      val technicalUsersKeysConf = conf.source.technicalUsersKeys

      val gitlabClient =
        new GitlabSource(wsClient,
                         gitlabConf.url,
                         gitlabConf.privateToken,
                         gitlabConf.onlyActiveUsers,
                         gitlabConf.perPage,
                         gitlabConf.throttleDuration)

      val technicalUsersKeysSource =
        new TechnicalUsersKeysSource(wsClient,
                                     technicalUsersKeysConf.url,
                                     technicalUsersKeysConf.httpBasicAuth,
                                     technicalUsersKeysConf.privateTokenAuth)

      val filesystemSink =
        new FileSystemSink(filesystemConf.path, filesystemConf.publicKeysFile, filesystemConf.createEmptyKeyFile)

      val pipeline = new KeyPipeline(gitlabClient, technicalUsersKeysSource, filesystemSink, conf.dryRun)

      while (true) {
        pipeline
          .generateKeys(conf.timeout)
          .failed
          .foreach { error =>
            logger.error(error.toString)
            throw error
          }
        Thread.sleep(conf.renderFrequency.toMillis)
      }
    }
    result.failed.foreach(_ => wsClient.close())
    result
  }

}

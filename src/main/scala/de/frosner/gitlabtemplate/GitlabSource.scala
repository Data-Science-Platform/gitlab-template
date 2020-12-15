/*
 * Copyright 2017 Frank Rosner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.frosner.gitlabtemplate

import cats.data.EitherT
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.StandaloneWSClient
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import de.frosner.gitlabtemplate.Error.GitlabError
import GitlabUser.format
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import de.frosner.gitlabtemplate.Main.{PublicKeyType, Username}

import scala.concurrent.{ExecutionContext, Future}

class GitlabSource(wsClient: StandaloneWSClient,
                   url: String,
                   privateToken: String,
                   onlyActiveUsers: Boolean,
                   perPage: Int,
                   throttleDuration: Int)(implicit ec: ExecutionContext, materializer: Materializer)
    extends StrictLogging {

  def getUsers: EitherT[Future, Error, Set[GitlabUser]] = {
    val activeFilter = if (onlyActiveUsers) "&active=true" else ""

    EitherT[Future, Error, Set[GitlabUser]](
      Source(Stream.from(1))
        .mapAsync(1)(
          pageNumber => {
            val request = wsClient
              .url(s"$url/api/v4/users?per_page=$perPage&page=$pageNumber$activeFilter")
              .withHttpHeaders(("PRIVATE-TOKEN", privateToken))
            logger.debug(s"Requesting users: ${request.url}")
            request.get().map { response =>
              val body = response.body[JsValue]
              Json.fromJson[Set[GitlabUser]](body).asEither.leftMap(GitlabError)
            }
          }
        )
        .takeWhile(either => either.exists(_.nonEmpty), inclusive = true)
        .runReduce((r1, r2) => r1.combine(r2)))

  }

  def getSshKeys(users: Set[GitlabUser]): EitherT[Future, Error, Map[Username, Set[PublicKeyType]]] = {
    val futureUsersAndKeys =
      Future.sequence {
        users.map { user =>
          val request = wsClient
            .url(s"$url/api/v4/users/${user.id}/keys")
            .withHttpHeaders(("PRIVATE-TOKEN", privateToken))
          logger.debug(s"Requesting public keys for ${user.username}: ${request.url}")
          Thread.sleep(throttleDuration)
          request.get().map { response =>
            Json
              .fromJson[Set[PublicKey]](response.body[JsValue])
              .asEither
              .map(keys => (user.username, keys.map(_.key)))
          }
        }.toList
      }
    EitherT(futureUsersAndKeys.map(_.sequenceU)).map(_.toMap).leftMap(GitlabError)
  }

}

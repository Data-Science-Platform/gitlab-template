package de.frosner.gitlabtemplate

import java.net.UnknownHostException

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.RouteDirectives
import akka.stream.Materializer
import com.fasterxml.jackson.core.JsonParseException
import org.scalatest.{Assertion, FlatSpec, Matchers}
import play.api.libs.ws.StandaloneWSClient

import scala.concurrent.{ExecutionContext, Future}

class GitlabSourceSpec extends FlatSpec with Matchers with HttpTests {

  def usersServer(users: Array[GitlabUser])(
      assertionGen: ExecutionContext => Materializer => (StandaloneWSClient, String) => Future[Assertion]): Assertion =
    withServerAndClient {
      path("api" / "v4" / "users") {
        get {
          parameters('per_page.as[Int], 'page.as[Int]) { (perPage, page) =>
            {
              assert(perPage > 0)
              assert(page > 0)
              val lowerLimit = perPage * (page - 1)
              if (lowerLimit > (users.length - 1)) {
                complete(s"""[]""")
              } else {
                val upperLimit = Math.min(lowerLimit + Math.min(perPage, users.length), users.length)
                complete(
                  "[" +
                    (for (i <- lowerLimit until upperLimit)
                      yield s"""{ "id": ${users(i).id}, "username": "${users(i).username}" }""").mkString(",") +
                    "]"
                )
              }
            }
          }
        }
      }
    }(assertionGen)

  "Getting users through a Gitlab source" should "work" in {
    usersServer(Array(GitlabUser(1, "usr1"), GitlabUser(2, "usr2"))) { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val expected = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2")
          )
          new GitlabSource(wsClient, address, "token", false, 100, 1).getUsers.value
            .map(_ shouldBe Right(expected))
      }
    }
  }

  "Getting users through a Gitlab source should work with pagination" should "work" in {
    usersServer(
      Array(
        GitlabUser(1, "usr1"),
        GitlabUser(2, "usr2"),
        GitlabUser(3, "usr3"),
        GitlabUser(4, "usr4"),
        GitlabUser(5, "usr5"),
        GitlabUser(6, "usr6"),
        GitlabUser(7, "usr7")
      )) { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val expected = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2"),
            GitlabUser(3, "usr3"),
            GitlabUser(4, "usr4"),
            GitlabUser(5, "usr5"),
            GitlabUser(6, "usr6"),
            GitlabUser(7, "usr7")
          )
          new GitlabSource(wsClient, address, "token", false, 2, 1).getUsers.value
            .map(_ shouldBe Right(expected))
      }
    }
  }

  it should "fail if it cannot parse the response" in {
    withServerAndClient {
      path("api" / "v4" / "users") {
        get {
          complete(s"""
                      |[
                      |  { "iddd": 1, "username": "usr1" },
                      |  { "id": 2, "username": "usr2" }
                      |]
             """.stripMargin)
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token", false, 100, 1).getUsers.value
            .map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if gitlab does not respond" in {
    withServerAndClient { RouteDirectives.reject } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, "http://dsafdsgdfsfdsfdsf", "token", false, 100, 1).getUsers.value.failed
            .map(_ shouldBe a[UnknownHostException])
      }
    }
  }

  "Getting SSH keys through a Gitlab source" should "work" in {
    withServerAndClient {
      path("api" / "v4" / "users" / "1" / "keys") {
        get {
          complete("""[ { "key": "key1" }, { "key": "key2" } ]""")
        }
      } ~ path("api" / "v4" / "users" / "2" / "keys") {
        get {
          complete("""[ { "key": "key3" } ]""")
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          val users = Set(
            GitlabUser(1, "usr1"),
            GitlabUser(2, "usr2")
          )
          val expected = Map(
            "usr1" -> Set("key1", "key2"),
            "usr2" -> Set("key3")
          )
          new GitlabSource(wsClient, address, "token", false, 100, 1)
            .getSshKeys(users)
            .value
            .map(_ shouldBe Right(expected))
      }
    }
  }

  it should "fail if the keys cannot be parsed" in {
    withServerAndClient {
      path("api" / "v4" / "users" / "1" / "keys") {
        get {
          complete("""[ { "foo": "bar" } ]""")
        }
      }
    } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token", false, 100, 1)
            .getSshKeys(Set(GitlabUser(1, "usr1")))
            .value
            .map(_ shouldBe a[Left[_, _]])
      }
    }
  }

  it should "fail if the keys endpoint does not exist" in {
    withServerAndClient { RouteDirectives.reject } { implicit ec => implicit materializer =>
      {
        case (wsClient, address) =>
          new GitlabSource(wsClient, address, "token", false, 100, 1)
            .getSshKeys(Set(GitlabUser(1, "usr1")))
            .value
            .failed
            .map(_ shouldBe a[JsonParseException])
      }
    }
  }

}

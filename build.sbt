import ReleaseTransformations._

enablePlugins(DockerPlugin)

// Commands to run on Travis CI
val validateCommands = List(
  "clean",
  "compile",
  "test:compile",
  "coverage",
  "test",
  "doc",
  "coverageReport"
)
// Do-it-all build alias for Travis CI
addCommandAlias("validate", validateCommands.mkString(";", ";", ""))

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    name := "gitlab-template",
    startYear := Some(2017),
    description := "Rendering your authorized_keys files based on Gitlab since 2017!",
    homepage := Some(url(s"https://github.com/Data-Science-Platform/gitlab-template")),
    licenses += "Apache 2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
    // Release settings: Publish maven style, sign our releases, and define the release steps
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    mainClass in Compile := Some("de.frosner.gitlabtemplate.Main"),
    Defaults.itSettings,
    // Build settings of this project
    // Macro tooling (for simulacrum and others)
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    // A set of useful dependencies
    libraryDependencies ++= List(
      // Basic data structures for funtional programming
      "org.typelevel" %% "cats-core" % "0.9.0",
      // Enum types,
      "com.beachape" %% "enumeratum" % "1.5.11",
      "com.beachape" %% "enumeratum-play-json" % "1.5.12",
      // HTTP client
      "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.1.3",
      // HTTP client JSON support
      "com.typesafe.play" %% "play-ws-standalone-json" % "1.1.3",
      // HTTP server for testing
      "com.typesafe.akka" %% "akka-http" % "10.0.10",
      // Nicer streams
      "com.typesafe.akka" %% "akka-stream" % "2.5.12",
      // Nicer syntax for type classes
      "com.github.mpilquist" %% "simulacrum" % "0.10.0",
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
      // Generic programming over data structures
      "com.chuusai" %% "shapeless" % "2.3.2",
      // Test framework
      "org.scalatest" %% "scalatest" % "3.0.1" % "it,test",
      // Property testing
      "org.scalacheck" %% "scalacheck" % "1.13.4" % "it,test",
      // Mocking
      "org.mockito" % "mockito-core" % "2.7.19" % Test,
      // Config
      "com.github.pureconfig" %% "pureconfig" % "0.8.0"
    ),
    dockerfile in docker := {
      // The assembly task generates a fat JAR file
      val artifact: File = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"
      val userName = "gitlabtemplate"
      val runScript = "run.sh"

      new Dockerfile {
        from("openjdk:8-jre")
        run("useradd", "-ms", "/bin/bash", userName)
        user(userName)
        add(artifact, artifactTargetPath)
        add(Path(s"src/main/resources/$runScript").asFile, s"/$runScript")
        entryPoint("bash", s"/$runScript", artifactTargetPath)
        volume("/ssh-keys")
      }
    },
    imageNames in docker := Seq(
      Some((version in Compile).value),
      Option(System.getenv("TRAVIS_COMMIT"))
        .map(_.substring(0, 8))
    ).filter(_.isDefined).map { tag =>
      ImageName(
        namespace = Some(organization.value),
        repository = name.value,
        tag = tag
      )
    },
    // Build settings for all projects in this build
    inThisBuild(
      List(
        organization := "datascienceplatform",
        scmInfo := Some(ScmInfo(
          url("https://github.com/Data-Science-Platform/gitlab-template"),
          "scm:git:https://github.com/Data-Science-Platform/gitlab-template.git",
          Some(s"scm:git:git@github.com:Data-Science-Platform/gitlab-template.git")
        )),
        // Credentials for Travis CI, see
        // http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
        credentials ++= (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq,
        // Release settings for all projects
        releaseTagComment := s"gitlab-template ${version.value}",
        releaseCommitMessage := s"Bump version to ${version.value}",
        releaseCrossBuild := true,
        releaseProcess := List[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          publishArtifacts,
          setNextVersion,
          commitNextVersion,
          pushChanges,
          releaseStepCommand("sonatypeRelease")
        ),
        test in assembly := {},
        // Scala versions we build for
        scalaVersion := "2.12.1",
        // Build settings
        scalacOptions ++= List(
          // Code encoding
          "-encoding",
          "UTF-8",
          // Deprecation warnings
          "-deprecation",
          // Warnings about features that should be imported explicitly
          "-feature",
          // Enable additional warnings about assumptions in the generated code
          "-unchecked",
          // Recommended additional warnings
          "-Xlint",
          // Warn when argument list is modified to match receiver
          "-Ywarn-adapted-args",
          // Warn about dead code
          "-Ywarn-dead-code",
          // Warn about inaccessible types in signatures
          "-Ywarn-inaccessible",
          // Warn when non-nullary overrides a nullary (def foo() over def foo)
          "-Ywarn-nullary-override",
          // Warn when numerics are unintentionally widened
          "-Ywarn-numeric-widen",
          // Fail compilation on warnings
          "-Xfatal-warnings"
        )
      )
    )
  )

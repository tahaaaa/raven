import play.twirl.sbt.SbtTwirl
import sbt._
import Keys._
import play.twirl.sbt.Import.TwirlKeys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{PathList, MergeStrategy}
import spray.revolver.RevolverPlugin._


object RavenBuild extends Build {

  val scalaV = "2.11.7"
  val akkaStreamV = "2.0.2"
  val akkaV = "2.4.1"
  val playTwirlV = "1.1.1"

  val commonSettings = Seq(
    organization := "com.opentok",
    scalaVersion := scalaV,
    scalacOptions := Seq(
      "-unchecked",
      "-Xlog-free-terms",
      "-deprecation",
      "-encoding", "UTF-8",
      "-target:jvm-1.8"
    )
  )

  lazy val twirlSettings = Seq(
    sourceDirectories in(Compile, compileTemplates) := Seq((resourceDirectory in Compile).value / "templates")
  )

  lazy val core: Project = Project("raven", file("."))
    .settings(commonSettings: _*)
    .settings(twirlSettings: _*)
    .settings(Revolver.settings: _*)
    .settings(
      assemblyMergeStrategy in assembly := {
        case PathList("application.conf") => MergeStrategy.discard
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      assemblyJarName in assembly := "raven-assembly.jar",
      test in assembly := {},
      libraryDependencies ++= {
        Seq(
          "org.scala-lang"                     % "scala-compiler"                         % scalaV,
          "com.typesafe.play"                 %%  "twirl-api"                             % playTwirlV,
          "com.typesafe.akka"                 %% "akka-actor"                             % akkaV,
          "com.typesafe.akka"                 %% "akka-slf4j"                             % akkaV,
          "com.typesafe.akka"                 %% "akka-stream-experimental"               % akkaStreamV,
          "com.typesafe.akka"                 %% "akka-http-spray-json-experimental"      % akkaStreamV,
          "com.typesafe.akka"                 %% "akka-http-experimental"                 % akkaStreamV,
          "com.typesafe.akka"                 %% "akka-http-core-experimental"            % akkaStreamV,
          "com.typesafe.akka"                 %% "akka-http-testkit-experimental"         % akkaStreamV,
          "ch.qos.logback"                     % "logback-classic"                        % "1.0.13",
          "com.typesafe.slick"                %% "slick"                                  % "3.0.2",
          "com.sendgrid"                       % "sendgrid-java"                          % "2.2.1",
          "mysql"                              % "mysql-connector-java"                   % "5.1.6",
          "joda-time"                          % "joda-time"                              % "2.5",
          "org.joda"                           % "joda-convert"                           % "1.7",
          "com.zaxxer"                         % "HikariCP"                               % "2.3.9",
          "com.h2database"                     % "h2"                                     % "1.3.175",
          "org.scalatest"                     %% "scalatest"                              % "2.2.5" % "test",
          "com.typesafe.akka"                 %% "akka-testkit"                           % akkaV % "test"
        )
      }
    ).enablePlugins(SbtTwirl)

  //lazy val root: Project = Project("root", file(".")).aggregate(core)
}

import play.twirl.sbt.SbtTwirl
import com.aol.sbt.sonar.SonarRunnerPlugin
import com.aol.sbt.sonar.SonarRunnerPlugin.autoImport._
import sbt._
import Keys._
import play.twirl.sbt.Import.TwirlKeys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{PathList, MergeStrategy}
import scoverage.ScoverageSbtPlugin.autoImport._
import spray.revolver.RevolverPlugin._


object Build extends sbt.Build {

  val scalaV = "2.11.8"
  val akkaV = "2.4.6"
  val playTwirlV = "1.1.1"

  val commonSettings = Seq(
    organization := "com.opentok",
    scalaVersion := scalaV,
    resolvers += Resolver.bintrayRepo("ernestrc", "maven"),
    scalacOptions := Seq(
      "-unchecked",
      "-Xlog-free-terms",
      "-deprecation",
      "-feature",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-unused",
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
      coverageExcludedPackages := "html",
      coverageEnabled.in(Test, test) := true,
      coverageEnabled in assembly := false,
      assemblyMergeStrategy in assembly := {
        case PathList("application.conf") => MergeStrategy.discard
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      sonarProperties ++= Map(
        "sonar.host.url" → "http://fistbump.tokbox.com:9000/",
        "sonar.jdbc.url" → "jdbc:mysql://fistbump.tokbox.com/sonar",
        "sonar.jdbc.driverClassName" → "com.mysql.jdbc.Driver",
        "sonar.scoverage.reportPath" → "target/scala-2.11/scoverage-report/scoverage.xml",
        "sonar.jdbc.username" → "sonar",
        "sonar.jdbc.password" → "sonar"
      ),
      assemblyJarName in assembly := "raven-assembly.jar",
      test in assembly := {},
      libraryDependencies ++= {
        Seq(
          "build.unstable" %% "tylog" % "0.3.0",
          "org.scala-lang" % "scala-compiler" % scalaV,
          "com.typesafe.play" %% "twirl-api" % playTwirlV,
          "com.typesafe.akka" %% "akka-actor" % akkaV,
          "com.typesafe.akka" %% "akka-slf4j" % akkaV,
          "com.typesafe.akka" %% "akka-stream" % akkaV,
          "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
          "ch.qos.logback" % "logback-classic" % "1.1.7",
          "net.logstash.logback" % "logstash-logback-encoder" % "4.7",
          "com.typesafe.slick" %% "slick" % "3.0.2",
          "com.sendgrid" % "sendgrid-java" % "2.2.1",
          "mysql" % "mysql-connector-java" % "5.1.6",
          "joda-time" % "joda-time" % "2.5",
          "org.joda" % "joda-convert" % "1.7",
          "com.zaxxer" % "HikariCP" % "2.3.9",
          "com.h2database" % "h2" % "1.3.175",
          "org.scalatest" %% "scalatest" % "2.2.5" % "test",
          "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
          "com.typesafe.akka" %% "akka-http-testkit" % akkaV % "test"
        )
      }
    ).enablePlugins(SbtTwirl, SonarRunnerPlugin)

  //lazy val root: Project = Project("root", file(".")).aggregate(core)
}

import sbt._
import Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.pgp.PgpKeys._

object ColossusBuild extends Build {

  val AKKA_VERSION            = "2.3.6"
  val SCALATEST_VERSION       = "2.2.0"

  val GeneralSettings = Seq[Setting[_]](


    compile <<= (compile in Compile) dependsOn (compile in Test),
    
    organization := "com.tumblr",
    scalaVersion  := "2.10.2",
    crossScalaVersions := Seq("2.10.4", "2.11.2"),
    version                   := "0.5.1-RC2",
    parallelExecution in Test := false,
    scalacOptions             ++= Seq("-feature", "-language:implicitConversions", "-language:postfixOps", "-unchecked", "-deprecation"),
    resolvers ++= Seq(
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
    ),

    libraryDependencies ++= Seq (
      "com.typesafe.akka" %% "akka-actor"   % AKKA_VERSION,
      "com.typesafe.akka" %% "akka-agent"   % AKKA_VERSION,
      "com.typesafe.akka" %% "akka-testkit" % AKKA_VERSION,
      "org.scalatest"     %% "scalatest" % SCALATEST_VERSION % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test",
      "com.github.nscala-time" %% "nscala-time" % "1.2.0",
      "commons-io" % "commons-io" % "2.4",
      "commons-codec" % "commons-codec" % "1.10",
      "com.typesafe.play" %% "play-json" % "2.2.0"

    )
  )

  val ColossusSettings = GeneralSettings ++ Publish.settings
  
  val noPubSettings = GeneralSettings ++ Seq(
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))) 
    )

  val testkitDependencies = libraryDependencies ++= Seq(
    "org.scalatest"     %% "scalatest" % SCALATEST_VERSION
  )

  val MetricSettings = ColossusSettings ++ Seq(
    libraryDependencies ++= Seq(
      "net.liftweb"       %% "lift-json"     % "2.6-RC1")
  )

  lazy val RootProject = Project(id="root", base=file("."))
      .settings(noPubSettings:_*)
      .dependsOn(ColossusProject)
      .aggregate(ColossusProject, ColossusTestkitProject, ColossusMetricsProject)

  lazy val ColossusProject: Project = Project(id="colossus", base=file("colossus"))
      .settings(ColossusSettings:_*)
      .aggregate(ColossusTestsProject)
      .dependsOn(ColossusMetricsProject)

  lazy val ColossusExamplesProject = Project(id="colossus-examples", base=file("colossus-examples"))
      .settings(noPubSettings:_*)
      .settings(Revolver.settings:_*)
      .dependsOn(ColossusProject)

  lazy val ColossusMetricsProject = Project(id="colossus-metrics", base=file("colossus-metrics"))
      .settings(MetricSettings:_*)
      .settings(Revolver.settings:_*)

  lazy val ColossusTestkitProject = Project(id="colossus-testkit", base = file("colossus-testkit"))
      .settings(ColossusSettings:_*)
      .settings(testkitDependencies)
      .dependsOn(ColossusProject)

  lazy val ColossusTestsProject = Project(
    id="colossus-tests", 
    base = file("colossus-tests"),
    dependencies = Seq(ColossusTestkitProject % "compile;test->test")
  ).settings(noPubSettings:_*)



}

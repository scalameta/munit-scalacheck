import com.typesafe.tools.mima.core._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import scala.collection.mutable

def previousVersion = "0.7.0"

def scala213 = "2.13.16"

def scala212 = "2.12.20"

def scala3 = "3.3.6"

def junitVersion = "4.13.2"

def munitVersion = "1.2.0"

inThisBuild(
  List(
    version ~= { old =>
      if ("true" == System.getProperty("CI") && old.contains("+0-")) {
        old.replaceAll("\\+0-.*", "")
      } else {
        old
      }
    },
    organization := "org.scalameta",
    homepage := Some(url("https://github.com/scalameta/munit")),
    licenses := List(
      "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
    ),
    developers := List(
      Developer(
        "olafurpg",
        "Ólafur Páll Geirsson",
        "olafurpg@gmail.com",
        url("https://geirsson.com")
      )
    ),
    scalaVersion := scala213,
    // TODO: remove when munit/scalacheck align on `test-interface` package
    // version; it comes from `org.scala-native:sbt-scala-native` plugin
    evictionErrorLevel := sbt.util.Level.Warn,
    useSuperShell := false
  )
)

publish / skip := true
mimaPreviousArtifacts := Set.empty
crossScalaVersions := List()
addCommandAlias(
  "scalafixAll",
  s"; ++$scala212 ; scalafixEnable ; all scalafix test:scalafix"
)
addCommandAlias(
  "scalafixCheckAll",
  s"; ++$scala212 ;  scalafixEnable ; scalafix --check ; test:scalafix --check"
)
val isPreScala213 = Set[Option[(Long, Long)]](Some((2, 11)), Some((2, 12)))
val scala2Versions = List(scala213, scala212)

val scala3Versions = List(scala3)
val allScalaVersions = scala2Versions ++ scala3Versions

def isScala2(v: Option[(Long, Long)]): Boolean = v.exists(_._1 == 2)
val isScala3Setting = Def.setting {
  isScala3(CrossVersion.partialVersion(scalaVersion.value))
}

def isScala3(v: Option[(Long, Long)]): Boolean = v.exists(_._1 == 3)

// NOTE(olafur): disable Scala.js and Native settings for IntelliJ.
lazy val skipIdeaSettings =
  SettingKey[Boolean]("ide-skip-project").withRank(KeyRanks.Invisible) := true
lazy val mimaEnable: List[Def.Setting[_]] = List(
  mimaBinaryIssueFilters ++= List(
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.ScalaCheckSuite.unitToProp"
    )
  ),
  mimaPreviousArtifacts := {
    if (crossPaths.value)
      Set("org.scalameta" %% moduleName.value % previousVersion)
    else Set("org.scalameta" % moduleName.value % previousVersion)
  }
)

val sharedJVMSettings: List[Def.Setting[_]] = List(
  crossScalaVersions := allScalaVersions
) ++ mimaEnable

val sharedJSSettings: List[Def.Setting[_]] = List(
  skipIdeaSettings,
  crossScalaVersions := allScalaVersions.filterNot(_.startsWith("0."))
)
val sharedJSConfigure: Project => Project =
  _.disablePlugins(MimaPlugin)

val sharedNativeSettings: List[Def.Setting[_]] = List(
  skipIdeaSettings,
  crossScalaVersions := allScalaVersions
)
val sharedNativeConfigure: Project => Project =
  _.disablePlugins(ScalafixPlugin, MimaPlugin)

val sharedSettings = List(
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((major, _)) if major != 2 =>
        List(
          "-language:implicitConversions"
        )
      case _ =>
        List(
          "-target:jvm-1.8",
          "-Yrangepos",
          // -Xlint is unusable because of
          // https://github.com/scala/bug/issues/10448
          "-Ywarn-unused:imports"
        )
    }
  }
)

lazy val munitScalacheck = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("munit-scalacheck"))
  .settings(
    moduleName := "munit-scalacheck",
    sharedSettings,
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.19.0",
      "org.scalameta" %%% "munit-diff" % munitVersion,
      "org.scalameta" %%% "munit" % munitVersion
    )
  )
  .jvmSettings(
    sharedJVMSettings
  )
  .nativeConfigure(sharedNativeConfigure)
  .nativeSettings(
    sharedNativeSettings
  )
  .jsConfigure(sharedJSConfigure)
  .jsSettings(sharedJSSettings)

lazy val munitScalacheckJVM = munitScalacheck.jvm
lazy val munitScalacheckJS = munitScalacheck.js
lazy val munitScalacheckNative = munitScalacheck.native

lazy val tests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .dependsOn(munitScalacheck)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sharedSettings,
    buildInfoPackage := "munit",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceDirectory" ->
        ((ThisBuild / baseDirectory).value / "tests" / "shared" / "src" / "main").getAbsolutePath.toString,
      scalaVersion
    ),
    Test / unmanagedSourceDirectories ++=
      crossBuildingDirectories("tests", "test").value,
    publish / skip := true
  )
  .nativeConfigure(sharedNativeConfigure)
  .nativeSettings(sharedNativeSettings)
  .jsConfigure(sharedJSConfigure)
  .jsSettings(
    sharedJSSettings,
    jsEnv := {
      val log = sLog.value
      if (Option(System.getenv("GITHUB_JOB")).contains("jsdom")) {
        log.info("Testing in JSDOMNodeJSEnv")
        new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
      } else {
        log.info("Testing in NodeJSEnv")
        new org.scalajs.jsenv.nodejs.NodeJSEnv
      }
    }
  )
  .jvmSettings(
    sharedJVMSettings,
    fork := true,
    Test / parallelExecution := true,
    Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "+b")
  )
  .disablePlugins(MimaPlugin)

lazy val testsJVM = tests.jvm
lazy val testsJS = tests.js
lazy val testsNative = tests.native

Global / excludeLintKeys ++= Set(
  mimaPreviousArtifacts
)
def crossBuildingDirectories(name: String, config: String) =
  Def.setting[Seq[File]] {
    val root = (ThisBuild / baseDirectory).value / name
    val base = root / "shared" / "src" / config
    val result = mutable.ListBuffer.empty[File]
    val partialVersion = CrossVersion.partialVersion(scalaVersion.value)
    if (isPreScala213(partialVersion)) {
      result += base / "scala-pre-2.13"
    }
    if (isScala2(partialVersion)) {
      result += base / "scala-2"
    }
    result.toList
  }

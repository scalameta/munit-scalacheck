import com.typesafe.tools.mima.core._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import scala.collection.mutable

def scala213 = "2.13.18"

def scala212 = "2.12.21"

def scala3 = "3.3.8"

def junitVersion = "4.13.2"

def munitVersion = "1.3.5"

inThisBuild(
  List(
    // version is set dynamically by sbt-dynver, but let's adjust it
    version := {
      val curVersion = version.value
      def dynVer(out: sbtdynver.GitDescribeOutput): String = {
        def tagVersion = out.ref.dropPrefix
        if (out.isCleanAfterTag) tagVersion
        else if (System.getenv("CI") == null)
          s"$tagVersion-next-SNAPSHOT" // modified for local builds
        else if (out.commitSuffix.distance == 0) tagVersion
        else if (sys.props.contains("backport.release")) tagVersion
        else curVersion
      }
      dynverGitDescribeOutput.value.mkVersion(dynVer, curVersion)
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
def onOtherPlatform(except: AutoPlugin*): Project => Project =
  _.disablePlugins(MimaPlugin +: except: _*).settings(
    SettingKey[Boolean]("ide-skip-project")
      .withRank(KeyRanks.Invisible) := true
  )
val onJS: Project => Project = onOtherPlatform()
val onNative: Project => Project = onOtherPlatform(ScalafixPlugin)

def mimaEnable = Def.settings(
  mimaBinaryIssueFilters ++= List(
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "munit.ScalaCheckSuite.unitToProp"
    )
  ),
  // the last tag, so the baseline cannot go stale; CI has to fetch tags for it
  mimaPreviousArtifacts := previousStableVersion.value.map { v =>
    if (crossPaths.value) "org.scalameta" %% moduleName.value % v
    else "org.scalameta" % moduleName.value % v
  }.toSet
)

val sharedSettings = Def.settings(
  crossScalaVersions := allScalaVersions,
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
  .jvmSettings(mimaEnable)
  .nativeConfigure(onNative)
  .jsConfigure(onJS)

lazy val munitScalacheckJVM = munitScalacheck.jvm
lazy val munitScalacheckJS = munitScalacheck.js
lazy val munitScalacheckNative = munitScalacheck.native

def testsJVMSettings = Def.settings(
  fork := true,
  Test / parallelExecution := true,
  Test / testOptions += Tests.Argument(TestFrameworks.MUnit, "+b")
)

def jsEnvForJob(log: Logger) =
  if (System.getenv("GITHUB_JOB") == "jsdom") {
    log.info("Testing in JSDOMNodeJSEnv")
    new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv
  } else {
    log.info("Testing in NodeJSEnv")
    new org.scalajs.jsenv.nodejs.NodeJSEnv
  }

lazy val tests = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .dependsOn(munitScalacheck)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sharedSettings,
    buildInfoPackage := "munit",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceDirectory" ->
        ((ThisBuild / baseDirectory).value / "tests" / "shared" / "src" / "main").getAbsolutePath,
      scalaVersion
    ),
    Test / unmanagedSourceDirectories ++=
      crossBuildingDirectories("tests", "test").value,
    publish / skip := true
  )
  .nativeConfigure(onNative)
  .jsConfigure(onJS)
  .jsSettings(jsEnv := jsEnvForJob(sLog.value))
  .jvmSettings(testsJVMSettings)
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

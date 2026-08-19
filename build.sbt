import com.typesafe.tools.mima.core._
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

LocalRootProject / publish / skip := true
LocalRootProject / mimaPreviousArtifacts := Set.empty
LocalRootProject / crossScalaVersions := List()

// scalafix runs on one Scala version, and Native opts out of the plugin
def scalafixOn(arg: String) = {
  val scalafixTargets =
    Seq(munitScalacheck, tests).flatMap(m => Seq(m.jvm, m.js).map(_(scala212)))
  val args = Seq(s"scalafix $arg", s"Test/scalafix $arg")
  onEach(scalafixTargets, "scalafixEnable")(args: _*)
}

def onEach(ps: Seq[Project], preTasks: String*)(postTasks: String*) =
  (preTasks ++ postTasks.flatMap { t => ps.map(p => s"${p.id}/$t") })
    .mkString("; ", "; ", "")

addCommandAlias("scalafixAll", scalafixOn(""))
addCommandAlias("scalafixCheckAll", scalafixOn("--check"))
addCommandAlias("testJVM", onEach(tests.jvm.get)("test"))
addCommandAlias("testJS", onEach(tests.js.get)("test"))
addCommandAlias("testNative", onEach(tests.native.get)("test"))

val scala2Versions = List(scala213, scala212)

val scala3Versions = List(scala3)
val allScalaVersions = scala2Versions ++ scala3Versions

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
  scalacOptions += "-release:8", // built on a newer JDK than it targets
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((major, _)) if major != 2 =>
        List(
          "-language:implicitConversions"
        )
      case _ =>
        List(
          "-Yrangepos",
          // -Xlint is unusable because of
          // https://github.com/scala/bug/issues/10448
          "-Ywarn-unused:imports"
        )
    }
  }
)

val munitScalacheckName = "munit-scalacheck"

lazy val munitScalacheck = projectMatrix
  .in(file(munitScalacheckName))
  .settings(
    moduleName := munitScalacheckName,
    sharedSettings,
    unmanagedMainSources(munitScalacheckName, "shared"),
    libraryDependencies ++= Seq(
      "org.scalacheck" %%% "scalacheck" % "1.19.0",
      "org.scalameta" %%% "munit-diff" % munitVersion,
      "org.scalameta" %%% "munit" % munitVersion
    )
  )
  .jvmPlatform(allScalaVersions, mimaEnable)
  .jsPlatform(allScalaVersions, Nil, onJS)
  .nativePlatform(allScalaVersions, Nil, onNative)

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

val testsOnJS: Project => Project =
  onJS.andThen(_.settings(jsEnv := jsEnvForJob(sLog.value)))

lazy val tests = projectMatrix
  .dependsOn(munitScalacheck)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    sharedSettings,
    unmanagedSources("tests", "shared"),
    buildInfoPackage := "munit",
    buildInfoKeys := Seq[BuildInfoKey](
      "sourceDirectory" ->
        ((ThisBuild / baseDirectory).value / "tests" / "shared" / "src" / "main").getAbsolutePath,
      scalaVersion
    ),
    publish / skip := true
  )
  .jvmPlatform(allScalaVersions, testsJVMSettings)
  .jsPlatform(allScalaVersions, Nil, testsOnJS)
  .nativePlatform(allScalaVersions, Nil, onNative)
  .disablePlugins(MimaPlugin)

Global / excludeLintKeys ++= Set(
  mimaPreviousArtifacts
)

// crossProject's layout, wired by hand: a matrix has one base directory, so
// each cell names the trees it shares. Absent directories are harmless.
def roots(name: String, cfg: String, dirs: String*) = Def.setting[Seq[File]] {
  val variants = new mutable.ListBuffer[String]()
  variants += "scala"
  variants += "java"
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) =>
      variants += "scala-2"
      if (minor < 13) variants += "scala-pre-2.13"
    case Some((3, _)) => variants += "scala-3"
    case _            =>
  }
  val root = (ThisBuild / baseDirectory).value / name
  for (dir <- dirs; base = root / dir / "src" / cfg; variant <- variants)
    yield base / variant
}

def unmanagedMainSources(name: String, dirs: String*) = Def.settings(
  Compile / unmanagedSourceDirectories ++= roots(name, "main", dirs: _*).value
)

def unmanagedTestSources(name: String, dirs: String*) = Def.settings(
  Test / unmanagedSourceDirectories ++= roots(name, "test", dirs: _*).value
)

def unmanagedSources(name: String, dirs: String*) = Def.settings(
  unmanagedMainSources(name, dirs: _*),
  unmanagedTestSources(name, dirs: _*)
)

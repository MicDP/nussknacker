import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import net.virtualvoid.sbt.graph.Plugin._
import sbt._
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy

val scalaV = "2.11.8"

//by default we include flink and scala, we want to be able to disable this behaviour for performance reasons
val includeFlinkAndScala = Option(System.getProperty("includeFlinkAndScala", "true")).exists(_.toBoolean)

val flinkScope = if (includeFlinkAndScala) "compile" else "provided"
val nexusHost = System.getProperty("nexusHost", "oss.sonatype.org")

// `publishArtifact := false` should be enough to keep sbt from publishing root module,
// unfortunately it does not work, so we resort to hack by publishing root module to Resolver.defaultLocal
//publishArtifact := false
publishTo := Some(Resolver.defaultLocal)

val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = s"https://$nexusHost/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomExtra in Global := {
    <scm>
      <connection>scm:git:github.com/touk/nussknacker.git</connection>
      <developerConnection>scm:git:git@github.com:touk/nussknacker.git</developerConnection>
      <url>github.com/touk/nussknacker</url>
    </scm>
      <developers>
        <developer>
          <id>TouK</id>
          <name>TouK</name>
          <url>https://touk.pl</url>
        </developer>
      </developers>
  },
  organization := "pl.touk.nussknacker",
  homepage := Some(url(s"https://github.com/touk/nussknacker")),
  credentials := Seq(Credentials("Sonatype Nexus Repository Manager",
    nexusHost, System.getProperty("nexusUser", "touk"), System.getProperty("nexusPassword"))
  )
)

def numberUtilsStrategy: String => MergeStrategy = {
  case PathList(ps@_*) if ps.last == "NumberUtils.class" => MergeStrategy.first
  case PathList("org", "w3c", "dom", "events", xs @ _*) => MergeStrategy.first
  case PathList("akka", xs @ _*) => MergeStrategy.last
  case x => MergeStrategy.defaultMergeStrategy(x)
}
val scalaTestReports = Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/surefire-reports", "-oFGD")
val commonSettings =
  graphSettings ++
  publishSettings ++
  Seq(
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    scalaVersion  := scalaV,
    resolvers ++= Seq(
      "spring milestone" at "https://repo.spring.io/milestone"
    ),
    testOptions in Test += scalaTestReports,
    testOptions in IntegrationTest += scalaTestReports,
    scalacOptions := Seq(
      "-unchecked",
      "-deprecation",
      "-encoding", "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:postfixOps",
      "-language:existentials",
      "-target:jvm-1.8"
    ),
    javacOptions := Seq(
      "-Xlint:deprecation",
      "-Xlint:unchecked"
    ),
    assemblyMergeStrategy in assembly := numberUtilsStrategy
  )

val akkaV = "2.4.20" //same version as in Flink
val flinkV = "1.4.2"
val kafkaMajorV = "0.11"
val kafkaV = s"$kafkaMajorV.0.2"
val springV = "5.0.0.M5"
val scalaTestV = "3.0.3"
val logbackV = "1.1.3"
val log4jV = "1.7.21"
val argonautShapelessV = "1.2.0-M1"
val argonautMajorV = "6.2"
val argonautV = s"$argonautMajorV-M3"
val catsV = "0.9.0"
val monocleV = "1.2.2"
val scalaParsersV = "1.0.4"
val dispatchV = "0.11.3"
val slf4jV = "1.7.21"
val scalaLoggingV = "3.4.0"
val scalaCompatV = "0.8.0"
val ficusV = "1.4.1"
val configV = "1.3.0"
val commonsLangV = "3.3.2"
val dropWizardV = "3.1.5"

val akkaHttpV = "10.0.10"
val slickV = "3.2.0-M1" // wsparcie dla select for update jest od 3.2.0
val hsqldbV = "2.3.4"
val flywayV = "4.0.3"

def engine(name: String) = file(s"engine/$name")

lazy val engineStandalone = (project in engine("standalone/engine")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-standalone-engine",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      (assembly in Compile) in standalone_sample
    ),
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
        "ch.qos.logback" % "logback-classic" % logbackV % "it,test"
      )
    }
  ).
  dependsOn(interpreter, standaloneUtil, argonautUtils, httpUtils)

lazy val standaloneApp = (project in engine("standalone/app")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-app",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = true, level = Level.Debug),
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    },
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),
        "com.typesafe.akka" %% "akka-slf4j" % akkaV,
        "ch.qos.logback" % "logback-classic" % logbackV
      )
    }
  ).
  settings(addArtifact(artifact in (Compile, assembly), assembly)).
  dependsOn(engineStandalone)


lazy val management = (project in engine("flink/management")).
  configs(IntegrationTest).
  settings(commonSettings).
  settings(Defaults.itSettings).
  settings(
    name := "nussknacker-management",
    Keys.test in IntegrationTest <<= (Keys.test in IntegrationTest).dependsOn(
      (assembly in Compile) in managementSample,
      (assembly in Compile) in management_java_sample
    ),
    //flink cannot run tests and deployment concurrently
    parallelExecution in IntegrationTest := false,
    libraryDependencies ++= {
      Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "org.apache.flink" %% "flink-clients" % flinkV % "provided",
        "org.apache.flink" % "flink-shaded-curator" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",

        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

        "org.scalatest" %% "scalatest" % scalaTestV % "it,test",
        "com.whisk" %% "docker-testkit-scalatest" % "0.9.0" % "it,test",
        "com.whisk" %% "docker-testkit-impl-spotify" % "0.9.0" % "it,test",
        "ch.qos.logback" % "logback-classic" % logbackV % "it,test",
        "org.slf4j" % "log4j-over-slf4j" % log4jV % "it,test",
        "ch.qos.logback" % "logback-core" % logbackV % "it,test"
      )
    }
  ).dependsOn(interpreter, queryableState, kafkaTestUtil % "it,test",securityApi)

lazy val standalone_sample = (project in engine("standalone/engine/sample")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-sample",
    assemblyJarName in assembly := "standaloneSample.jar"
  ).dependsOn(util, standaloneApi, standaloneUtil)

val managementSampleName = "nussknacker-management-sample"

lazy val managementSample = (project in engine("flink/management/sample")).
  settings(commonSettings).
  settings(
    name := managementSampleName,
    assemblyJarName in assembly := "managementSample.jar",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Debug),
    test in assembly := {},
    publishArtifact in (Compile, packageDoc) := false, //scaladoc fails so we disable it, we don't need it here anyway
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-queryable-state-runtime" % flinkV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(flinkUtil, kafka, kafkaFlinkUtil, process % "runtime,test", flinkTestUtil % "test", kafkaTestUtil % "test",
    securityApi)

lazy val management_java_sample = (project in engine("flink/management/java_sample")).
  settings(commonSettings).
  settings(
    name := managementSampleName,
    assemblyJarName in assembly := "managementJavaSample.jar",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false, level = Level.Debug),
    test in assembly := {},
    libraryDependencies ++= {
      Seq(
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"
      )
    }
  ).dependsOn(flinkUtil, process % "runtime")


lazy val example = (project in engine("example")).
  settings(commonSettings).
  settings(
    name := "nussknacker-example",
    fork := true, // without this there are some classloading issues
    libraryDependencies ++= {
      Seq(
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "ch.qos.logback" % "logback-classic" % logbackV % "test"
      )
    },
    test in assembly := {},
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    }
  )
  .settings(addArtifact(artifact in (Compile, assembly), assembly))
  .dependsOn(process, kafkaFlinkUtil, kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val process = (project in engine("flink/process")).
  settings(commonSettings).
  settings(
    name := "nussknacker-process",
    fork := true, // without this there are some classloading issues
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-runtime" % flinkV % "provided",
        "org.apache.flink" %% "flink-statebackend-rocksdb" % flinkV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(flinkApi, flinkUtil, interpreter, kafka % "test", kafkaTestUtil % "test", kafkaFlinkUtil % "test", flinkTestUtil % "test")

lazy val interpreter = (project in engine("interpreter")).
  settings(commonSettings).
  settings(
    name := "nussknacker-interpreter",
    libraryDependencies ++= {
      Seq(
        "org.springframework" % "spring-expression" % springV,
        "org.hsqldb" % "hsqldb" % hsqldbV,
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "ch.qos.logback" % "logback-classic" % logbackV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version),
    buildInfoKeys ++= Seq[BuildInfoKey] (
      "buildTime" -> java.time.LocalDateTime.now().toString,
      "gitCommit" -> git.gitHeadCommit.value.getOrElse("")
    ),
    buildInfoPackage := "pl.touk.nussknacker.engine.version",
    buildInfoOptions ++= Seq(BuildInfoOption.ToMap)
  ).
  dependsOn(util)

lazy val kafka = (project in engine("kafka")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" % "kafka-clients" % kafkaV
      )
    }
  ).
  dependsOn(util)

lazy val kafkaFlinkUtil = (project in engine("flink/kafka-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% s"flink-connector-kafka-$kafkaMajorV" % flinkV,
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).
  dependsOn(flinkApi, kafka, flinkUtil, kafkaTestUtil % "test", flinkTestUtil % "test")

lazy val kafkaTestUtil = (project in engine("kafka-test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-kafka-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.kafka" %% "kafka" % kafkaV  excludeAll (
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")
        ),
        "ch.qos.logback" % "logback-classic" % logbackV,
        "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
        "org.slf4j" % "log4j-over-slf4j" % "1.7.21",
        "org.scalatest" %% "scalatest" % scalaTestV
      )
    }
  )

lazy val util = (project in engine("util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-util",
    libraryDependencies ++= {
      Seq(
        "com.iheart" %% "ficus" % ficusV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(api)



lazy val flinkUtil = (project in engine("flink/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV
      )
    }
  ).dependsOn(util, flinkApi)

lazy val flinkTestUtil = (project in engine("flink/test-util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-test-util",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.apache.flink" %% "flink-test-utils" % flinkV,
        "org.apache.flink" % "flink-metrics-dropwizard" % flinkV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "ch.qos.logback" % "logback-classic" % logbackV,
        "org.scalatest" %% "scalatest" % scalaTestV
      )
    }
  ).dependsOn(queryableState)

lazy val standaloneUtil = (project in engine("standalone/util")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-util",
    libraryDependencies ++= {
      Seq(
        "io.dropwizard.metrics" % "metrics-core" % dropWizardV,
        "io.dropwizard.metrics" % "metrics-graphite" % dropWizardV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(util, standaloneApi)


lazy val standaloneApi = (project in engine("standalone/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-standalone-api"
  ).dependsOn(api)



lazy val api = (project in engine("api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-api",
    libraryDependencies ++= {
      Seq(
        //TODO: czy faktycznie tak chcemy??
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "org.apache.commons" % "commons-lang3" % commonsLangV,
        "org.typelevel" %% "cats-core" % catsV,
        "org.typelevel" %% "cats-effect" % "0.3",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV

      )
    }
  )

lazy val securityApi = (project in engine("security-api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-security-api",
    libraryDependencies ++= {
      Seq(
        "org.scalatest" %% "scalatest" % scalaTestV % "test",
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),
        "com.typesafe" % "config" % configV
      )
    }
  )
  .dependsOn(util)

lazy val flinkApi = (project in engine("flink/api")).
  settings(commonSettings).
  settings(
    name := "nussknacker-flink-api",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-java" % flinkV % "provided",
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided"

      )
    }
  ).dependsOn(api)

lazy val processReports = (project in engine("processReports")).
  settings(commonSettings).
  settings(
    name := "nussknacker-process-reports",
    libraryDependencies ++= {
      Seq(
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(httpUtils)

lazy val httpUtils = (project in engine("httpUtils")).
  settings(commonSettings).
  settings(
    name := "nussknacker-http-utils",
    libraryDependencies ++= {
      Seq(
        "net.databinder.dispatch" %% "dispatch-core" % dispatchV,// % "optional",
        "org.scala-lang.modules" %% "scala-parser-combinators" % scalaParsersV, // scalaxb deps
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,

        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  ).dependsOn(api)

lazy val argonautUtils = (project in engine("argonautUtils")).
  settings(commonSettings).
  settings(
    name := "nussknacker-argonaut-utils",
    libraryDependencies ++= {
      Seq(
        "io.argonaut" %% "argonaut" % argonautV,
        "com.github.alexarchambault" %% s"argonaut-shapeless_$argonautMajorV" % argonautShapelessV,
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force()
      )
    }
  )

//osobny modul bo chcemy uzyc klienta do testowania w managementSample
lazy val queryableState = (project in engine("queryableState")).
  settings(commonSettings).
  settings(
    name := "nussknacker-queryable-state",
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % "provided",
        "org.scala-lang.modules" %% "scala-java8-compat" % scalaCompatV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV,
        "com.typesafe" % "config" % configV
      )
    }
  ).dependsOn(api)



lazy val buildUi = taskKey[Unit]("builds ui")
lazy val testUi = taskKey[Unit]("tests ui")

def runNpm(command: String, errorMessage: String) = {
  val path = Path.apply("ui/client").asFile
  println("Using path: " + path.getAbsolutePath)
  val installResult = Process("npm install", path) !;
  if (installResult != 0) throw new RuntimeException("NPM install failed")
  val result = Process(s"npm $command", path) !;
  if (result != 0) throw new RuntimeException(errorMessage)
}

lazy val ui = (project in file("ui/server"))
  .settings(commonSettings)
  .settings(
    name := "nussknacker-ui",
    buildUi := {
      runNpm("run build", "Client build failed")
    },
    testUi := {
      runNpm("test", "Client tests failed")
    },
    parallelExecution in ThisBuild := false,
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = includeFlinkAndScala, level = Level.Debug),
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    },
    test in assembly := {},
    Keys.test in Test <<= (Keys.test in Test).dependsOn(
      //TODO: maybe here there should be engine/demo??
      (assembly in Compile) in managementSample
    ).dependsOn(
      testUi
    ),
    assemblyJarName in assembly := "nussknacker-ui-assembly.jar",
    assembly in ThisScope <<= (assembly in ThisScope).dependsOn(
      buildUi
    ),
    libraryDependencies ++= {
      Seq(
        "org.apache.flink" %% "flink-streaming-scala" % flinkV % flinkScope
        excludeAll(
            ExclusionRule("com.google.code.findbugs", "jsr305"),
            ExclusionRule("log4j", "log4j"),
            ExclusionRule("org.slf4j", "slf4j-log4j12")
          ),
        "org.apache.flink" %% "flink-clients" % flinkV % flinkScope
        //tutaj mamy dwie wersje jsr305 we flinku i assembly sie pluje...
        excludeAll(
          ExclusionRule("com.google.code.findbugs", "jsr305"),
          ExclusionRule("log4j", "log4j"),
          ExclusionRule("org.slf4j", "slf4j-log4j12")

        ),
        "com.typesafe.akka" %% "akka-http" % akkaHttpV force(),
        "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test" force(),

        "ch.qos.logback" % "logback-core" % logbackV,
        "ch.qos.logback" % "logback-classic" % logbackV,
        "org.slf4j" % "log4j-over-slf4j" % "1.7.21",

        "com.typesafe.slick" %% "slick" % slickV,
        "com.typesafe.slick" %% "slick-hikaricp" % slickV,
        "org.hsqldb" % "hsqldb" % hsqldbV,
        "org.flywaydb" % "flyway-core" % flywayV,
        "org.apache.xmlgraphics" % "fop" % "2.1",

        "com.typesafe.slick" %% "slick-testkit" % slickV % "test",
        "org.scalatest" %% "scalatest" % scalaTestV % "test"
      )
    }
  )
  .settings(addArtifact(artifact in (Compile, assembly), assembly))
  .dependsOn(management, interpreter, engineStandalone, processReports, securityApi)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  ReleaseStep(action = Command.process("publishSigned", _)),
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)

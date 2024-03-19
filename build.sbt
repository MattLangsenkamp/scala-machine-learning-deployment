import org.scalajs.linker.interface.ModuleSplitStyle

// 1 create dependency variables
val scala3Version        = "3.3.1"
val fs2Version           = "3.9.3"
val openCVVersion        = "1.5.9"
val circeVersion         = "0.15.0-M1"
val cirisVersion         = "3.5.0"
val osLibVersion         = "0.9.2"
val http4sVersion        = "0.23.24"
val http4sDomVersion     = "0.2.11"
val http4sJwtVersion     = "1.2.1"
val gatlingVersion       = "3.10.3"
val otel4sVersion        = "0.4.0"
val openTelemetryVersion = "1.34.0"

ThisBuild / scalaVersion := scala3Version

// 2 create protobuf module
lazy val protobuf = project
  .in(file("protobuf"))
  .enablePlugins(Fs2Grpc) // explicitly depend on gRPC plugin

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeVersion)
  )

lazy val client = project
  .in(file("client"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.FewestModules)
        .withSourceMap(false)
    },
    libraryDependencies ++= Seq(
      "io.indigoengine" %%% "tyrian-io"     % "0.8.0",
      "org.http4s"      %%% "http4s-dom"    % http4sDomVersion,
      "org.http4s"      %%% "http4s-client" % "0.23.24",
      "org.http4s"      %%% "http4s-circe"  % "0.23.24",
      "org.scalameta"   %%% "munit"         % "0.7.29" % Test
    ),
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeVersion)
  )
  .dependsOn(core.js)

lazy val server = project
  .in(file("server"))
  .settings(
    name    := "scalamachinelearningdeployment",
    version := "0.0.1",

    // 3 add dependencies
    libraryDependencies ++= Seq(
      "org.bytedeco"   % "javacv-platform" % openCVVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Runtime
    ),
    libraryDependencies ++= Seq(
      "io.grpc"         % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "com.lihaoyi"    %% "os-lib"            % osLibVersion,
      "dev.profunktor" %% "http4s-jwt-auth"   % http4sJwtVersion,
      "org.typelevel"  %% "log4cats-slf4j"    % "2.6.0"
    ),
    libraryDependencies ++= Seq(
      "is.cir" %% "ciris",
      "is.cir" %% "ciris-circe",
      "is.cir" %% "ciris-http4s"
    ).map(_ % cirisVersion),
    libraryDependencies ++= Seq("co.fs2" %% "fs2-core", "co.fs2" %% "fs2-io").map(_ % fs2Version),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client",
      "org.http4s" %% "http4s-ember-server",
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-circe"
    ).map(_ % http4sVersion),
    libraryDependencies ++= Seq(
      "org.typelevel"   %% "otel4s-core"                               % otel4sVersion,
      "org.typelevel"   %% "otel4s-java"                               % otel4sVersion,
      "io.opentelemetry" % "opentelemetry-exporter-otlp"               % openTelemetryVersion % Runtime,
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % openTelemetryVersion % Runtime
    ),
    javaOptions += "-Dotel.java.global-autoconfigure.enabled=true",
    javaOptions += "-Dotel.service.name=scala-machine-learning",
    javaOptions += "-Dotel.exporter.otlp.endpoint=http://localhost:4317",
    fork := true,
    docker / dockerfile := {
      val appDir: File = stage.value
      val targetDir    = "/app"
      new Dockerfile {
        from("eclipse-temurin:21-jre")
        expose(8080)
        expose(8081)
        entryPoint(s"$targetDir/bin/${executableScriptName.value}")
        copy(appDir, targetDir, chown = "daemon:daemon")
      }
    },
    docker / imageNames := Seq(ImageName("mattlangsenkamp/scalamachinelearningdeployment-mid:latest"))
  )
  .enablePlugins(sbtdocker.DockerPlugin, JavaAppPackaging)
  .dependsOn(protobuf, core.jvm) // explicitly depend on protobuf module

lazy val gatling = project
  .in(file("gatling"))
  .settings(
    version := "0.0.1",
    // GatlingIt / scalaSource := baseDirectory.value / ""
    libraryDependencies += "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test,it",
    libraryDependencies += "io.gatling" % "gatling-test-framework" % gatlingVersion % "test,it"
  )
  .enablePlugins(GatlingPlugin)

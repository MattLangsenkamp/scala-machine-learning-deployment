import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / scalaVersion := scala3Version

// 1 create dependency variables
val scala3Version    = "3.3.1"
val fs2Version       = "3.9.3"
val openCVVersion    = "1.5.9"
val circeVersion     = "0.15.0-M1"
val cirisVersion     = "3.5.0"
val osLibVersion     = "0.9.2"
val http4sVersion    = "0.23.24"
val http4sDomVersion = "0.2.11"
val http4sJwtVersion = "1.2.1"

// 2 create protobuf module
lazy val protobuf = project
  .in(file("protobuf"))
  .settings(
    name := "protobuf"
  )
  .enablePlugins(Fs2Grpc) // explicitly depend on gRPC plugin

lazy val front = project
  .in(file("front"))
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
      "io.circe"        %%% "circe-generic" % circeVersion,
      "org.http4s"      %%% "http4s-client" % "0.23.24",
      "org.http4s"      %%% "http4s-circe"  % "0.23.24",
      "org.scalameta"   %%% "munit"         % "0.7.29" % Test
    ),
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
  )

lazy val server = project
  .in(file("server"))
  .settings(
    name    := "scalamachinelearningdeployment",
    version := "0.0.1",
    // 3 add dependencies
    libraryDependencies ++= Seq(
      "io.grpc"         % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "org.bytedeco"    % "javacv-platform"   % openCVVersion,
      "com.lihaoyi"    %% "os-lib"            % osLibVersion,
      "org.scalameta"  %% "munit"             % "0.7.29" % Test,
      "dev.profunktor" %% "http4s-jwt-auth"   % http4sJwtVersion
    ),
    libraryDependencies ++= Seq("is.cir" %% "ciris", "is.cir" %% "ciris-circe").map(_ % cirisVersion),
    libraryDependencies ++= Seq("co.fs2" %% "fs2-core", "co.fs2" %% "fs2-io").map(_ % fs2Version),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-client",
      "org.http4s" %% "http4s-ember-server",
      "org.http4s" %% "http4s-dsl",
      "org.http4s" %% "http4s-circe"
    ).map(_ % http4sVersion),
    fork := true
  )
  .dependsOn(protobuf) // explicitly depend on protobuf module

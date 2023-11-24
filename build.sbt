// 1 create dependency variables
val scala3Version = "3.3.1"
val fs2Version    = "3.9.3"
val openCVVersion = "1.5.9"
val circeVersion  = "0.14.6"
val osLibVersion  = "0.9.2"

// 2 create protobuf module
lazy val protobuf =
  project
    .in(file("protobuf"))
    .settings(
      name         := "protobuf",
      scalaVersion := scala3Version
    )
    .enablePlugins(Fs2Grpc) // explicitly depend on gRPC plugin

lazy val root = project
  .in(file("."))
  .settings(
    name         := "scalamachinelearningdeployment",
    version      := "0.0.1",
    scalaVersion := scala3Version,
    // 3 add dependencies
    libraryDependencies ++= Seq(
      "io.grpc"        % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "co.fs2"        %% "fs2-core"          % fs2Version,
      "co.fs2"        %% "fs2-io"            % fs2Version,
      "org.bytedeco"   % "javacv-platform"   % openCVVersion,
      "io.circe"      %% "circe-core"        % circeVersion,
      "io.circe"      %% "circe-generic"     % circeVersion,
      "io.circe"      %% "circe-parser"      % circeVersion,
      "com.lihaoyi"   %% "os-lib"            % osLibVersion,
      "org.scalameta" %% "munit"             % "0.7.29" % Test
    ),
    fork := true
  )
  .dependsOn(protobuf) // explicitly depend on protobuf module

val Http4sVersion = "0.23.29"
val CirceVersion  = "0.14.10"

lazy val app = (project in file("."))
  .settings(
    name         := "remote-camera-mcp",
    version      := "0.1.0",
    scalaVersion := "3.3.4",
    scalacOptions ++= Seq("-Wunused:all", "-feature", "-deprecation"),
    Compile / mainClass := Some("com.github.kmizu.remotecamera.Main"),
    libraryDependencies ++= Seq(
      "org.http4s"             %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"             %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"             %% "http4s-circe"        % Http4sVersion,
      "org.http4s"             %% "http4s-dsl"          % Http4sVersion,
      "io.circe"               %% "circe-core"          % CirceVersion,
      "io.circe"               %% "circe-generic"       % CirceVersion,
      "io.circe"               %% "circe-parser"        % CirceVersion,
      "org.scala-lang.modules" %% "scala-xml"           % "2.3.0",
    ),
  )

// Usage:
//   sbt run                         -- run on JVM
//   sbt "run --port 3001"           -- custom port
//   sbt compile                     -- compile only
//   sbt assembly                    -- build fat JAR (if sbt-assembly added)

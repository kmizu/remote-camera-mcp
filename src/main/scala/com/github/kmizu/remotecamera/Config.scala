package com.github.kmizu.remotecamera

import scala.io.Source
import scala.util.Try

// ---------------------------------------------------------------------------
// Configuration loaded from environment variables (with .env fallback)
// ---------------------------------------------------------------------------

case class CameraConfig(
    host: String,
    username: String,
    password: String,
    onvifPort: Int = 2020,
    mountMode: String = "normal", // "normal" | "ceiling"
)

case class ServerConfig(
    host: String = "0.0.0.0",
    port: Int = 8000,
    captureDir: String = "/tmp/remote-camera-mcp",
)

object Config:
  /** Load configuration from env vars, falling back to `.env` file. */
  def load(): (CameraConfig, ServerConfig) =
    val camera = CameraConfig(
      host = env("TAPO_CAMERA_HOST"),
      username = env("TAPO_USERNAME"),
      password = env("TAPO_PASSWORD"),
      onvifPort = envOpt("TAPO_ONVIF_PORT").flatMap(s => Try(s.toInt).toOption).getOrElse(2020),
      mountMode = envOpt("TAPO_MOUNT_MODE").getOrElse("normal"),
    )
    val server = ServerConfig(
      captureDir = envOpt("CAPTURE_DIR").getOrElse("/tmp/remote-camera-mcp"),
    )
    (camera, server)

  // -- helpers --------------------------------------------------------------

  private lazy val dotEnv: Map[String, String] =
    val file = java.io.File(".env")
    if !file.exists() then Map.empty
    else
      Try(Source.fromFile(file)).toOption.map { src =>
        try
          src
            .getLines()
            .map(_.trim)
            .filter(l => l.nonEmpty && !l.startsWith("#"))
            .flatMap { line =>
              line.split("=", 2) match
                case Array(k, v) => Some(k.trim -> v.trim)
                case _           => None
            }
            .toMap
        finally src.close()
      }.getOrElse(Map.empty)

  private def env(key: String): String =
    envOpt(key).getOrElse(throw RuntimeException(s"$key environment variable is required"))

  private def envOpt(key: String): Option[String] =
    sys.env.get(key).orElse(dotEnv.get(key)).filter(_.nonEmpty)

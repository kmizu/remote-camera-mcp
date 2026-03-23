package com.github.kmizu.remotecamera

import cats.effect.*
import org.http4s.ember.client.EmberClientBuilder
import com.github.kmizu.remotecamera.mcp.McpServer
import com.github.kmizu.remotecamera.onvif.OnvifClient

object Main extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val (host, port) = parseArgs(args)
    val (camConfig, srvConfig) = Config.load()

    EmberClientBuilder
      .default[IO]
      .build
      .use: httpClient =>
        for
          _      <- IO.println(s"Connecting to camera at ${camConfig.host}:${camConfig.onvifPort}...")
          onvif   = OnvifClient(httpClient, camConfig)
          camera <- Camera.create(camConfig, srvConfig, onvif)
          _      <- IO.println(s"Connected. Starting MCP server on $host:$port/mcp")
          _      <- McpServer(camera).start(host, port)
        yield ExitCode.Success

  private def parseArgs(as: List[String]): (String, Int) =
    var host = "0.0.0.0"
    var port = 8000
    var rest = as
    while rest.nonEmpty do
      rest match
        case "--host" :: h :: tail => host = h; rest = tail
        case "--port" :: p :: tail => port = p.toInt; rest = tail
        case _ :: tail             => rest = tail
        case Nil                   => ()
    (host, port)

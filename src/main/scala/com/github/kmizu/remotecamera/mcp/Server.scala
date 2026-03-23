package com.github.kmizu.remotecamera.mcp

import cats.effect.*
import com.comcast.ip4s.*
import io.circe.*
import io.circe.syntax.*
import org.http4s.{Protocol as _, *}
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.ci.CIString
import java.util.UUID
import com.github.kmizu.remotecamera.Camera

// ===========================================================================
// MCP Streamable HTTP server — POST /mcp
// ===========================================================================

class McpServer(camera: Camera):

  private val sessionId = UUID.randomUUID().toString

  def start(host: String, port: Int): IO[Nothing] =
    val h = Host.fromString(host).getOrElse(host"0.0.0.0")
    val p = Port.fromInt(port).getOrElse(port"8000")
    EmberServerBuilder
      .default[IO]
      .withHost(h)
      .withPort(p)
      .withHttpApp(routes.orNotFound)
      .build
      .useForever

  // -- Routes ---------------------------------------------------------------

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO]:
    case req @ POST -> Root / "mcp" =>
      for
        json    <- req.as[Json]
        rpcReq  <- IO.fromEither(json.as[RpcRequest])
        rpcResp <- handle(rpcReq)
        resp    <- rpcResp match
          case Some(r) =>
            Ok(r.asJson, Header.Raw(CIString("Mcp-Session-Id"), sessionId))
          case None =>
            Accepted()
      yield resp

  // -- Request handling -----------------------------------------------------

  private def handle(req: RpcRequest): IO[Option[RpcResponse]] =
    req.method match
      case "initialize" =>
        val result = InitResult(
          protocolVersion = Protocol.Version,
          capabilities = ServerCapabilities(tools = Some(ToolsCap(listChanged = Some(false)))),
          serverInfo = ServerInfo("remote-camera-mcp", "0.1.0"),
        )
        IO.pure(Some(Protocol.success(req.id, result.asJson)))

      case "notifications/initialized" =>
        IO.pure(None)

      case "tools/list" =>
        IO.pure(Some(Protocol.success(req.id, ListToolsResult(toolDefs).asJson)))

      case "tools/call" =>
        val params = req.params.flatMap(_.as[CallToolParams].toOption)
          .getOrElse(CallToolParams("", None))
        camera
          .handleTool(params.name, params.arguments.getOrElse(Json.obj()))
          .map(r => Some(Protocol.success(req.id, r.asJson)))
          .handleError: e =>
            val err = CallToolResult(List(TextContent(s"Error: ${e.getMessage}")), isError = Some(true))
            Some(Protocol.success(req.id, err.asJson))

      case other =>
        IO.pure(Some(Protocol.methodNotFound(req.id, other)))

  // -- Tool definitions -----------------------------------------------------

  private val emptySchema = Json.obj("type" -> "object".asJson, "properties" -> Json.obj())

  private def degreesSchema(default: Int): Json = Json.obj(
    "type" -> "object".asJson,
    "properties" -> Json.obj(
      "degrees" -> Json.obj(
        "type" -> "integer".asJson,
        "minimum" -> 1.asJson,
        "maximum" -> 90.asJson,
        "default" -> default.asJson,
      )
    ),
  )

  private val toolDefs: List[Tool] = List(
    Tool("see",
      "See what's in front of you right now (capture image).",
      emptySchema),
    Tool("look_left",
      "Turn your head to the LEFT.",
      degreesSchema(30)),
    Tool("look_right",
      "Turn your head to the RIGHT.",
      degreesSchema(30)),
    Tool("look_up",
      "Tilt your head UP.",
      degreesSchema(20)),
    Tool("look_down",
      "Tilt your head DOWN.",
      degreesSchema(20)),
    Tool("look_around",
      "Look around the room (captures center, left, right, up).",
      emptySchema),
    Tool("camera_info",
      "Get camera device information.",
      emptySchema),
    Tool("camera_presets",
      "List saved camera position presets.",
      emptySchema),
    Tool("camera_go_to_preset",
      "Move camera to a saved preset position.",
      Json.obj(
        "type" -> "object".asJson,
        "properties" -> Json.obj(
          "preset_id" -> Json.obj("type" -> "string".asJson),
        ),
        "required" -> List("preset_id").asJson,
      )),
  )

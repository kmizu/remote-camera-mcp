package com.github.kmizu.remotecamera.mcp

import io.circe.*
import io.circe.syntax.*
import io.circe.generic.semiauto.*

// ===========================================================================
// JSON-RPC 2.0
// ===========================================================================

case class RpcRequest(
    jsonrpc: String,
    id: Option[Json],
    method: String,
    params: Option[Json],
)
object RpcRequest:
  given Decoder[RpcRequest] = deriveDecoder

case class RpcError(code: Int, message: String, data: Option[Json] = None)
object RpcError:
  given Encoder[RpcError] = deriveEncoder

case class RpcResponse(
    jsonrpc: String,
    id: Option[Json],
    result: Option[Json] = None,
    error: Option[RpcError] = None,
)
object RpcResponse:
  given Encoder[RpcResponse] = deriveEncoder

// ===========================================================================
// MCP types
// ===========================================================================

case class ServerInfo(name: String, version: String)
object ServerInfo:
  given Encoder[ServerInfo] = deriveEncoder

case class ToolsCap(listChanged: Option[Boolean] = None)
object ToolsCap:
  given Encoder[ToolsCap] = deriveEncoder

case class ServerCapabilities(tools: Option[ToolsCap] = None)
object ServerCapabilities:
  given Encoder[ServerCapabilities] = deriveEncoder

case class InitResult(
    protocolVersion: String,
    capabilities: ServerCapabilities,
    serverInfo: ServerInfo,
)
object InitResult:
  given Encoder[InitResult] = deriveEncoder

// -- Tools ------------------------------------------------------------------

case class Tool(name: String, description: String, inputSchema: Json)
object Tool:
  given Encoder[Tool] = deriveEncoder

case class ListToolsResult(tools: List[Tool])
object ListToolsResult:
  given Encoder[ListToolsResult] = deriveEncoder

case class CallToolParams(name: String, arguments: Option[Json])
object CallToolParams:
  given Decoder[CallToolParams] = deriveDecoder

// -- Content ----------------------------------------------------------------

sealed trait Content
case class TextContent(text: String) extends Content
case class ImageContent(data: String, mimeType: String) extends Content

object Content:
  given Encoder[Content] = Encoder.instance:
    case t: TextContent =>
      Json.obj("type" -> "text".asJson, "text" -> t.text.asJson)
    case i: ImageContent =>
      Json.obj("type" -> "image".asJson, "data" -> i.data.asJson, "mimeType" -> i.mimeType.asJson)

case class CallToolResult(content: List[Content], isError: Option[Boolean] = None)
object CallToolResult:
  given Encoder[CallToolResult] = Encoder.instance: r =>
    val fields: List[(String, Json)] =
      ("content" -> r.content.asJson) ::
        r.isError.map(e => "isError" -> e.asJson).toList
    Json.obj(fields*)

// ===========================================================================
// Helpers
// ===========================================================================

object Protocol:
  val Version = "2024-11-05"

  def success(id: Option[Json], result: Json): RpcResponse =
    RpcResponse("2.0", id, result = Some(result))

  def error(id: Option[Json], code: Int, msg: String): RpcResponse =
    RpcResponse("2.0", id, error = Some(RpcError(code, msg)))

  def methodNotFound(id: Option[Json], method: String): RpcResponse =
    error(id, -32601, s"Method not found: $method")

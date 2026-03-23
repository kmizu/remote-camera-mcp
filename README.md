# Remote Camera MCP Server

[日本語版はこちら](README-ja.md)

MCP server (Scala 3) that controls TP-Link Tapo Wi-Fi PTZ cameras via ONVIF, giving AI models **eyes** (camera) and a **neck** (pan/tilt) over Streamable HTTP.

No external command dependencies — runs on JDK alone.

## Supported Cameras

- TP-Link Tapo C210 (3MP)
- TP-Link Tapo C220 (4MP)
- Other ONVIF-compatible Tapo pan/tilt cameras

## Tools

| Tool | Description |
|------|-------------|
| `see` | Capture an image of what's currently visible |
| `look_left` | Turn left (degrees configurable) |
| `look_right` | Turn right |
| `look_up` | Tilt up |
| `look_down` | Tilt down |
| `look_around` | Look around the room (captures 4 angles) |
| `camera_info` | Get camera device information |
| `camera_presets` | List saved position presets |
| `camera_go_to_preset` | Move to a saved preset position |

## Quick Start

### Prerequisites

- **JDK 21+**
- **sbt** (Scala build tool)

### 1. Configure

```bash
cp .env.example .env
```

Edit `.env` with your camera credentials:

```
CAMERA_HOST=192.168.1.100
CAMERA_USERNAME=your-name
CAMERA_PASSWORD=your-password
```

### 2. Run

```bash
# Linux / macOS
./start.sh

# Windows
start.bat

# Or directly with sbt
sbt run
sbt "run --port 3001"    # custom port
```

The server starts at `http://0.0.0.0:8000/mcp`.

## Connecting MCP Clients

Streamable HTTP transport only. Endpoint: `http://<host>:8000/mcp`.

### Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "remote-camera": {
      "type": "streamable-http",
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

### Claude Code

Add to `.mcp.json`:

```json
{
  "mcpServers": {
    "remote-camera": {
      "type": "streamable-http",
      "url": "http://localhost:8000/mcp"
    }
  }
}
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `CAMERA_HOST` | Yes | - | Camera IP address |
| `CAMERA_USERNAME` | Yes | - | Camera account username |
| `CAMERA_PASSWORD` | Yes | - | Camera account password |
| `CAMERA_ONVIF_PORT` | No | `2020` | ONVIF port |
| `CAMERA_MOUNT_MODE` | No | `normal` | `normal` or `ceiling` (inverts pan direction) |
| `CAPTURE_DIR` | No | `/tmp/remote-camera-mcp` | Image save directory |

## Tech Stack

| Library | Purpose |
|---------|---------|
| **http4s-ember** | HTTP server & client |
| **circe** | JSON (MCP JSON-RPC protocol) |
| **scala-xml** | ONVIF SOAP communication |
| **cats-effect** | Async IO |

## Camera Setup

1. Install the "TP-Link Tapo" app on your phone
2. Create a Tapo account and add your camera via the app
3. Find the camera's IP address (Tapo app > Camera Settings > Device Info)
4. Create a camera account (Tapo app > Camera > Settings > Advanced > Camera Account)
5. Use the camera account credentials (not your Tapo cloud account) in `.env`

> **Tip**: Set a DHCP reservation on your router so the camera IP stays fixed after reboots.

## Troubleshooting

- **Can't connect**: Ensure camera and PC are on the same network; check firewall rules
- **Auth error**: Use the camera's local account, not the TP-Link cloud account
- **No image**: Update camera firmware; reboot the camera

## License

MIT License

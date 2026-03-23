# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

MCP server (Scala 3) that wraps TP-Link Tapo Wi-Fi PTZ cameras (C210/C220) via ONVIF, giving AI models eyes (camera) and a neck (pan/tilt). No external command dependencies — runs on JDK alone.

## Commands

```bash
sbt run                           # Start MCP server (Streamable HTTP on 0.0.0.0:8000, /mcp endpoint)
sbt "run --port 3001"             # Custom port
sbt compile                       # Compile only
```

## Architecture

```
Main.scala          ── IOApp entry point, CLI args, wiring
  └─→ mcp/
  │     ├─ Server.scala    ── http4s Streamable HTTP routes, tool dispatch
  │     └─ Protocol.scala  ── JSON-RPC & MCP types, circe codecs
  └─→ Camera.scala         ── high-level camera ops (capture, PTZ, tool handler)
  └─→ onvif/
  │     └─ Client.scala    ── ONVIF SOAP client, WS-Security UsernameToken
  └─→ Config.scala         ── env var + .env file loading
```

**Key patterns:**
- cats-effect `IO` throughout — all effects are pure
- `Ref[IO, _]` for mutable state (camera position, ONVIF session)
- Capture via ONVIF `GetSnapshotUri` (HTTP download, no ffmpeg)
- Ceiling mount mode (`CAMERA_MOUNT_MODE=ceiling`): inverts pan direction
- ONVIF service discovery via `GetCapabilities` on connect

**Stack:**
- http4s-ember (HTTP server & client)
- circe (JSON) — MCP JSON-RPC
- scala-xml (ONVIF SOAP)
- cats-effect IO (async)

## Environment Variables

See `.env.example` for full list. Required: `CAMERA_HOST`, `CAMERA_USERNAME`, `CAMERA_PASSWORD`.

## External Dependencies

- JDK 21+

# currentDate
Today's date is 2026-03-23.

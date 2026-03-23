# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

MCP server (Scala 3) that wraps TP-Link Tapo Wi-Fi PTZ cameras (C210/C220) via ONVIF, giving AI models eyes (camera), a neck (pan/tilt), and ears (microphone + Whisper transcription). Cross-compiles to JVM and Scala Native.

## Commands

```bash
sbt appJVM/run                         # Start MCP server (Streamable HTTP on 0.0.0.0:8000, /mcp endpoint)
sbt "appJVM/run -- --port 3001"        # Custom port
sbt appNative/nativeLink               # Build native binary
sbt appJVM/compile                     # Compile only
```

## Architecture

```
Main.scala          ── IOApp entry point, CLI args, wiring
  └─→ mcp/
  │     ├─ Server.scala    ── http4s Streamable HTTP routes, tool dispatch
  │     └─ Protocol.scala  ── JSON-RPC & MCP types, circe codecs
  └─→ Camera.scala         ── high-level camera ops (capture, PTZ, audio, tool handler)
  └─→ onvif/
  │     └─ Client.scala    ── ONVIF SOAP client, WS-Security UsernameToken
  └─→ Config.scala         ── env var + .env file loading
```

**Key patterns:**
- cats-effect `IO` throughout — all effects are pure
- `Ref[IO, _]` for mutable state (camera position, ONVIF session)
- Capture fallback chain: ONVIF `GetSnapshotUri` → ffmpeg RTSP
- Ceiling mount mode (`TAPO_MOUNT_MODE=ceiling`): inverts pan direction
- ONVIF service discovery via `GetCapabilities` on connect

**Stack:**
- http4s-ember (HTTP server & client) — JVM & Native
- circe (JSON) — MCP JSON-RPC
- scala-xml (ONVIF SOAP)
- cats-effect IO (async)

## Environment Variables

See `.env.example` for full list. Required: `TAPO_CAMERA_HOST`, `TAPO_USERNAME`, `TAPO_PASSWORD`.

## External Dependencies

- `ffmpeg` must be installed on the system (used for RTSP capture and audio recording)
- `whisper` CLI for audio transcription (optional)
- JDK 11+ (for JVM) or clang + libunwind (for Native)

# currentDate
Today's date is 2026-03-23.

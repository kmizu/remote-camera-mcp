@echo off
setlocal

if "%MCP_HOST%"=="" set MCP_HOST=0.0.0.0
if "%MCP_PORT%"=="" set MCP_PORT=8000

sbt "run --host %MCP_HOST% --port %MCP_PORT%"

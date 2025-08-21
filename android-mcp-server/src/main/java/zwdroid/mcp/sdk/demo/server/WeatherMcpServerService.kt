package zwdroid.mcp.sdk.demo.server

import zwdroid.mcp.sdk.server.McpServerService

class WeatherMcpServerService : McpServerService<WeatherMcpServer>(WeatherMcpServer::class.java)

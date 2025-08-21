package zwdroid.mcp.sdk.client

import zwdroid.mcp.sdk.common.McpServerInfo


data class McpConnection(
    val serverInfo: McpServerInfo,
    val client: McpClient
)

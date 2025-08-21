package zwdroid.mcp.sdk.client

import android.content.ComponentName
import android.content.Context
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import zwdroid.mcp.sdk.common.InvalidStateException
import zwdroid.mcp.sdk.common.ServiceCommunicationException
import zwdroid.mcp.sdk.common.ToolNotFoundException
import zwdroid.mcp.sdk.transport.BinderClientTransport
import zwdroid.mcp.sdk.common.KLog

class McpClient(
  private val context: Context,
  private val serviceComponent: ComponentName,
  private val scope: CoroutineScope
) {
  companion object {
    private const val TAG = "McpClient"
  }

  private var clientTransport: BinderClientTransport? = null
  private var client: Client? = null
  private val connectionMutex = Mutex()
  private var connected = false
  private var isConnecting = false

  fun getServiceComponent(): ComponentName = serviceComponent

  fun isConnected(): Boolean = connected

  private suspend fun ensureConnected() {
    KLog.d(TAG, "ensureConnected 进入")

    connectionMutex.withLock {
      KLog.d(TAG, "ensureConnected 进入 withLock")

      if (connected) {
        KLog.d(TAG, "ensureConnected connected 返回")
        return@withLock
        // return
      }

      KLog.d(TAG, "ensureConnected connected 后")

      if (isConnecting) {
        KLog.w(TAG, "ensureConnected isConnecting 返回")
        return@withLock
        // return
      }

      KLog.d(TAG, "ensureConnected isConnecting 后")

      try {
        isConnecting = true
        KLog.i(TAG, "ensureConnected Connecting to service: $serviceComponent")

        // Create transport
        clientTransport = BinderClientTransport(context, serviceComponent, scope)

        KLog.i(TAG, "ensureConnected BinderClientTransport 后")

        // Create MCP client with default implementation
        client = Client(
          clientInfo = Implementation(
            name = "mcp-android-client",
            version = "1.0.0"
          )
        )

        KLog.i(TAG, "ensureConnected client connect 前")

        // Connect client to transport (this will automatically start the transport)
        client!!.connect(clientTransport!!)

        KLog.i(TAG, "ensureConnected client connect 后")

        connected = true
        KLog.i(TAG, "ensureConnected Successfully connected to service: $serviceComponent")

      } catch (e: Exception) {
        KLog.e(TAG, "ensureConnected Failed to connect to service: $serviceComponent", e)
        cleanup()
        throw ServiceCommunicationException("Failed to connect to service: $serviceComponent", e)
      } finally {
        isConnecting = false
        KLog.d(TAG, "ensureConnected finally")
      }
    }
  }

  /**
   * Calls a tool in the connected service.
   *
   * @param toolName Name of the tool to call
   * @param arguments Arguments to pass to the tool
   * @return Result of the tool execution
   * @throws ToolNotFoundException if the tool is not found
   * @throws ServiceCommunicationException if communication fails
   * @throws InvalidStateException if the client is in an invalid state
   */
  suspend fun callTool(toolName: String, arguments: Map<String, JsonElement>): CallToolResultBase {
    KLog.d(TAG, "在服务 $serviceComponent 上调用工具 $toolName。")

    KLog.d(TAG, "ensureConnected 前")
    ensureConnected()
    KLog.d(TAG, "ensureConnected 后")

    // val client = mcpClient ?: throw InvalidStateException("调用工具失败。原因：MCP 客户端尚未初始化。")

    return try {
      KLog.d(TAG, "callTool 前")
      val result = client!!.callTool(toolName, arguments)
      KLog.d(TAG, "callTool 后")

      KLog.d(TAG) { "Tool '$toolName' call completed successfully" }
      result ?: throw ServiceCommunicationException("Tool call returned null result")
    } catch (e: Exception) {
      KLog.e(TAG, "Tool '$toolName' call failed", e)
      when {
        e.message?.contains("not found", ignoreCase = true) == true -> {
          throw ToolNotFoundException(toolName)
        }

        else -> {
          throw ServiceCommunicationException("Tool call failed: ${e.message}", e)
        }
      }
    }
  }

  /**
   * Lists all tools available in the connected service.
   *
   * @return List of available tools
   * @throws ServiceCommunicationException if communication fails
   * @throws InvalidStateException if the client is in an invalid state
   */
  suspend fun listTools(): List<io.modelcontextprotocol.kotlin.sdk.Tool> {
    KLog.d(TAG) { "Listing tools from service: $serviceComponent" }

    // Ensure we're connected
    ensureConnected()

    // val client = mcpClient ?: throw InvalidStateException("MCP client not initialized")

    return try {
      val result = client?.listTools()
      val tools = result?.tools ?: emptyList()
      KLog.d(TAG) { "Retrieved ${tools.size} tools from service: $serviceComponent" }
      tools
    } catch (e: Exception) {
      KLog.e(TAG, "Failed to list tools from service: $serviceComponent", e)
      throw ServiceCommunicationException("Failed to list tools: ${e.message}", e)
    }
  }

  /**
   * Disconnects from the service and cleans up resources.
   * After calling this method, the client must be reconnected before making tool calls.
   */
  suspend fun disconnect() {
    connectionMutex.withLock {
      KLog.i(TAG, "Disconnecting from service: $serviceComponent")
      cleanup()
    }
  }

  /**
   * Internal cleanup method that releases all resources.
   */
  private fun cleanup() {
    try {
      client?.let { client ->
        kotlinx.coroutines.runBlocking {
          try {
            client.close()
          } catch (e: Exception) {
            KLog.w(TAG, "Error closing MCP client", e)
          }
        }
      }

      clientTransport?.let { t ->
        kotlinx.coroutines.runBlocking {
          try {
            t.close()
          } catch (e: Exception) {
            KLog.w(TAG, "Error closing transport", e)
          }
        }
      }

    } catch (e: Exception) {
      KLog.e(TAG, "Error during cleanup", e)
    } finally {
      client = null
      clientTransport = null
      connected = false
    }
  }

  /**
   * Gets information about the underlying transport connection.
   * @return String representation of connection status
   */
  fun getConnectionInfo(): String {
    return "McpClient(service=$serviceComponent, connected=$connected, connecting=$isConnecting)"
  }
}

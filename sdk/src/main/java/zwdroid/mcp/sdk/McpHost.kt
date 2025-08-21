package zwdroid.mcp.sdk

import android.content.ComponentName
import android.content.Context
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import zwdroid.mcp.sdk.client.McpServiceRegistry
import zwdroid.mcp.sdk.common.ExecutionFailedException
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.common.McpException
import zwdroid.mcp.sdk.common.McpServerInfo
import zwdroid.mcp.sdk.common.McpServerInfoItem
import zwdroid.mcp.sdk.common.McpServerInfoItemType
import zwdroid.mcp.sdk.common.ToolNotFoundException

/**
 * McpHost provides the highest-level, simplest interface for LLM applications
 * to interact with MCP tools across the Android system.
 *
 * This interface abstracts away all the complexity of service discovery,
 * client management, and tool routing, providing a clean API that LLM
 * applications can easily integrate with.
 *
 * Features:
 * - Simple tool discovery and calling interface
 * - Automatic service management
 * - Comprehensive error handling
 * - Thread-safe operations
 *
 * Usage example:
 * ```kotlin
 * val mcpHost = McpHost.getInstance(context)
 *
 * // Get all available tools
 * val tools = mcpHost.listMcpServers()
 *
 * // Call a specific tool
 * val arguments = buildJsonObject {
 *     put("location", "San Francisco, CA")
 * }
 * val result = mcpHost.callTool("com.weather/.WeatherService#get_current_weather", arguments)
 * ```
 */
interface McpHost {

  /**
   * Gets all available MCP tools discovered across the system.
   *
   * This method performs service discovery and returns a comprehensive list
   * of all tools provided by all MCP services installed on the device.
   * Results are cached for performance.
   *
   * @return List of McpServerInfo objects representing all available tools
   * @throws zwdroid.mcp.sdk.common.McpException if service discovery fails
   */
  suspend fun listMcpServers(): List<McpServerInfo>

  suspend fun listMcpTools(): List<McpServerInfoItem>

  /**
   * Calls a specific MCP tool with the provided arguments.
   *
   * This method automatically:
   * - Finds the service that provides the tool
   * - Establishes connection if needed (lazy binding)
   * - Routes the call to the appropriate service
   * - Returns the result or throws appropriate exceptions
   *
   * @param qualifiedToolName Fully qualified tool name (e.g., "com.package/.Service#tool_name")
   * @param arguments Tool arguments as a JsonObject
   * @return Tool execution result as JsonElement
   * @throws zwdroid.mcp.sdk.common.ToolNotFoundException if the tool is not found
   * @throws zwdroid.mcp.sdk.common.InvalidParametersException if arguments are invalid
   * @throws zwdroid.mcp.sdk.common.PermissionDeniedException if access is denied
   * @throws zwdroid.mcp.sdk.common.ExecutionFailedException if tool execution fails
   * @throws zwdroid.mcp.sdk.common.ServiceCommunicationException if communication fails
   */
  // suspend fun callTool(qualifiedToolName: String, arguments: JsonObject): JsonElement

  suspend fun callTool(packageName: String, className: String, methodName: String, arguments: JsonObject? = null): JsonElement

}

/**
 * Default implementation of McpHost.
 * This implementation delegates to McpServiceRegistry for all operations.
 */
class DefaultMcpHost private constructor(
  private val context: Context
) : McpHost {

  companion object {
    private const val TAG = "McpHost"

    private lateinit var instance: DefaultMcpHost

    fun getInstance(context: Context): DefaultMcpHost {
      if (!::instance.isInitialized) {
        synchronized(this) {
          if (!::instance.isInitialized) {
            instance = DefaultMcpHost(context.applicationContext)
          }
        }
      }
      return instance
    }
  }

  private val mcpServerRegistry = McpServiceRegistry.getInstance(context)

  override suspend fun listMcpServers() = mcpServerRegistry.listMcpServers()

  override suspend fun listMcpTools() = mcpServerRegistry.listMcpTools()


  override suspend fun callTool(packageName: String, className: String, methodName: String, arguments: JsonObject?): JsonElement {
    KLog.d(TAG, "使用工具。包：$packageName 类：$className 方法：$methodName 参数：$arguments")

    return try {
      val client = mcpServerRegistry.getConnection(packageName, className, methodName).client
      val map = arguments?.toMap() ?: mapOf<String, JsonElement>()
      val result = client.callTool(methodName, map)
      KLog.d(TAG, "使用工具成功。包：$packageName 类：$className 方法：$methodName 参数：$arguments")

      val textContent = result.content.joinToString(" ") { content ->
        when (content) {
          is io.modelcontextprotocol.kotlin.sdk.TextContent -> content.text ?: ""
          else -> content.toString()
        }
      }

      kotlinx.serialization.json.JsonPrimitive(textContent)

    } catch (e: McpException) {
      // Re-throw MCP exceptions as-is
      KLog.e(TAG, "使用工具失败。包：$packageName 类：$className 方法：$methodName 参数：$arguments", e)
      throw e
    } catch (e: Exception) {
      KLog.e(TAG, "使用工具失败。包：$packageName 类：$className 方法：$methodName 参数：$arguments", e)
      throw ExecutionFailedException(methodName, e)
    }
  }
}

package zwdroid.mcp.sdk.server

import android.os.IBinder
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import zwdroid.mcp.sdk.annotation.*
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.transport.BinderServerTransport
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

/**
 * McpServer wraps the official MCP SDK Server and integrates it with Binder transport.
 *
 * This class provides the server-side MCP functionality using Android's Binder mechanism
 * for inter-process communication. It handles tool registration, request processing,
 * and response generation according to the MCP protocol.
 *
 * @param implementation Information about the server implementation
 * @param options Server configuration options
 * @param scope Coroutine scope for managing async operations
 * @param mcpService The McpService instance for permission validation
 */
open class McpServer() {
  companion object {
    private const val TAG = "McpServer"
  }

  private lateinit var transport: BinderServerTransport<out McpServer>
  lateinit var binder: IBinder
  private val server = Server(this.getImplementation(), this.getServerOptions())
  private var isStarted = false

  open fun getImplementation(): Implementation {
    val ann = this::class.findAnnotation<McpServerImplementation>()
    if (ann != null) {
      return Implementation(ann.name, ann.version)
    }

    return Implementation(this::class.qualifiedName ?: "Unknown", "")
  }

  open fun getServerOptions(): ServerOptions {
    return ServerOptions(
      capabilities = ServerCapabilities(
        tools = ServerCapabilities.Tools(listChanged = true),
        prompts = ServerCapabilities.Prompts(listChanged = true),
        resources = ServerCapabilities.Resources(subscribe = false, listChanged = true)
      )
    )
  }

  fun attachTransport(aTransport: BinderServerTransport<out McpServer>) {
    transport = aTransport
    binder = transport.binder
  }

  /**
   * Starts the MCP server and connects it to the Binder transport.
   * This method should be called during service initialization.
   */
  suspend fun start() {
    if (isStarted) {
      KLog.w(TAG, "MCP 服务已经启动。")
      return
    }

    KLog.i(TAG, "开始启动 MCP 服务。")

    try {
      server.connect(transport)
      isStarted = true
      KLog.i(TAG, "启动 MCP 服务成功。")
    } catch (e: Exception) {
      KLog.e(TAG, "启动 MCP 服务失败。", e)
      throw e
    }
  }

  /**
   * Stops the MCP server and closes the transport.
   * This method should be called during service cleanup.
   */
  suspend fun stop() {
    if (!isStarted) {
      KLog.w(TAG, "MCP 服务未启动。")
      return
    }

    KLog.i(TAG, "开始停止 MCP 服务。")

    try {
      server.close()
      isStarted = false
      KLog.i(TAG, "停止 MCP 服务成功。")
    } catch (e: Exception) {
      KLog.e(TAG, "停止 MCP 服务失败。", e)
      throw e
    }
  }

  open fun register() {
    server.addTools(scanTools())
    server.addPrompts(scanPrompts())
    server.addResources(scanResources())
  }

  open fun scanTools(): List<RegisteredTool> {
    KLog.d(TAG, "扫描工具。")
    val tools = mutableListOf<RegisteredTool>()
    this::class.memberFunctions.forEach { function ->
      val tool = getRegisteredTool(function)
      if (tool != null) {
        KLog.d(TAG, "发现工具。名字：${tool.tool.name} 包：${this::class.java.packageName} 类：${this::class.java.name} 方法：${function.name} ${tool.tool}")
        tools.add(tool)
      }
    }

    return tools
  }

  fun isCallToolMethod(function: KFunction<*>): Boolean {
    val declaredParameters = function.valueParameters
    if (declaredParameters.isEmpty()) return false

    // 检查参数类型
    val firstParamType = declaredParameters[0].type
    return firstParamType.classifier == CallToolRequest::class &&
        function.returnType.classifier == CallToolResult::class
  }


  fun getRegisteredTool(function: KFunction<*>): RegisteredTool? {
    val annotation = function.findAnnotation<McpTool>() ?: return null
    if (!isCallToolMethod(function)) {
      return null
    }

    var name = annotation.method
    if (name == "") {
      name = function.name
    }

    val tool = Tool(
      name = name,
      title = name,
      description = annotation.description,
      inputSchema = Tool.Input(),
      annotations = ToolAnnotations(title = ""),
      outputSchema = Tool.Output()
    )

    // TODO 从注解构建 inputSchema 和 outputSchema。
    return RegisteredTool(tool) {
      function.callSuspend(this, it) as CallToolResult
    }
  }

  // TODO
  open fun scanPrompts(): List<RegisteredPrompt> = emptyList()

  // TODO
  open fun scanResources(): List<RegisteredResource> = emptyList()

  fun getUnderlyingServer(): Server = server

  fun isRunning(): Boolean = isStarted
}

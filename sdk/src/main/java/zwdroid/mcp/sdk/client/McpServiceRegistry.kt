package zwdroid.mcp.sdk.client

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.IBinder
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zwdroid.mcp.sdk.IJsonRpcService
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.common.McpServerInfo
import zwdroid.mcp.sdk.common.McpServerInfoItem
import zwdroid.mcp.sdk.common.McpServerInfoItemType
import zwdroid.mcp.sdk.common.ServiceDiscoveryException
import zwdroid.mcp.sdk.server.McpServerService


/**
 * McpServiceRegistry manages service discovery, client lifecycle, and provides
 * a unified interface for accessing MCP tools across multiple services.
 *
 * This singleton class provides:
 * - Automatic service discovery with caching
 * - Dynamic service monitoring via broadcast receivers
 * - Client lifecycle management
 * - Tool lookup and routing
 *
 * Features:
 * - Singleton pattern for application-wide access
 * - Lazy client creation (clients are created only when needed)
 * - Automatic cache invalidation on package changes
 * - Coroutine-based async operations
 */
class McpServiceRegistry private constructor(
  private val context: Context,
  private val managerScope: CoroutineScope = CoroutineScope(SupervisorJob())
) {
  companion object {
    private const val TAG = "McpServiceRegistry"
    private lateinit var instance: McpServiceRegistry

    fun getInstance(context: Context): McpServiceRegistry {
      if (!::instance.isInitialized) {
        synchronized(this) {
          if (!::instance.isInitialized) {
            instance = McpServiceRegistry(context.applicationContext)
          }
        }
      }
      return instance
    }
  }

  // Cache for discovered services and their tools
  private val serverInfos = ConcurrentHashMap<ComponentName, McpServerInfo>()
  private val connections = ConcurrentHashMap<ComponentName, McpConnection>()

  // Mutex for coordinating cache updates
  private val cacheMutex = Mutex()

  // Track if discovery has been performed
  private var isDiscoveryComplete = false

  // Broadcast receiver for monitoring package changes
  private val packageDeployEventReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        Intent.ACTION_PACKAGE_ADDED,
        Intent.ACTION_PACKAGE_REMOVED,
        Intent.ACTION_PACKAGE_REPLACED -> {
          val packageName = intent.data?.schemeSpecificPart
          KLog.i(TAG, "Package change detected: ${intent.action} for $packageName")

          // Invalidate cache and trigger rediscovery
          runBlocking {
            invalidMcpServerCache()
            discoverMcpServer()
          }
        }
      }
    }
  }

  init {
    // Register for package change notifications
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_PACKAGE_ADDED)
      addAction(Intent.ACTION_PACKAGE_REMOVED)
      addAction(Intent.ACTION_PACKAGE_REPLACED)
      addDataScheme("package")
    }
    context.registerReceiver(packageDeployEventReceiver, filter)
    KLog.i(TAG, "McpServiceRegistry initialized")
  }

  /**
   * Discovers all available MCP services and their tools.
   * Results are cached for performance.
   *
   * @return List of all discovered tools across all services
   * @throws ServiceDiscoveryException if discovery fails
   */
  suspend fun discoverMcpServer(): List<McpServerInfo> {
    cacheMutex.withLock {
      if (isDiscoveryComplete && serverInfos.isNotEmpty()) {
        KLog.d(TAG, "返回 MCP 服务缓存信息。")
        return serverInfos.values.toList()
      }

      KLog.i(TAG, "开始发现 MCP 服务。")

      try {
        val serverInfoList = mutableListOf<McpServerInfo>()
        val intent = Intent(McpServerService.MCP_SERVER_SERVICE_ACTION)
        val resolveInfos = context.packageManager.queryIntentServices(
          intent,
          PackageManager.GET_META_DATA
        )

        KLog.d(TAG, "发现候选 MCP 服务。数量：${resolveInfos.size}。")

        resolveInfos.forEach { resolveInfo ->
          val mcpServiceInfo = getMcpServiceInfo(resolveInfo)
          mcpServiceInfo?.let {
            serverInfoList.add(it)
          }
        }

        isDiscoveryComplete = true
        KLog.i(TAG, "发现 MCP 服务结束。服务数：${serverInfoList.size} 。")

        serverInfoList.forEach { info ->
          serverInfos.put(info.getComponentName(), info)
        }

        return serverInfoList
      } catch (e: Exception) {
        KLog.e(TAG, "发现 MCP 服务失败。", e)
        throw ServiceDiscoveryException("发现 MCP 服务失败。", e)
      }
    }
  }

  private suspend fun getMcpServiceInfo(resolveInfo: ResolveInfo): McpServerInfo? = suspendCancellableCoroutine { continuation ->
    val componentName = ComponentName(
      resolveInfo.serviceInfo.packageName,
      resolveInfo.serviceInfo.name
    )

    val serviceConnection = object : ServiceConnection {
      private var binder: IBinder? = null

      fun call(jsonRpcService: IJsonRpcService, method: String): JSONRPCResponse {
        val request = JSONRPCRequest(
          RequestId.StringId(UUID.randomUUID().toString()),
          method = method
        )

        val requestStr = McpJson.encodeToString(request)
        KLog.d(TAG, "调用 JSON RPC 服务。请求：${requestStr}")
        val responseStr = jsonRpcService.call(requestStr)
        KLog.d(TAG, "调用 JSON RPC 服务。应答：${responseStr}")
        val response = McpJson.decodeFromString<JSONRPCResponse>(responseStr)
        return response
      }

      override fun onServiceConnected(name: ComponentName, ibinder: IBinder) {
        KLog.d(TAG, "已连接服务。服务名：$name")
        binder = ibinder
        try {
          val jsonRpcService = IJsonRpcService.Stub.asInterface(ibinder)

          val listToolsResp = call(jsonRpcService, "tools/list")
          // TODO
          // val listPromptsResp = call(jsonRpcService, "prompts/list")
          // val listResourcesResp = call(jsonRpcService, "resources/list")

          var items = mutableListOf<McpServerInfoItem>()
          KLog.d(TAG, "listToolsResp.result：${listToolsResp.result}")
          val listToolsResult = listToolsResp.result as? ListToolsResult
          if (listToolsResult == null) {
            continuation.resume(null) {}
          }

          listToolsResult!!.tools.forEach { tool ->
            val item = McpServerInfoItem(
              itemType = McpServerInfoItemType.Tool,
              packageName = name.packageName,
              className = name.className,
              methodName = tool.name,
              description = tool.description ?: ""
            )
            items.add(item)
          }

          KLog.d(TAG, "items：${items}")

          val mcpServerInfo = McpServerInfo(packageName = name.packageName, className = name.className, items = items)
          KLog.d(TAG, "MCP 服务信息：${mcpServerInfo}")

          continuation.resume(mcpServerInfo) {}

        } catch (e: Exception) {
          KLog.e(TAG, "调用 MCP 服务失败", e)
          continuation.resume(null) {}
        } finally {
          //context.unbindService(this)
        }
      }

      override fun onServiceDisconnected(name: ComponentName) {
        KLog.w(TAG, "Service disconnected: $name")
        binder = null
        continuation.resume(null) {}
      }
    }

    val intent = Intent(McpServerService.MCP_SERVER_SERVICE_ACTION).setComponent(componentName)
    val ok = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    KLog.i(TAG, "绑定服务结果：${ok}")
    if (!ok) {
      continuation.resume(null) {}
    }

    // 添加超时处理
    continuation.invokeOnCancellation {
      context.unbindService(serviceConnection)
    }
  }

  /**
   * Gets all available tools across all discovered services.
   *
   * @return List of all available tools
   */
  suspend fun listMcpServers(): List<McpServerInfo> {
    return discoverMcpServer()
  }

  suspend fun listMcpItems(itemType: McpServerInfoItemType): List<McpServerInfoItem> {
    return try {
      val servers = listMcpServers()
      val tools = servers.flatMap { it.items }
        .filter { it.itemType == itemType }

      KLog.i(TAG, "发现 MCP 项。类型：${itemType.name} 数量：${tools.size}。")
      tools
    } catch (e: Exception) {
      KLog.e(TAG, "查询 MCP 项失败。原因：", e)
      throw e
    }
  }

  suspend fun listMcpTools() = listMcpItems(McpServerInfoItemType.Tool)

  suspend fun isMcpToolExist(packageName: String, className: String, methodName: String): Boolean {
    KLog.d(TAG, """查找工具

包：$packageName
类：$className
方法：$methodName

""".trimIndent())

    return listMcpTools().any {
      it.packageName == packageName && it.className == className && it.methodName == methodName
    }
  }

  /**
   * Gets or creates an McpClient for the specified service.
   * Implements lazy client creation.
   *
   * @param serviceComponent ComponentName of the service
   * @return McpClient instance
   */
  suspend fun getConnection(componentName: ComponentName): McpConnection {
    val serverInfo: McpServerInfo = serverInfos.get(componentName) ?: throw RuntimeException("获取 MCP 服务信息失败。")

    return connections.getOrPut(componentName) {
      KLog.d(TAG, "创建新 MCP 服务客户端。包：${componentName.packageName} 类：${componentName.className}")
      McpConnection(serverInfo, McpClient(context, componentName, managerScope))
    }
  }

  /**
   * Gets an McpClient for the service that provides the specified tool.
   *
   * @param qualifiedToolName Fully qualified tool name
   * @return McpClient instance
   * @throws ServiceDiscoveryException if tool or service not found
   */
  suspend fun getConnection(packageName: String, className: String, methodName: String): McpConnection {
    if (!isMcpToolExist(packageName, className, methodName)) {
      throw RuntimeException("获取工具失败。包：$packageName 类：$className 方法：$methodName")
    }
    return getConnection(ComponentName(packageName, className))
  }

  /**
   * Invalidates the service discovery cache.
   * Next discovery call will perform a fresh scan.
   */
  suspend fun invalidMcpServerCache() {
    cacheMutex.withLock {
      KLog.i(TAG, "清除 MCP 服务信息缓存。")
      serverInfos.clear()
      isDiscoveryComplete = false
    }
  }

  /**
   * Disconnects all clients and cleans up resources.
   * Should be called when the application is shutting down.
   */
  suspend fun shutdown() {
    KLog.i(TAG, "关闭 McpServiceRegistry。")

    // Disconnect all clients
    connections.values.forEach { connection ->
      try {
        connection.client.disconnect()
      } catch (e: Exception) {
        KLog.e(TAG, "断开 MCP 客户端连接失败。连接：${connection}", e)
      }
    }

    connections.clear()
    serverInfos.clear()

    // Unregister broadcast receiver
    try {
      context.unregisterReceiver(packageDeployEventReceiver)
    } catch (e: Exception) {
      KLog.w(TAG, "Error unregistering package receiver", e)
    }

    // Cancel coroutine scope
    managerScope.cancel()

    KLog.i(TAG, "McpServiceRegistry shutdown completed")
  }

  /**
   * Gets statistics about the current state of the manager.
   */
  fun getStats(): String {
    return "McpServiceRegistry(services=${serverInfos.size}, clients=${connections.size}, discoveryComplete=$isDiscoveryComplete)"
  }
}

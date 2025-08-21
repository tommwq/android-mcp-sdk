package zwdroid.mcp.sdk.server

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.transport.BinderServerTransport

/**
 * Abstract base class for implementing MCP services.
 * 
 * This class provides the foundation for creating Android services that expose
 * MCP tools to remote clients. It handles the service lifecycle, coroutine scope
 * management, and MCP server setup.
 * 
 * Subclasses must implement:
 * - [getImplementation]: Provide service metadata
 * - [getServerOptions]: Configure server capabilities  
 * - [registerTools]: Register available tools
 * 
 * The service automatically handles:
 * - Coroutine scope lifecycle management
 * - MCP server startup/shutdown
 * - Security validation (when implemented)
 * 
 * Example AndroidManifest.xml declaration:
 * ```xml
 * <service
 *     android:name=".WeatherMcpService"
 *     android:enabled="true"
 *     android:exported="true"
 *     android:permission="zwdroid.mcp.sdk.permission.ACCESS_MCP_SERVICES">
 *     <intent-filter>
 *         <action android:name="zwdroid.mcp.sdk.McpServerService" />
 *     </intent-filter>
 *     <meta-data
 *         android:name="mcp.description"
 *         android:value="Weather tools service" />
 *     <meta-data
 *         android:name="mcp.tools"
 *         android:resource="@raw/mcp_tools" />
 * </service>
 * ```
 */
open class McpServerService<T: McpServer>(private val mcpServerClass: Class<T>) : Service() {

  companion object {
    private const val TAG = "McpServerService"

    const val MCP_SERVER_SERVICE_ACTION = "zwdroid.mcp.sdk.McpServerService"
  }

  private lateinit var mcpServer: McpServer
  private lateinit var serviceScope: CoroutineScope

  override fun onCreate() {
    super.onCreate()

    serviceScope = CoroutineScope(SupervisorJob())

    try {
      mcpServer = mcpServerClass.newInstance()
      mcpServer.attachTransport(BinderServerTransport<T>(serviceScope, this))
      mcpServer.register()

      KLog.i(TAG, "创建 MCP 服务成功。服务名：${this::class.simpleName}")
    } catch (e: Exception) {
      KLog.e(TAG, "创建 MCP 服务失败。原因：", e)
      cleanup()
      throw e
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    if (intent?.action != MCP_SERVER_SERVICE_ACTION) {
      KLog.w(TAG, "不支持的 intent action：${intent?.action}")
      return null
    }

    kotlinx.coroutines.runBlocking {
      try {
        mcpServer.start()
        KLog.d(TAG, "启动 MCP 服务成功。")
      } catch (e: Exception) {
        KLog.e(TAG, "启动 MCP 服务失败。", e)
        return@runBlocking null
      }
    }

    return mcpServer.binder
  }

  override fun onUnbind(intent: Intent?): Boolean {
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    cleanup()
    super.onDestroy()
  }

  /**
   * Performs cleanup of resources.
   */
  private fun cleanup() {
    try {
      kotlinx.coroutines.runBlocking {
        try {
          mcpServer.stop()
        } catch (e: Exception) {
          KLog.e(TAG, "停止 MCP 服务失败。", e)
        }
      }

      // Cancel coroutine scope
      serviceScope.cancel()

    } catch (e: Exception) {
      KLog.e(TAG, "清理 MCP 服务失败。", e)
    }
  }

  /**
   * Gets the current MCP server instance.
   * May be null if service is not properly initialized.
   */
  protected fun getMcpServer(): McpServer = mcpServer

  /**
   * Gets the service coroutine scope.
   * May be null if service is not properly initialized.
   */
  protected fun getServiceScope(): CoroutineScope = serviceScope
}

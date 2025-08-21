package zwdroid.mcp.sdk.transport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import zwdroid.mcp.sdk.IJsonRpcService
import zwdroid.mcp.sdk.common.ServiceCommunicationException
import zwdroid.mcp.sdk.common.TransportException
import zwdroid.mcp.sdk.common.KLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zwdroid.mcp.sdk.server.McpServerService

/**
 * Client-side Binder transport implementation for MCP protocol.
 *
 * This transport connects to a remote MCP service via Android's Binder mechanism
 * and handles bidirectional communication using JSON-RPC protocol.
 *
 * @param context Android Context for service binding
 * @param serviceComponent ComponentName of the target MCP service
 * @param scope Coroutine scope injected by the business layer (MCPClientManager)
 */
class BinderClientTransport(
  private val context: Context,
  private val serviceComponent: ComponentName,
  private val scope: CoroutineScope
) : AbstractTransport() {

  companion object {
    private const val TAG = "BinderClientTransport"
  }

  private var connectionJob: Job? = null
  private var sendingJob: Job? = null
  private val messageChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)
  private val initMutex = Mutex()
  private var initialized = false
  private var remoteService: IJsonRpcService? = null
  private val connectionDeferred = CompletableDeferred<IJsonRpcService>()

  /**
   * ServiceConnection implementation to handle service binding lifecycle.
   */
  private val serviceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      KLog.d(TAG, "Service connected: $name")
      remoteService = IJsonRpcService.Stub.asInterface(service)
      if (!connectionDeferred.isCompleted) {
        connectionDeferred.complete(remoteService!!)
      }
    }

    override fun onServiceDisconnected(name: ComponentName) {
      KLog.w(TAG, "Service disconnected: $name")
      remoteService = null
      // Service disconnection means abnormal disconnection, trigger cleanup
      scope.launch { close() }
    }
  }

  override suspend fun start() {
    initMutex.withLock {
      if (initialized) {
        error("BinderClientTransport already started!")
      }
      initialized = true
    }

    KLog.i(TAG, "Starting BinderClientTransport for service: $serviceComponent")

    connectionJob = scope.launch(CoroutineName("binder-client-connection")) {
      val intent = Intent(McpServerService.MCP_SERVER_SERVICE_ACTION).setComponent(serviceComponent)
      val bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

      if (!bindResult) {
        val error = ServiceCommunicationException("Failed to bind service: $serviceComponent")
        KLog.e(TAG, error.message ?: "Unknown error")
        _onError.invoke(error)
        connectionDeferred.completeExceptionally(error)
        return@launch
      }

      KLog.d(TAG, "Service binding initiated for: $serviceComponent")
    }

    sendingJob = scope.launch(CoroutineName("binder-client-sender")) {
      val service = try {
        // Wait for service connection to be established
        connectionDeferred.await()
      } catch (e: Exception) {
        KLog.e(TAG, "Failed to connect to service", e)
        _onError.invoke(e)
        return@launch
      }

      KLog.i(TAG, "Service connection established, starting message processing")

      messageChannel.consumeEach { message ->
        try {
          val requestJson = McpJson.encodeToString(message)
          KLog.d(TAG) { "Sending request: ${requestJson.take(100)}..." }

          val responseJson = service.call(requestJson)
          _onMessage.invoke(McpJson.decodeFromString(responseJson))
        } catch (e: Exception) {
          KLog.e(TAG, "Error sending message to service", e)
          _onError.invoke(ServiceCommunicationException("Failed to send message", e))
        }
      }
    }

    // Wait for service connection to be established before returning
    try {
      connectionDeferred.await()
      KLog.i(TAG, "BinderClientTransport started successfully, service connected")
    } catch (e: Exception) {
      KLog.e(TAG, "Failed to establish service connection during start", e)
      throw e
    }
  }

  override suspend fun send(message: JSONRPCMessage) {
    if (!initialized) {
      val error = IllegalStateException("Transport not started.")
      KLog.e(TAG, error.message ?: "")
      _onError.invoke(error)
      return
    }

    try {
      messageChannel.send(message)
    } catch (e: Exception) {
      KLog.e(TAG, "Error queuing message for sending", e)
      _onError.invoke(TransportException("Failed to queue message", e))
    }
  }

  override suspend fun close() {
    initMutex.withLock {
      if (!initialized) return@withLock
      initialized = false
    }

    KLog.i(TAG, "Closing BinderClientTransport...")

    withContext(NonCancellable) {
      // Stop accepting new send requests
      messageChannel.close()

      // Cancel running jobs
      connectionJob?.cancel()
      sendingJob?.cancel()

      // Unbind from service
      if (remoteService != null) {
        try {
          context.unbindService(serviceConnection)
          KLog.d(TAG, "Service unbound successfully")
        } catch (e: Exception) {
          KLog.w(TAG, "Error unbinding service", e)
        }
        remoteService = null
      }

      // Trigger close callback
      _onClose.invoke()
    }

    KLog.i(TAG, "BinderClientTransport closed successfully")
  }

  /**
   * Checks if the transport is currently connected to the remote service.
   */
  fun isConnected(): Boolean {
    return remoteService != null && connectionDeferred.isCompleted
  }

  /**
   * Gets the component name of the connected service.
   */
  fun getServiceComponent(): ComponentName = serviceComponent
}
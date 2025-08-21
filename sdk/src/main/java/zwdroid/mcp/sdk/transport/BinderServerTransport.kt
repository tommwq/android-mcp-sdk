package zwdroid.mcp.sdk.transport

import android.os.IBinder
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import zwdroid.mcp.sdk.IJsonRpcService
import zwdroid.mcp.sdk.common.TransportException
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.server.McpServerService
import zwdroid.mcp.sdk.server.McpServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.set
import kotlin.reflect.typeOf

/**
 * Server-side Binder transport implementation for MCP protocol.
 *
 * This transport handles incoming Binder calls from clients and converts them
 * to MCP protocol messages. It implements the linkToDeath mechanism to proactively
 * detect client disconnections and clean up resources.
 *
 * The transport also integrates permission validation, delegating access control
 * decisions to the McpService instance.
 *
 * @param scope Coroutine scope injected by the business layer (McpService) for lifecycle management
 * @param mcpServerService The McpService instance for permission validation
 */
class BinderServerTransport<T : McpServer>(
  private val scope: CoroutineScope,
  private val mcpServerService: McpServerService<T>
) : AbstractTransport() {

  companion object {
    private const val TAG = "BinderServerTransport"
  }

  private var sendingJob: Job? = null
  private val initMutex = Mutex()
  private var initialized = false
  private val messageChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)

  private val defers = ConcurrentHashMap<String, CompletableDeferred<String>>()

  // For linkToDeath mechanism to track client death
  private val deathRecipients = ConcurrentHashMap<String, IBinder.DeathRecipient>()

  private val requestExecutor = Executors.newFixedThreadPool(4)

  /**
   * The IBinder instance that will be returned to clients.
   * Implements the AIDL interface for receiving MCP calls.
   */
  val binder: IBinder = object : IJsonRpcService.Stub() {
    override fun call(requestJson: String): String {
      try {
        KLog.d(TAG, "收到 JSON-RPC 请求。请求：${requestJson}。")

        // TODO 安全检查。

        val resultHolder = AtomicReference<String>()
        val latch = CountDownLatch(1)

        val message = McpJson.decodeFromString<JSONRPCMessage>(requestJson)
        val messageJson = McpJson.parseToJsonElement(requestJson).jsonObject
        val requestId = messageJson["id"]?.jsonPrimitive?.content

        KLog.d(TAG, "message ${message::class.java} ${message}") // class io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest JSONRPCRequest
        KLog.d(TAG, "messageJson ${messageJson::class.java} ${messageJson}") // class kotlinx.serialization.json.JsonObject

        // scope.launch {
        //   try {
        //     val message = McpJson.decodeFromString<JSONRPCMessage>(requestJson)
        //     val messageJson = McpJson.parseToJsonElement(requestJson).jsonObject
        //     val requestId = messageJson["id"]?.jsonPrimitive?.content

        //     if (requestId == null) {
        //       resultHolder.set("{\"error\":\"Missing request ID\"}")
        //       return@launch
        //     }

        //     val defer = CompletableDeferred<String>()
        //     defers[requestId] = defer

        //     // 触发实际处理逻辑
        //     _onMessage.invoke(message)

        //     // 等待结果（带超时）
        //     resultHolder.set(
        //       withTimeout(5000) { defer.await() }
        //     )
        //   } catch (e: Exception) {
        //     resultHolder.set("{\"error\":\"${e.message}\"}")
        //   } finally {
        //     latch.countDown() // 释放同步锁
        //   }
        // }

        // // 等待协程完成（设置超时防止永久阻塞）
        // latch.await(10, TimeUnit.SECONDS)

        // 在线程池中处理，不阻塞 Binder 线程
        requestExecutor.submit {
          try {
            val message = McpJson.decodeFromString<JSONRPCMessage>(requestJson)
            val messageJson = McpJson.parseToJsonElement(requestJson).jsonObject
            val requestId = messageJson["id"]?.jsonPrimitive?.content

            if (requestId == null) {
              resultHolder.set("{\"error\":\"Missing request ID\"}")
              return@submit
            }

            // 使用 runBlocking 在后台线程中等待
            val result = runBlocking {
              val defer = CompletableDeferred<String>()
              defers[requestId] = defer
              KLog.d(TAG,"开始处理 $requestId ${defers[requestId]} $defer")

              _onMessage.invoke(message)

              withTimeout(10000) { defer.await() }
            }

            resultHolder.set(result)
          } catch (e: Exception) {
            resultHolder.set("{\"error\":\"${e.message}\"}")
          } finally {
            latch.countDown()
          }
        }

        // 等待后台线程完成（最多10秒）
        latch.await(10, TimeUnit.SECONDS)
        KLog.d(TAG, "发送 JSON-RPC 应答。应答：${resultHolder.get()}。")
        return resultHolder.get() ?: "{\"error\":\"Timeout\"}"

      } catch (e: Exception) {
        KLog.e(TAG, "Error processing MCP call", e)
        // _onError.invoke(TransportException("Failed to process MCP call", e))
        // Try to notify client of the error
        // runCatching {
        //   callback.onFailure("{\"error\": \"${e.message}\"}")
        // }
        return "{\"error\": \"${e.message}\"}"
      }
    }
  }

  override suspend fun start() {
    initMutex.withLock {
      if (initialized) {
        error("BinderServerTransport already started!")
      }
      initialized = true
    }

    KLog.i(TAG, "Starting BinderServerTransport...")

    sendingJob = scope.launch(CoroutineName("binder-server-sender")) {
      messageChannel.consumeEach { message ->
        try {

          // callback.onSuccess(responseJson)

          val responseJson = McpJson.encodeToString(message)
          KLog.d(TAG, "Sending response: ${responseJson}...")
          val messageJson = McpJson.parseToJsonElement(responseJson).jsonObject
          val requestId = messageJson["id"]?.jsonPrimitive?.content
          KLog.d(TAG,"处理完成。 $requestId ${defers[requestId]}")
          defers[requestId]?.complete(responseJson)
        } catch (e: Exception) {
          KLog.e(TAG, "Error sending response to client", e)
          _onError.invoke(TransportException("Failed to send response", e))
        }
      }
    }

    KLog.i(TAG, "BinderServerTransport started successfully")
  }

  override suspend fun send(message: JSONRPCMessage) {
    KLog.d(TAG, "发送 JSON-RPC 消息。")

    // Extract ID safely from the JSON message
    val responseJson = McpJson.encodeToString(message)
    val messageJson = McpJson.parseToJsonElement(responseJson).jsonObject
    val requestId = messageJson["id"]?.jsonPrimitive?.content
    // val callback = requestIdStr?.let { callbackMap.remove(it) }

    // if (callback == null) {
    //   val error = IllegalStateException("No callback found for response with id: $requestIdStr")
    //   KLog.e(TAG, error.message ?: "Unknown error")
    //   _onError.invoke(error)
    //   return
    // }

    // // Unregister death recipient before sending response
    // if (requestIdStr != null) {
    //   unregisterDeathRecipient(requestIdStr, callback)
    // }

    try {
      //defers[requestId]?.complete(responseJson)
      messageChannel.send(message)
    } catch (e: Exception) {
      KLog.e(TAG, "发送 JSON-RPC 消息失败。", e)
      _onError.invoke(TransportException("Failed to prepare response", e))
    }
  }

  override suspend fun close() {
    initMutex.withLock {
      if (!initialized) return@withLock
      initialized = false
    }

    KLog.i(TAG, "Closing BinderServerTransport...")

    withContext(NonCancellable) {
      //writeChannel.close()
      sendingJob?.cancelAndJoin()

      // Clean up all remaining callbacks and death recipients
      //defers.forEach { (id, defer) ->
      //defer.completeExceptionally()
      //}
      defers.clear()
      //deathRecipients.clear()

      // Don't cancel the external scope, it's managed by the injecting component
      _onClose.invoke()
    }

    KLog.i(TAG, "BinderServerTransport closed successfully")
  }
}

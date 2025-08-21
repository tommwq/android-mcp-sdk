package zwdroid.mcp.sdk.demo.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import zwdroid.mcp.sdk.DefaultMcpHost
import zwdroid.mcp.sdk.McpHost
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.common.McpServerInfo
import zwdroid.mcp.sdk.common.McpServerInfoItemType

class MainActivity : AppCompatActivity() {

  private lateinit var mcpHost: McpHost
  private lateinit var locationInput: EditText
  private lateinit var resultText: TextView
  private lateinit var discoverButton: Button
  private lateinit var currentWeatherButton: Button
  private lateinit var forecastButton: Button
  private lateinit var openAIButton: Button

  companion object {
    private const val TAG = "MCPClientDemo"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    setupViews()
    setupMCP()
  }

  private fun setupViews() {
    locationInput = findViewById(R.id.locationInput)
    resultText = findViewById(R.id.resultText)
    discoverButton = findViewById(R.id.discoverButton)
    currentWeatherButton = findViewById(R.id.currentWeatherButton)
    forecastButton = findViewById(R.id.forecastButton)
    openAIButton = findViewById(R.id.openAIButton)

    discoverButton.setOnClickListener {
      discoverServices()
    }

    currentWeatherButton.setOnClickListener {
      queryWeather()
    }

    forecastButton.setOnClickListener {
      forecastWeather()
    }

    openAIButton.setOnClickListener {
      openOpenAIDemo()
    }

    // Set default location
    locationInput.setText("北京")

    // Initial message
    resultText.text = """MCP 客户端演示

点击“发现服务”来找到可用的 MCP 工具。
然后使用天气按钮来调用工具。

或者尝试 OpenAI LLM 演示，体验由 AI 驱动的工具调用功能！""".trimIndent()
  }

  private fun setupMCP() {
    mcpHost = DefaultMcpHost.getInstance(this)
    KLog.isDebugEnabled = true
  }

  private fun discoverServices() {
    lifecycleScope.launch {
      try {
        resultText.text = "开始发现 MCP 服务。"

        val mcpServerInfos = mcpHost.listMcpServers()

        val result = if (mcpServerInfos.isEmpty()) {
          "未发现 MCP 服务。请确保 Weather Server 已安装，且使用相同的签名证书。"
        } else {
          printServerInfoList(mcpServerInfos)
        }

        resultText.text = result

      } catch (e: Exception) {
        KLog.e(TAG, "发现 MCP 服务失败。", e)
        resultText.text = "发现 MCP 服务失败。原因：${e.message}"
      }
    }
  }

  private fun printServerInfoList(mcpServerInfos: List<McpServerInfo>): String {
    var text = """
      已发现 MCP 服务数：${mcpServerInfos.size}。
    """.trimIndent()

    var number = 1;
    for (info in mcpServerInfos) {
      text = text + """
        MCP 服务 $number
        包：${info.packageName}
        类：${info.className}
        工具：
      """.trimIndent()

      for (item in info.items.filter { it.itemType == McpServerInfoItemType.Tool }) {
        text = text + """
          |
          |    方法：${item.methodName}
          |    描述：${item.description}
          |  
        """.trimMargin("|")
      }

      number++
    }

    return text
  }

  private fun queryWeather() {
    val location = locationInput.text.toString().trim()
    if (location.isEmpty()) {
      resultText.text = "请输入城市"
      return
    }

    lifecycleScope.launch {
      try {
        resultText.text = "开始查询 $location 天气。"

        val arguments = buildJsonObject {
          put("location", location)
        }

        val result = mcpHost.callTool(
          "zwdroid.mcp.sdk.demo.server",
          "zwdroid.mcp.sdk.demo.server.WeatherMcpServerService",
          "queryWeather",
          arguments
        ).jsonPrimitive.content

        resultText.text = "当前天气：\n\n$result"

      } catch (e: Exception) {
        KLog.e(TAG, "Weather call failed", e)
        resultText.text = "Weather call failed: ${e.message}\n\nMake sure:\n1. Weather Server app is installed\n2. Both apps are signed with the same certificate\n3. The service is discoverable"
      }
    }
  }

  private fun forecastWeather() {
    val location = locationInput.text.toString().trim()
    if (location.isEmpty()) {
      resultText.text = "请输入城市"
      return
    }

    lifecycleScope.launch {
      try {
        resultText.text = "开始预测 $location 天气。"

        val arguments = buildJsonObject {
          put("location", location)
          put("days", 5)
        }

        val result = mcpHost.callTool(
          "zwdroid.mcp.sdk.demo.server",
          "zwdroid.mcp.sdk.demo.server.WeatherMcpServerService",
          "forecastWeather",
          arguments
        ).jsonPrimitive.content

        resultText.text = "天气预报\n\n$result"

      } catch (e: Exception) {
        KLog.e(TAG, "Forecast call failed", e)
        resultText.text = "Forecast call failed: ${e.message}\n\nMake sure:\n1. Weather Server app is installed\n2. Both apps are signed with the same certificate\n3. The service is discoverable"
      }
    }
  }

  private fun openOpenAIDemo() {
    val intent = Intent(this, OpenAILLMActivity::class.java)
    startActivity(intent)
  }
}

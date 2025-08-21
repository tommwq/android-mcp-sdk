package zwdroid.mcp.sdk.demo.server

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlin.random.Random
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import zwdroid.mcp.sdk.annotation.*
import zwdroid.mcp.sdk.common.KLog
import zwdroid.mcp.sdk.server.McpServer

@McpServerImplementation("WeatherService", "1.0.0")
class WeatherMcpServer : McpServer() {

  companion object {
    private const val TAG = "WeatherMcpServer"
  }

  @McpTool("查询天气")
  suspend fun queryWeather(request: CallToolRequest): CallToolResult {
    KLog.d(TAG, "执行查询天气工具。")

    val location = request.arguments["location"]?.jsonPrimitive?.content
      ?: return CallToolResult(content = listOf(TextContent(text = "错误：缺少必要参数。参数名：location' ")))

    val temperature = Random.nextInt(-10, 40)
    val conditions = listOf("晴朗", "多云", "雨天", "下雪", "局部多云").random()
    val humidity = Random.nextInt(20, 90)
    val windSpeed = Random.nextInt(0, 30)

    val weatherReport = """
    $location 当前天气：
    温度：${temperature}°C
    天气状况：$conditions
    湿度：${humidity}%
    风速：${windSpeed} 公里/小时
        """.trimIndent()

    return CallToolResult(content = listOf(TextContent(text = weatherReport)))
  }

  @McpTool("预测天气")
  suspend fun forecastWeather(request: CallToolRequest): CallToolResult {
    KLog.d(TAG, "执行预测天气工具。")
    val location = request.arguments["location"]?.jsonPrimitive?.content
      ?: return CallToolResult(
        content = listOf(TextContent(text = "Error: The 'location' parameter is required."))
      )

    val daysArg = request.arguments["days"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 3
    val days = daysArg.coerceIn(1, 7)

    // Generate mock forecast data
    val forecast = StringBuilder()
    forecast.append("天气预报。地点：$location ($days 天):\n\n")

    for (day in 1..days) {
      val highTemp = Random.nextInt(15, 40)
      val lowTemp = Random.nextInt(-5, highTemp - 5)
      val conditions = listOf("晴朗", "多云", "雨天", "下雪", "局部多云").random()
      val chanceOfRain = Random.nextInt(0, 100)

      forecast.append("第 $day 天：\n")
      forecast.append("  最高温度：${highTemp}°C，最低温度：${lowTemp}°C\n")
      forecast.append("  天气状况：$conditions\n")
      forecast.append("  降雨概率：${chanceOfRain}%\n\n")
    }

    return CallToolResult(
      content = listOf(TextContent(text = forecast.toString()))
    )
  }
}

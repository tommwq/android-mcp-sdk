package zwdroid.mcp.sdk.demo.server

import android.content.ComponentName
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_main)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    
    setupServiceInfo()
  }
  
  private fun setupServiceInfo() {
    val textView = findViewById<TextView>(R.id.serviceInfoText)
    
    val serviceComponent = ComponentName(this, WeatherMcpServerService::class.java)
    val serviceInfo = """
        天气 MCP 服务演示
        
        本应用提供天气 MCP 服务，向 MCP 客户端开放天气相关工具。
        
        服务详情：
        • 组件：${serviceComponent.flattenToString()}
        • 操作：zwdroid.mcp.sdk.McpServerService
        
        可用工具：
        • queryWeather - 获取指定位置的实时天气信息
        • forecastWeather - 获取多日天气预报
        
        该服务可通过服务发现机制自动被 MCP 客户端识别。
        
        注：此服务生成的天气数据为演示用途的模拟数据。
        """.trimIndent()
    
    textView.text = serviceInfo
  }
}

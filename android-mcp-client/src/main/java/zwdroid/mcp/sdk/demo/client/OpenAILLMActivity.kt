package zwdroid.mcp.sdk.demo.client

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
import zwdroid.mcp.sdk.DefaultMcpHost
import zwdroid.mcp.sdk.McpHost
import zwdroid.mcp.sdk.common.KLog

class OpenAILLMActivity : AppCompatActivity() {
  
  private lateinit var mcpHost: McpHost
  private lateinit var openAIHelper: OpenAILLMHelper
  private lateinit var messageInput: EditText
  private lateinit var resultText: TextView
  private lateinit var sendButton: Button
  private lateinit var weatherAdviceButton: Button
  private lateinit var trendAnalysisButton: Button
  private lateinit var backButton: Button
  
  companion object {
    private const val TAG = "OpenAILLMActivity"
    // TODO: Replace with your actual OpenAI API key
    private const val OPENAI_API_KEY = "b921521e-8a64-45c7-96d3-e0c4a2d4dfd7"
    // Optional: Replace with custom endpoint (e.g., for Azure OpenAI)
    private const val OPENAI_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContentView(R.layout.activity_openai_llm)
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }
    
    setupViews()
    setupLLM()
  }
  
  private fun setupViews() {
    messageInput = findViewById(R.id.messageInput)
    resultText = findViewById(R.id.resultText)
    sendButton = findViewById(R.id.sendButton)
    weatherAdviceButton = findViewById(R.id.weatherAdviceButton)
    trendAnalysisButton = findViewById(R.id.trendAnalysisButton)
    backButton = findViewById(R.id.backButton)
    
    sendButton.setOnClickListener {
      sendMessage()
    }
    
    weatherAdviceButton.setOnClickListener {
      getWeatherAdvice()
    }
    
    trendAnalysisButton.setOnClickListener {
      analyzeTrend()
    }
    
    backButton.setOnClickListener {
      finish()
    }
    
    // Set initial message
    resultText.text = """
            OpenAI + MCP Integration Demo
            
            This demo shows how to use OpenAI's LLM to automatically discover and call MCP tools.
            
            Features:
            • Natural language tool calling
            • Automatic MCP tool discovery
            • Context-aware conversations
            
            Try asking:
            • "What's the weather like in Tokyo?"
            • "Get weather forecast for New York and suggest activities"
            • "Compare weather in London and Paris"
            
            Note: You need to set your OpenAI API key in the code to use this feature.
        """.trimIndent()
  }
  
  private fun setupLLM() {
    mcpHost = DefaultMcpHost.getInstance(this)
    KLog.isDebugEnabled = true
    
    // Check if API key is configured
    if (OPENAI_API_KEY == "your-openai-api-key-here") {
      resultText.append("\n\n⚠️ WARNING: Please configure your OpenAI API key in OpenAILLMActivity.kt")
      sendButton.isEnabled = false
      weatherAdviceButton.isEnabled = false
      trendAnalysisButton.isEnabled = false
    } else {
      openAIHelper = OpenAILLMHelper(mcpHost, OPENAI_API_KEY, OPENAI_BASE_URL)
    }
  }
  
  private fun sendMessage() {
    val message = messageInput.text.toString().trim()
    if (message.isEmpty()) {
      resultText.text = "Please enter a message"
      return
    }
    
    if (!::openAIHelper.isInitialized) {
      resultText.text = "OpenAI API key not configured. Please set OPENAI_API_KEY in the code."
      return
    }
    
    lifecycleScope.launch {
      try {
        resultText.text = "🤔 Thinking and discovering tools...\n\nMessage: $message\n\nProcessing..."
        
        val response = openAIHelper.chatWithMCPTools(message)
        
        resultText.text = """
                    💬 Your Message:
                    $message
                    
                    🤖 AI Response:
                    $response
                    
                    ✨ The AI automatically discovered and used available MCP tools to answer your question!
            """.trimIndent()
        
        // Clear input
        messageInput.text.clear()
        
      } catch (e: Exception) {
        KLog.e(TAG, "Error sending message", e)
        resultText.text = """
                    ❌ Error occurred:
                    ${e.message}
                    
                    Please check:
                    1. OpenAI API key is valid
                    2. Internet connection is available
                    3. MCP services are running
            """.trimIndent()
      }
    }
  }
  
  private fun getWeatherAdvice() {
    if (!::openAIHelper.isInitialized) {
      resultText.text = "OpenAI API key not configured. Please set OPENAI_API_KEY in the code."
      return
    }
    
    lifecycleScope.launch {
      try {
        resultText.text = "🌤️ Getting weather info and advice for San Francisco..."
        
        val response = openAIHelper.getWeatherAdvice("San Francisco, CA")
        
        resultText.text = """
                    🌤️ Weather Advice for San Francisco:
                    
                    $response
                    
                    🔧 MCP Tools Used:
                    The AI automatically called weather MCP tools to get current conditions and provided personalized advice!
            """.trimIndent()
        
      } catch (e: Exception) {
        KLog.e(TAG, "Error getting weather advice", e)
        resultText.text = "Error getting weather advice: ${e.message}"
      }
    }
  }
  
  private fun analyzeTrend() {
    if (!::openAIHelper.isInitialized) {
      resultText.text = "OpenAI API key not configured. Please set OPENAI_API_KEY in the code."
      return
    }
    
    lifecycleScope.launch {
      try {
        resultText.text = "📊 Analyzing weather trends for New York..."
        
        val response = openAIHelper.analyzeWeatherTrend("New York, NY")
        
        resultText.text = """
                    📊 Weather Trend Analysis for New York:
                    
                    $response
                    
                    🎯 Advanced MCP Integration:
                    The AI used multiple MCP tool calls (current weather + forecast) to provide comprehensive analysis and recommendations!
            """.trimIndent()
        
      } catch (e: Exception) {
        KLog.e(TAG, "Error analyzing trend", e)
        resultText.text = "Error analyzing weather trend: ${e.message}"
      }
    }
  }
}

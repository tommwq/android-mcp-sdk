package zwdroid.mcp.sdk.demo.client

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageToolCall
import com.openai.models.chat.completions.ChatCompletionTool
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import zwdroid.mcp.sdk.McpHost
import zwdroid.mcp.sdk.common.McpServerInfo
import zwdroid.mcp.sdk.common.McpServerInfoItem
import zwdroid.mcp.sdk.common.McpServerInfoItemType
import zwdroid.mcp.sdk.common.KLog
import java.util.function.Consumer

/**
 * OpenAI integration helper for MCP tools.
 * This class demonstrates how to integrate OpenAI SDK with Android MCP SDK
 * to enable LLM-driven tool discovery and calling.
 */
class OpenAILLMHelper(
    private val mcpHost: McpHost,
    private val apiKey: String,
    private val baseUrl: String = "https://ark.cn-beijing.volces.com/api/v3"
) {
  companion object {
    private const val TAG = "OpenAILLMHelper"
    private const val DEFAULT_MODEL = "doubao-seed-1-6-250615"
  }

  private val openAIClient: OpenAIClient by lazy {
    OpenAIOkHttpClient.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .build()
  }

  /**
   * Chat with LLM using available MCP tools.
   * The LLM will automatically decide which tools to call based on the user message.
   */
  suspend fun chatWithMCPTools(
      userMessage: String,
      model: String = DEFAULT_MODEL
  ): String = withContext(Dispatchers.IO) {
    ""

    // try {
    //   KLog.i(TAG, "Starting chat with MCP tools integration")

    //   val tools = mcpHost.listMcpTools()
    //   KLog.i(TAG, "Discovered ${tools.size} MCP tools")
      
    //   // 2. Create a mapping from OpenAI function names to MCP qualified names
    //   val functionNameToQualifiedName = mutableMapOf<String, String>()
      
    //   // 3. Create chat completion request with tools
    //   val paramsBuilder = ChatCompletionCreateParams.builder()
    //       .addUserMessage(userMessage)
    //       .model(model)
      
    //   // 4. Convert MCP tools to OpenAI function tools
    //   tools.forEach { mcpTool ->
    //     val functionTool = convertMcpToolToCompletionTool(mcpTool)
    //     paramsBuilder.addTool(functionTool)
    //     // Map OpenAI function name back to MCP qualified name
    //     functionNameToQualifiedName[mcpTool.qualifiedName] = mcpTool.qualifiedName
    //     // KLog.d(TAG, "Added MCP tool: ${mcpTool.functionName}")
    //   }
      
    //   // 5. Initial LLM call
    //   val completion = openAIClient.chat().completions().create(paramsBuilder.build())
      
    //   // 6. Process tool calls if any
    //   val toolCallsToExecute = mutableListOf<ChatCompletionMessageToolCall>()
    //   completion.choices().forEach(Consumer { choice ->
    //                                  val message = choice.message()
    //                                  // Add assistant message to context
    //                                  paramsBuilder.addMessage(message)
                                     
    //                                  // Collect tool calls
    //                                  message.toolCalls().ifPresent { toolCalls ->
    //                                    toolCalls.forEach { toolCall ->
    //                                      toolCall.function()?.let { 
    //                                        toolCallsToExecute.add(toolCall)
    //                                      }
    //                                    }
    //                                  }
    //                                })
      
    //   // 7. Execute MCP tool calls
    //   if (toolCallsToExecute.isNotEmpty()) {
    //     KLog.i(TAG, "Executing ${toolCallsToExecute.size} tool calls")
        
    //     for (toolCall in toolCallsToExecute) {
    //       val function = toolCall.function()
    //       if (function != null) {
    //         try {
    //           KLog.d(TAG, "Calling tool: ${function.name()}")
              
    //           // Parse arguments
    //           val argumentsJson = kotlinx.serialization.json.Json.parseToJsonElement(
    //               function.arguments()
    //           ).jsonObject
              
    //           // Get the MCP qualified name from the mapping
    //           val qualifiedName = functionNameToQualifiedName[function.name()]
    //               ?: throw IllegalArgumentException("Unknown tool: ${function.name()}")
              
    //           // Call MCP tool using qualified name
    //           val result = mcpHost.callTool(qualifiedName, argumentsJson)
              
    //           // Add tool result to context
    //           paramsBuilder.addMessage(
    //               ChatCompletionToolMessageParam.builder()
    //                   .toolCallId(toolCall.id())
    //                   .content(result.toString())
    //                   .build()
    //           )
              
    //           KLog.d(TAG, "Tool ${function.name()} executed successfully")
              
    //         } catch (e: Exception) {
    //           KLog.e(TAG, "Error executing tool ${function.name()}", e)
    //           // Add error message to context
    //           paramsBuilder.addMessage(
    //               ChatCompletionToolMessageParam.builder()
    //                   .toolCallId(toolCall.id())
    //                   .content("Error executing tool: ${e.message}")
    //                   .build()
    //           )
    //         }
    //       }
    //     }
        
    //     // 8. Continue conversation with tool results
    //     val followUpCompletion = openAIClient.chat().completions().create(paramsBuilder.build())
        
    //     // 9. Return final response
    //     val finalResponse = StringBuilder()
    //     followUpCompletion.choices().forEach(Consumer { choice ->
    //                                            choice.message().content().ifPresent { content ->
    //                                              finalResponse.append(content)
    //                                            }
    //                                          })
        
    //     return@withContext finalResponse.toString()
        
    //   } else {
    //     // No tool calls, return direct response
    //     val response = StringBuilder()
    //     completion.choices().forEach(Consumer { choice ->
    //                                    choice.message().content().ifPresent { content ->
    //                                      response.append(content)
    //                                    }
    //                                  })
    //     return@withContext response.toString()
    //   }
      
    // } catch (e: Exception) {
    //   KLog.e(TAG, "Error in chat with MCP tools", e)
    //   return@withContext "Sorry, I encountered an error: ${e.message}"
    // }
  }

  /**
   * Convert MCP tool to OpenAI ChatCompletionTool format
   */
  private fun convertMcpToolToCompletionTool(mcpTool: McpServerInfoItem): ChatCompletionTool {
    // val functionDefinitionBuilder = FunctionDefinition.builder()
    //     .name(mcpTool.qualifiedName)
    //     .description(mcpTool.description ?: "MCP Tool: ${mcpTool.functionName}")
    
    // // Convert parameters schema
    // val parametersBuilder = FunctionParameters.builder()
    
    // // Add parameters from the MCP tool schema
    // val parametersSchema = mcpTool.parametersSchema
    // parametersSchema.entries.forEach { (key, value) ->
    //   when (value) {
    //     is JsonPrimitive -> {
    //       parametersBuilder.putAdditionalProperty(key, JsonValue.from(value.content))
    //     }
    //     is JsonObject -> {
    //       parametersBuilder.putAdditionalProperty(key, jsonElementToJsonValue(value))
    //     }
    //     is JsonArray -> {
    //       parametersBuilder.putAdditionalProperty(key, jsonElementToJsonValue(value))
    //     }
    //   }
    // }
    
    // functionDefinitionBuilder.parameters(parametersBuilder.build())
    
    return ChatCompletionTool.builder()
        // .function(functionDefinitionBuilder.build())
        .build()
  }
  
  /**
   * Convert JsonElement to JsonValue for OpenAI API
   */
  private fun jsonElementToJsonValue(element: JsonElement): JsonValue {
    return when (element) {
      is JsonPrimitive -> {
        when {
          element.isString -> JsonValue.from(element.content)
          else -> JsonValue.from(element.content)
        }
      }
      is JsonObject, is JsonArray -> {
        JsonValue.from(element.toString())
      }
    }
  }
  
  /**
   * Simple weather query example
   */
  suspend fun askWeatherQuestion(location: String): String {
    return chatWithMCPTools("What's the current weather in $location? Please provide a detailed report.")
  }
  
  /**
   * Weather with advice example  
   */
  suspend fun getWeatherAdvice(location: String): String {
    return chatWithMCPTools("Check the current weather in $location and give me clothing recommendations for today.")
  }
  
  /**
   * Multi-step weather analysis
   */
  suspend fun analyzeWeatherTrend(location: String): String {
    return chatWithMCPTools("Get both current weather and forecast for $location, then analyze the weather trend and suggest the best activities for the next few days.")
  }
}


// fun McpServerInfoItem.toCompletionTool(): ChatCompletionTool {
//   val functionBuilder = FunctionDefinition.builder()
//       .name(mcpTool.qualifiedName)
//       .description(mcpTool.description ?: "MCP Tool: ${mcpTool.methodName}")
  
//   // Convert parameters schema
//   val parametersBuilder = FunctionParameters.builder()
  
//   // Add parameters from the MCP tool schema
//   val parametersSchema = mcpTool.parametersSchema
//   parametersSchema.entries.forEach { (key, value) ->
//     when (value) {
//       is JsonPrimitive -> {
//         parametersBuilder.putAdditionalProperty(key, JsonValue.from(value.content))
//       }
//       is JsonObject -> {
//         parametersBuilder.putAdditionalProperty(key, jsonElementToJsonValue(value))
//       }
//       is JsonArray -> {
//         parametersBuilder.putAdditionalProperty(key, jsonElementToJsonValue(value))
//       }
//     }
//   }
  
//   functionBuilder.parameters(parametersBuilder.build())
  
//   return ChatCompletionTool.builder()
//       .function(functionBuilder.build())
//       .build()
// }
  

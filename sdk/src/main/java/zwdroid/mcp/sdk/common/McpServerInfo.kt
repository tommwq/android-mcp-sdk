package zwdroid.mcp.sdk.common

import android.content.ComponentName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpServerInfo(
  val packageName: String,
  val className: String,
  val items: List<McpServerInfoItem> = listOf()
) {
  fun getComponentName() = ComponentName(packageName, className)
}

enum class McpServerInfoItemType {
  Tool, Prompt, Resource
}

@Serializable
data class McpServerInfoItem(
  val itemType: McpServerInfoItemType,
  val packageName: String,
  val className: String,
  val methodName: String,
  val description: String
)
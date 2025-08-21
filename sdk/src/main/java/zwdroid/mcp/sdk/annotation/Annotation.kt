package zwdroid.mcp.sdk.annotation

import kotlin.reflect.KClass

/**
 * MCP 工具注解 - 标记可被模型调用的工具函数
 *
 * @property method 工具名称，在 MCP 协议中唯一标识该工具
 * @property description 工具描述，说明工具的功能和用途
 * @property inputSchema inputSchema定义类名字
 * @property outputSchema outputSchema定义类名字
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpTool(
  val description: String = "",
  val method: String = "",
  val inputSchema: KClass<*> = Void::class,
  val outputSchema: KClass<*> = Void::class
)

/**
 * MCP 提示注解 - 标记可被模型使用的提示模板
 *
 * @property name 提示名称
 * @property description 提示描述
 * @property template 提示模板内容，可以包含变量占位符
 * @property arguments 提示模板参数定义
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpPrompt(
  val name: String,
  val description: String
)


/**
 * MCP 资源注解 - 标记可被模型访问的资源
 *
 * @property name 资源名称
 * @property description 资源描述
 * @property type 资源类型 (默认 file)
 * @property uri 资源标识符或访问路径
 * @property actions 资源操作权限 (默认 ["read"])
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpResource(
  val name: String,
  val description: String
)


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpServerImplementation(
  val name: String,
  val version: String
)


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class McpRequiredParameter()

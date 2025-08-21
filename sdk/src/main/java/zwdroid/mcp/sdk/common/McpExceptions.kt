package zwdroid.mcp.sdk.common

/**
 * Base exception class for all MCP SDK related errors.
 * Provides a clear hierarchy for error handling and type-safe exception catching.
 */
open class McpException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when a requested tool is not found in any discovered service.
 * This can happen if:
 * - The tool name is misspelled
 * - The service providing the tool is not available
 * - The service discovery hasn't found the service yet
 */
class ToolNotFoundException(toolName: String) : McpException("Tool '$toolName' not found.")

/**
 * Thrown when the caller doesn't have permission to access a tool or service.
 * This enforces the security model defined in the MCP SDK.
 */
class PermissionDeniedException(message: String) : McpException(message)

/**
 * Thrown when invalid parameters are provided to a tool call.
 * This includes:
 * - Missing required parameters
 * - Parameters that don't match the expected JSON schema
 * - Type mismatches in parameter values
 */
class InvalidParametersException(details: String) : McpException("Invalid parameters: $details")

/**
 * Thrown when a tool execution fails on the server side.
 * This wraps any runtime errors that occur during tool execution.
 */
class ExecutionFailedException(
    toolName: String,
    cause: Throwable? = null
) : McpException("Tool '$toolName' execution failed", cause)

/**
 * Thrown when there's a communication error with the MCP service.
 * This includes:
 * - Service binding failures
 * - Binder communication errors
 * - Service disconnections during operation
 */
class ServiceCommunicationException(
    message: String,
    cause: Throwable? = null
) : McpException("Service communication error: $message", cause)

/**
 * Thrown when service discovery fails.
 * This can happen due to:
 * - PackageManager query failures
 * - Malformed service metadata
 * - System permission issues
 */
class ServiceDiscoveryException(
    message: String,
    cause: Throwable? = null
) : McpException("Service discovery failed: $message", cause)

/**
 * Thrown when there's an error in the transport layer.
 * This includes serialization/deserialization errors and protocol violations.
 */
class TransportException(
    message: String,
    cause: Throwable? = null
) : McpException("Transport error: $message", cause)

/**
 * Thrown when the SDK is used in an invalid state.
 * For example, trying to use a client that hasn't been initialized.
 */
class InvalidStateException(message: String) : McpException("Invalid state: $message")

/**
 * Thrown when a timeout occurs during an operation.
 * This can happen during service binding or tool execution.
 */
class TimeoutException(
    operation: String,
    timeoutMs: Long
) : McpException("Operation '$operation' timed out after ${timeoutMs}ms")
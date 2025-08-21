package zwdroid.mcp.sdk.common

import android.util.Log

/**
 * KLog is a wrapper around Android's built-in Log class for unified logging throughout the MCP SDK.
 * All logging within the SDK should use this class for consistent formatting and control.
 * 
 * Features:
 * - Consistent tag prefixing with "MCP-"
 * - Debug mode control for production builds
 * - Exception logging support
 * - Lazy evaluation of log messages
 */
object KLog {
    private const val TAG_PREFIX = "McpSdk-"
    
    /**
     * Controls whether debug and verbose logs are printed.
     * Should be set to false in production builds.
     */
    var isDebugEnabled: Boolean = true
    
    /**
     * Log a verbose message.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun v(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.v(TAG_PREFIX + tag, message)
        }
    }
    
    /**
     * Log a verbose message with lazy evaluation.
     * @param tag Used to identify the source of a log message
     * @param messageProvider Lambda that provides the message only if logging is enabled
     */
    fun v(tag: String, messageProvider: () -> String) {
        if (isDebugEnabled) {
            Log.v(TAG_PREFIX + tag, messageProvider())
        }
    }
    
    /**
     * Log a debug message.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d(TAG_PREFIX + tag, message)
        }
    }
    
    /**
     * Log a debug message with lazy evaluation.
     * @param tag Used to identify the source of a log message
     * @param messageProvider Lambda that provides the message only if logging is enabled
     */
    fun d(tag: String, messageProvider: () -> String) {
        if (isDebugEnabled) {
            Log.d(TAG_PREFIX + tag, messageProvider())
        }
    }
    
    /**
     * Log an info message.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun i(tag: String, message: String) {
        Log.i(TAG_PREFIX + tag, message)
    }
    
    /**
     * Log an info message with lazy evaluation.
     * @param tag Used to identify the source of a log message
     * @param messageProvider Lambda that provides the message only if logging is enabled
     */
    fun i(tag: String, messageProvider: () -> String) {
        Log.i(TAG_PREFIX + tag, messageProvider())
    }
    
    /**
     * Log a warning message.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun w(tag: String, message: String) {
        Log.w(TAG_PREFIX + tag, message)
    }
    
    /**
     * Log a warning message with exception.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     * @param throwable Exception to log
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(TAG_PREFIX + tag, message, throwable)
    }
    
    /**
     * Log a warning message with lazy evaluation.
     * @param tag Used to identify the source of a log message
     * @param messageProvider Lambda that provides the message only if logging is enabled
     */
    fun w(tag: String, messageProvider: () -> String) {
        Log.w(TAG_PREFIX + tag, messageProvider())
    }
    
    /**
     * Log an error message.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun e(tag: String, message: String) {
        Log.e(TAG_PREFIX + tag, message)
    }
    
    /**
     * Log an error message with exception.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     * @param throwable Exception to log
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(TAG_PREFIX + tag, message, throwable)
    }
    
    /**
     * Log an error message with lazy evaluation.
     * @param tag Used to identify the source of a log message
     * @param messageProvider Lambda that provides the message only if logging is enabled
     */
    fun e(tag: String, messageProvider: () -> String) {
        Log.e(TAG_PREFIX + tag, messageProvider())
    }
    
    /**
     * Log a "What a Terrible Failure" message.
     * This should only be used for situations that should never happen.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     */
    fun wtf(tag: String, message: String) {
        Log.wtf(TAG_PREFIX + tag, message)
    }
    
    /**
     * Log a "What a Terrible Failure" message with exception.
     * @param tag Used to identify the source of a log message
     * @param message The message to log
     * @param throwable Exception to log
     */
    fun wtf(tag: String, message: String, throwable: Throwable) {
        Log.wtf(TAG_PREFIX + tag, message, throwable)
    }
}
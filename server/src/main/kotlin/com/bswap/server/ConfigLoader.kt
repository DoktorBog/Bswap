package com.bswap.server

import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.File
import java.util.*

object ConfigLoader {
    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)
    
    fun loadOpenAIKey(): String? {
        return try {
            // Try to load from gradle.properties file
            val gradleProps = loadPropertiesFile("gradle.properties")
            gradleProps?.getProperty("openai.api.key")?.takeIf { it != "your-openai-api-key-here" }
                ?: System.getenv("OPENAI_API_KEY") // Fallback to environment variable
                ?: System.getProperty("openai.api.key") // Fallback to system property
        } catch (e: Exception) {
            logger.error("Error loading OpenAI API key", e)
            null
        }
    }
    
    private fun loadPropertiesFile(fileName: String): Properties? {
        return try {
            val props = Properties()
            val file = File(fileName)
            
            if (file.exists()) {
                FileInputStream(file).use { props.load(it) }
                logger.info("Loaded properties from $fileName")
                props
            } else {
                logger.warn("Properties file $fileName not found")
                null
            }
        } catch (e: Exception) {
            logger.error("Error loading properties file $fileName", e)
            null
        }
    }
    
    fun validateConfiguration(): ConfigValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check OpenAI API key
        val openaiKey = loadOpenAIKey()
        if (openaiKey.isNullOrBlank()) {
            errors.add("OpenAI API key not found. Set 'openai.api.key' in gradle.properties or OPENAI_API_KEY environment variable.")
        } else if (openaiKey == "your-openai-api-key-here") {
            errors.add("OpenAI API key placeholder detected. Please set a real API key.")
        }
        
        // Additional configuration checks can be added here
        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.info("Configuration validation passed")
        } else {
            if (errors.isNotEmpty()) {
                logger.error("Configuration errors: ${errors.joinToString("; ")}")
            }
            if (warnings.isNotEmpty()) {
                logger.warn("Configuration warnings: ${warnings.joinToString("; ")}")
            }
        }
        
        return ConfigValidationResult(errors, warnings)
    }
}

data class ConfigValidationResult(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}
package com.bswap.server.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class OpenAIRequest(
    val model: String = "gpt-3.5-turbo",
    val messages: List<OpenAIMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 500
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage? = null
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage,
    val finish_reason: String? = null
)

@Serializable
data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class TokenAnalysis(
    val shouldBuy: Boolean,
    val shouldSell: Boolean,
    val shouldSkip: Boolean,
    val confidence: Double,
    val reasoning: String,
    val riskAssessment: String,
    val priceTarget: Double? = null
)

class OpenAIService(
    private val apiKey: String,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(OpenAIService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun analyzeToken(
        tokenAddress: String,
        currentPrice: Double,
        priceHistory: List<Double>,
        volume: Double,
        volatility: Double,
        marketContext: String = ""
    ): TokenAnalysis? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildTokenAnalysisPrompt(
                    tokenAddress, currentPrice, priceHistory, volume, volatility, marketContext
                )
                
                val response = callOpenAI(prompt)
                parseTokenAnalysis(response)
                
            } catch (e: Exception) {
                logger.error("Error analyzing token with OpenAI", e)
                null
            }
        }
    }
    
    suspend fun validateTokenSafety(
        tokenAddress: String,
        tokenMetadata: Map<String, Any>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildSafetyValidationPrompt(tokenAddress, tokenMetadata)
                val response = callOpenAI(prompt)
                
                // Simple validation - look for "SAFE" or "UNSAFE" in response
                response.contains("SAFE", ignoreCase = true) && 
                !response.contains("UNSAFE", ignoreCase = true) &&
                !response.contains("SCAM", ignoreCase = true) &&
                !response.contains("RUG", ignoreCase = true)
                
            } catch (e: Exception) {
                logger.error("Error validating token safety", e)
                false // Default to unsafe if analysis fails
            }
        }
    }
    
    suspend fun getPriceAnalysis(
        tokenAddress: String,
        currentPrice: Double,
        historicalData: List<Pair<Double, Long>>,
        marketTrends: String = ""
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildPriceAnalysisPrompt(tokenAddress, currentPrice, historicalData, marketTrends)
                callOpenAI(prompt)
            } catch (e: Exception) {
                logger.error("Error getting price analysis", e)
                null
            }
        }
    }
    
    private suspend fun callOpenAI(prompt: String): String {
        val request = OpenAIRequest(
            messages = listOf(
                OpenAIMessage("system", "You are an expert cryptocurrency trader and analyst. Provide clear, actionable trading advice based on technical analysis and market data."),
                OpenAIMessage("user", prompt)
            )
        )
        
        val response: HttpResponse = httpClient.post("https://api.openai.com/v1/chat/completions") {
            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody(request)
        }
        
        if (response.status.isSuccess()) {
            val openAIResponse: OpenAIResponse = response.body()
            return openAIResponse.choices.firstOrNull()?.message?.content ?: ""
        } else {
            val errorBody = response.bodyAsText()
            logger.error("OpenAI API error: ${response.status} - $errorBody")
            throw RuntimeException("OpenAI API error: ${response.status}")
        }
    }
    
    private fun buildTokenAnalysisPrompt(
        tokenAddress: String,
        currentPrice: Double,
        priceHistory: List<Double>,
        volume: Double,
        volatility: Double,
        marketContext: String
    ): String {
        val priceHistoryStr = priceHistory.takeLast(20).joinToString(", ") { "%.6f".format(it) }
        val priceChange = if (priceHistory.size > 1) {
            val change = (currentPrice - priceHistory.first()) / priceHistory.first() * 100
            "%.2f%%".format(change)
        } else "N/A"
        
        return """
        Analyze this cryptocurrency token for trading decision:
        
        Token: $tokenAddress
        Current Price: $currentPrice USD
        Recent Price History: [$priceHistoryStr]
        Price Change: $priceChange
        Volume: $volume
        Volatility: $volatility
        Market Context: $marketContext
        
        Based on this data, provide:
        1. Trading recommendation (BUY/SELL/SKIP)
        2. Confidence level (0-100%)
        3. Risk assessment (LOW/MEDIUM/HIGH)
        4. Brief reasoning (2-3 sentences)
        5. Price target if buying (optional)
        
        Format your response as:
        RECOMMENDATION: [BUY/SELL/SKIP]
        CONFIDENCE: [0-100]%
        RISK: [LOW/MEDIUM/HIGH]
        REASONING: [your analysis]
        PRICE_TARGET: [target price or N/A]
        """.trimIndent()
    }
    
    private fun buildSafetyValidationPrompt(
        tokenAddress: String,
        tokenMetadata: Map<String, Any>
    ): String {
        return """
        Validate the safety of this cryptocurrency token:
        
        Token Address: $tokenAddress
        Metadata: $tokenMetadata
        
        Check for common red flags:
        - Suspicious contract patterns
        - Lack of liquidity
        - Unusual token distribution
        - Missing or questionable metadata
        - Known scam indicators
        
        Respond with either:
        SAFE - if the token appears legitimate
        UNSAFE - if there are significant red flags
        
        Include a brief explanation of your assessment.
        """.trimIndent()
    }
    
    private fun buildPriceAnalysisPrompt(
        tokenAddress: String,
        currentPrice: Double,
        historicalData: List<Pair<Double, Long>>,
        marketTrends: String
    ): String {
        val dataPoints = historicalData.takeLast(10).joinToString("\n") { (price, timestamp) ->
            "Price: $price USD at ${timestamp}"
        }
        
        return """
        Provide detailed price analysis for token $tokenAddress:
        
        Current Price: $currentPrice USD
        Historical Data:
        $dataPoints
        
        Market Trends: $marketTrends
        
        Analyze:
        1. Price momentum and trend direction
        2. Support and resistance levels
        3. Volume patterns
        4. Potential breakout/breakdown scenarios
        5. Short-term price prediction (next 5-15 minutes)
        
        Provide actionable insights for algorithmic trading.
        """.trimIndent()
    }
    
    private fun parseTokenAnalysis(response: String): TokenAnalysis {
        val lines = response.lines()
        var shouldBuy = false
        var shouldSell = false
        var shouldSkip = false
        var confidence = 0.5
        var reasoning = "No analysis available"
        var riskAssessment = "UNKNOWN"
        var priceTarget: Double? = null
        
        for (line in lines) {
            when {
                line.startsWith("RECOMMENDATION:", ignoreCase = true) -> {
                    val rec = line.substringAfter(":").trim().uppercase()
                    shouldBuy = rec.contains("BUY")
                    shouldSell = rec.contains("SELL") && !rec.contains("BUY")
                    shouldSkip = rec.contains("SKIP") || rec.contains("HOLD")
                }
                line.startsWith("CONFIDENCE:", ignoreCase = true) -> {
                    val conf = line.substringAfter(":").replace("%", "").trim()
                    confidence = conf.toDoubleOrNull()?.div(100) ?: 0.5
                }
                line.startsWith("REASONING:", ignoreCase = true) -> {
                    reasoning = line.substringAfter(":").trim()
                }
                line.startsWith("RISK:", ignoreCase = true) -> {
                    riskAssessment = line.substringAfter(":").trim()
                }
                line.startsWith("PRICE_TARGET:", ignoreCase = true) -> {
                    val target = line.substringAfter(":").trim()
                    if (target != "N/A") {
                        priceTarget = target.toDoubleOrNull()
                    }
                }
            }
        }
        
        return TokenAnalysis(
            shouldBuy = shouldBuy,
            shouldSell = shouldSell,
            shouldSkip = shouldSkip,
            confidence = confidence,
            reasoning = reasoning,
            riskAssessment = riskAssessment,
            priceTarget = priceTarget
        )
    }
}
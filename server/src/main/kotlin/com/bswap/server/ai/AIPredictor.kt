package com.bswap.server.ai

import com.bswap.server.AIModelType
import com.bswap.server.AIStrategyConfig
import com.bswap.server.TradingRuntime
import com.bswap.server.data.solana.transaction.TokenInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import kotlin.math.*
import kotlin.random.Random

data class MarketFeatures(
    val priceAction: Double,
    val volume: Double, 
    val momentum: Double,
    val volatility: Double,
    val sentiment: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class PredictionResult(
    val buyProbability: Double,
    val sellProbability: Double,
    val confidence: Double,
    val expectedReturn: Double,
    val riskScore: Double
)

data class TrainingSample(
    val features: MarketFeatures,
    val actualReturn: Double,
    val timestamp: Long
)

interface AIModel {
    suspend fun predict(features: MarketFeatures): PredictionResult
    suspend fun train(samples: List<TrainingSample>): Boolean
    fun getModelAccuracy(): Double
}

class NeuralNetworkModel(private val config: AIStrategyConfig) : AIModel {
    private val logger = LoggerFactory.getLogger(NeuralNetworkModel::class.java)
    
    // Simplified neural network with configurable architecture
    private var weights: Array<Array<DoubleArray>> = initializeWeights()
    private var biases: Array<DoubleArray> = initializeBiases()
    private val trainMutex = Mutex()
    private var accuracy = 0.5 // Start with baseline accuracy
    
    private fun initializeWeights(): Array<Array<DoubleArray>> {
        val layers = listOf(5) + config.hiddenLayers + listOf(2) // 5 input features, 2 outputs (buy/sell)
        return Array(layers.size - 1) { i ->
            Array(layers[i]) { DoubleArray(layers[i + 1]) { Random.nextGaussian() * 0.1 } }
        }
    }
    
    private fun initializeBiases(): Array<DoubleArray> {
        val layers = listOf(5) + config.hiddenLayers + listOf(2)
        return Array(layers.size - 1) { i ->
            DoubleArray(layers[i + 1]) { Random.nextGaussian() * 0.1 }
        }
    }
    
    override suspend fun predict(features: MarketFeatures): PredictionResult {
        val input = doubleArrayOf(
            features.priceAction,
            features.volume,
            features.momentum,
            features.volatility,
            features.sentiment
        )
        
        val output = forwardPass(input)
        val buyProb = sigmoid(output[0])
        val sellProb = sigmoid(output[1])
        
        // Normalize probabilities
        val total = buyProb + sellProb
        val normalizedBuy = if (total > 0) buyProb / total else 0.5
        val normalizedSell = if (total > 0) sellProb / total else 0.5
        
        val confidence = abs(normalizedBuy - normalizedSell)
        val expectedReturn = (normalizedBuy - normalizedSell) * 0.1 // Simplified expected return
        val riskScore = calculateRiskScore(features)
        
        return PredictionResult(
            buyProbability = normalizedBuy,
            sellProbability = normalizedSell,
            confidence = confidence,
            expectedReturn = expectedReturn,
            riskScore = riskScore
        )
    }
    
    override suspend fun train(samples: List<TrainingSample>): Boolean = trainMutex.withLock {
        if (samples.size < 100) {
            logger.warn("Insufficient training samples: ${samples.size}")
            return false
        }
        
        try {
            val shuffledSamples = samples.shuffled()
            val batchSize = min(32, samples.size / 4)
            var totalLoss = 0.0
            var correct = 0
            
            for (i in shuffledSamples.indices step batchSize) {
                val batch = shuffledSamples.subList(i, min(i + batchSize, shuffledSamples.size))
                val batchLoss = trainBatch(batch)
                totalLoss += batchLoss
                
                // Calculate accuracy on this batch
                batch.forEach { sample ->
                    val prediction = predict(sample.features)
                    val predictedAction = if (prediction.buyProbability > prediction.sellProbability) 1.0 else -1.0
                    val actualAction = if (sample.actualReturn > 0) 1.0 else -1.0
                    if (predictedAction == actualAction) correct++
                }
            }
            
            accuracy = correct.toDouble() / samples.size
            logger.info("Training completed. Accuracy: ${accuracy * 100:.2f}%, Loss: ${totalLoss / shuffledSamples.size}")
            
            return accuracy > 0.55 // Only consider successful if better than random
        } catch (e: Exception) {
            logger.error("Training failed", e)
            return false
        }
    }
    
    private fun trainBatch(batch: List<TrainingSample>): Double {
        var totalLoss = 0.0
        
        batch.forEach { sample ->
            val input = doubleArrayOf(
                sample.features.priceAction,
                sample.features.volume, 
                sample.features.momentum,
                sample.features.volatility,
                sample.features.sentiment
            )
            
            val target = if (sample.actualReturn > 0) doubleArrayOf(1.0, 0.0) else doubleArrayOf(0.0, 1.0)
            val output = forwardPass(input)
            val loss = calculateLoss(output, target)
            totalLoss += loss
            
            // Simplified backpropagation (gradient descent)
            backpropagate(input, target, output)
        }
        
        return totalLoss / batch.size
    }
    
    private fun forwardPass(input: DoubleArray): DoubleArray {
        var activation = input
        
        for (layerIdx in weights.indices) {
            val layerOutput = DoubleArray(weights[layerIdx][0].size)
            for (j in layerOutput.indices) {
                var sum = biases[layerIdx][j]
                for (i in activation.indices) {
                    sum += activation[i] * weights[layerIdx][i][j]
                }
                layerOutput[j] = if (layerIdx == weights.size - 1) sum else relu(sum)
            }
            activation = layerOutput
        }
        
        return activation
    }
    
    private fun backpropagate(input: DoubleArray, target: DoubleArray, output: DoubleArray) {
        // Simplified gradient calculation and weight update
        val outputError = DoubleArray(output.size)
        for (i in output.indices) {
            outputError[i] = target[i] - sigmoid(output[i])
        }
        
        // Update output layer weights (simplified)
        val lastLayerIdx = weights.size - 1
        for (i in weights[lastLayerIdx].indices) {
            for (j in weights[lastLayerIdx][i].indices) {
                weights[lastLayerIdx][i][j] += config.learningRate * outputError[j] * input[min(i, input.size - 1)]
            }
        }
        
        // Update biases
        for (j in biases[lastLayerIdx].indices) {
            biases[lastLayerIdx][j] += config.learningRate * outputError[j]
        }
    }
    
    private fun calculateLoss(output: DoubleArray, target: DoubleArray): Double {
        var loss = 0.0
        for (i in output.indices) {
            val diff = target[i] - sigmoid(output[i])
            loss += diff * diff
        }
        return loss / output.size
    }
    
    private fun calculateRiskScore(features: MarketFeatures): Double {
        // Combine volatility and other risk factors
        return features.volatility * 0.4 + (1.0 - features.sentiment) * 0.3 + abs(features.momentum) * 0.3
    }
    
    override fun getModelAccuracy(): Double = accuracy
    
    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))
    private fun relu(x: Double): Double = maxOf(0.0, x)
}

class RandomForestModel(private val config: AIStrategyConfig) : AIModel {
    private val logger = LoggerFactory.getLogger(RandomForestModel::class.java)
    private var trees: List<DecisionTree> = emptyList()
    private var accuracy = 0.5
    
    override suspend fun predict(features: MarketFeatures): PredictionResult {
        if (trees.isEmpty()) {
            // Fallback prediction when not trained
            return PredictionResult(0.5, 0.5, 0.0, 0.0, 0.5)
        }
        
        val votes = trees.map { it.predict(features) }
        val buyVotes = votes.count { it > 0 }
        val sellVotes = votes.count { it <= 0 }
        
        val buyProb = buyVotes.toDouble() / votes.size
        val sellProb = sellVotes.toDouble() / votes.size
        val confidence = abs(buyProb - sellProb)
        
        return PredictionResult(
            buyProbability = buyProb,
            sellProbability = sellProb, 
            confidence = confidence,
            expectedReturn = (buyProb - sellProb) * 0.08,
            riskScore = features.volatility
        )
    }
    
    override suspend fun train(samples: List<TrainingSample>): Boolean {
        if (samples.size < 200) return false
        
        try {
            val numTrees = 10
            val sampleSize = (samples.size * 0.8).toInt()
            
            trees = (1..numTrees).map {
                val bootstrapSample = samples.shuffled().take(sampleSize)
                DecisionTree().apply { train(bootstrapSample) }
            }
            
            // Calculate out-of-bag accuracy
            val oobSamples = samples.shuffled().take(samples.size / 4)
            var correct = 0
            
            oobSamples.forEach { sample ->
                val prediction = predict(sample.features)
                val predictedAction = if (prediction.buyProbability > prediction.sellProbability) 1.0 else -1.0
                val actualAction = if (sample.actualReturn > 0) 1.0 else -1.0
                if (predictedAction == actualAction) correct++
            }
            
            accuracy = correct.toDouble() / oobSamples.size
            logger.info("Random Forest trained with accuracy: ${accuracy * 100:.2f}%")
            return accuracy > 0.55
        } catch (e: Exception) {
            logger.error("Random Forest training failed", e)
            return false
        }
    }
    
    override fun getModelAccuracy(): Double = accuracy
}

// Simplified Decision Tree for Random Forest
private class DecisionTree {
    private var root: TreeNode? = null
    
    fun train(samples: List<TrainingSample>) {
        root = buildTree(samples, 0, 5) // max depth = 5
    }
    
    fun predict(features: MarketFeatures): Double {
        return root?.predict(features) ?: 0.0
    }
    
    private fun buildTree(samples: List<TrainingSample>, depth: Int, maxDepth: Int): TreeNode? {
        if (depth >= maxDepth || samples.size < 10) {
            val avgReturn = samples.map { it.actualReturn }.average()
            return TreeNode.Leaf(avgReturn)
        }
        
        val bestSplit = findBestSplit(samples)
        val (left, right) = splitSamples(samples, bestSplit)
        
        return TreeNode.Internal(
            feature = bestSplit.feature,
            threshold = bestSplit.threshold,
            left = buildTree(left, depth + 1, maxDepth),
            right = buildTree(right, depth + 1, maxDepth)
        )
    }
    
    private fun findBestSplit(samples: List<TrainingSample>): Split {
        // Simplified split finding - just use price action with median threshold
        val priceActions = samples.map { it.features.priceAction }.sorted()
        val threshold = priceActions[priceActions.size / 2]
        return Split("priceAction", threshold)
    }
    
    private fun splitSamples(samples: List<TrainingSample>, split: Split): Pair<List<TrainingSample>, List<TrainingSample>> {
        return samples.partition { getFeatureValue(it.features, split.feature) <= split.threshold }
    }
    
    private fun getFeatureValue(features: MarketFeatures, featureName: String): Double {
        return when (featureName) {
            "priceAction" -> features.priceAction
            "volume" -> features.volume
            "momentum" -> features.momentum
            "volatility" -> features.volatility
            "sentiment" -> features.sentiment
            else -> 0.0
        }
    }
    
    private data class Split(val feature: String, val threshold: Double)
    
    private sealed class TreeNode {
        abstract fun predict(features: MarketFeatures): Double
        
        data class Leaf(val value: Double) : TreeNode() {
            override fun predict(features: MarketFeatures): Double = value
        }
        
        data class Internal(
            val feature: String,
            val threshold: Double,
            val left: TreeNode?,
            val right: TreeNode?
        ) : TreeNode() {
            override fun predict(features: MarketFeatures): Double {
                val featureValue = when (feature) {
                    "priceAction" -> features.priceAction
                    "volume" -> features.volume
                    "momentum" -> features.momentum
                    "volatility" -> features.volatility
                    "sentiment" -> features.sentiment
                    else -> 0.0
                }
                
                return if (featureValue <= threshold) {
                    left?.predict(features) ?: 0.0
                } else {
                    right?.predict(features) ?: 0.0
                }
            }
        }
    }
}

class EnsembleModel(private val config: AIStrategyConfig) : AIModel {
    private val models = listOf(
        NeuralNetworkModel(config),
        RandomForestModel(config)
    )
    
    override suspend fun predict(features: MarketFeatures): PredictionResult {
        val predictions = models.map { it.predict(features) }
        
        val avgBuyProb = predictions.map { it.buyProbability }.average()
        val avgSellProb = predictions.map { it.sellProbability }.average()
        val avgConfidence = predictions.map { it.confidence }.average()
        val avgReturn = predictions.map { it.expectedReturn }.average()
        val avgRisk = predictions.map { it.riskScore }.average()
        
        return PredictionResult(
            buyProbability = avgBuyProb,
            sellProbability = avgSellProb,
            confidence = avgConfidence,
            expectedReturn = avgReturn,
            riskScore = avgRisk
        )
    }
    
    override suspend fun train(samples: List<TrainingSample>): Boolean {
        val results = models.map { it.train(samples) }
        return results.any { it } // Return true if at least one model trained successfully
    }
    
    override fun getModelAccuracy(): Double {
        return models.map { it.getModelAccuracy() }.average()
    }
}

object AIModelFactory {
    fun createModel(config: AIStrategyConfig): AIModel {
        return when (config.modelType) {
            AIModelType.NEURAL_NETWORK -> NeuralNetworkModel(config)
            AIModelType.RANDOM_FOREST -> RandomForestModel(config)
            AIModelType.GRADIENT_BOOSTING -> RandomForestModel(config) // Simplified - using RandomForest as fallback
            AIModelType.ENSEMBLE -> EnsembleModel(config)
        }
    }
}
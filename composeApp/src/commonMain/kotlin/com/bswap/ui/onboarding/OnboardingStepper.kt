package com.bswap.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bswap.ui.UiTheme

/**
 * Simple horizontal stepper indicator for onboarding flow.
 *
 * @param currentStep current step index starting from 0.
 * @param modifier Modifier for styling and test tag.
 */
@Composable
fun OnboardingStepper(
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val steps = listOf("Welcome", "Setup", "Ready")
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("OnboardingStepper")
    ) {
        steps.forEachIndexed { index, label ->
            StepItem(
                text = label,
                active = index <= currentStep
            )
            if (index != steps.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepItem(text: String, active: Boolean, size: Dp = 16.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = Modifier
                .padding(end = 4.dp)
                .height(size)
        ) {
            val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            drawCircle(color = Color.Transparent, radius = size.toPx() / 2, style = Stroke(width = 2.dp.toPx(), miter = 0f))
            drawCircle(color = color, radius = size.toPx() / 4)
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(name = "Stepper", device = "id:pixel_4", showBackground = true)
@Composable
private fun OnboardingStepperPreview() {
    UiTheme {
        OnboardingStepper(currentStep = 1)
    }
}

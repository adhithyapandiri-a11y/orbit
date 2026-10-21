package com.orbit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.core.ui.component.OrbitButton
import com.orbit.core.ui.component.OrbitCard
import com.orbit.core.ui.component.OrbitOutlinedButton
import com.orbit.core.ui.theme.OrbitBackground
import com.orbit.core.ui.theme.OrbitCardBorder
import com.orbit.core.ui.theme.OrbitCardDark
import com.orbit.core.ui.theme.OrbitCardElevated
import com.orbit.core.ui.theme.OrbitOrange
import com.orbit.core.ui.theme.OrbitTextPrimary
import com.orbit.core.ui.theme.OrbitTextSecondary

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            onFinished()
        }
    }

    val currentStep = state.steps[state.currentStepIndex]
    val isLastStep = state.currentStepIndex == state.steps.lastIndex

    Scaffold(
        containerColor = OrbitBackground,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Step Indicator dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.steps.indices.forEach { index ->
                        val isActive = index == state.currentStepIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isActive) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) OrbitOrange else OrbitCardBorder)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.currentStepIndex > 0) {
                        OrbitOutlinedButton(
                            text = "Back",
                            onClick = { viewModel.previousStep() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OrbitButton(
                        text = if (isLastStep) "Get Started" else "Next",
                        onClick = { viewModel.nextStep() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon hero
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(OrbitCardDark)
                    .border(1.dp, OrbitOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (state.currentStepIndex) {
                        0 -> Icons.Default.Lock
                        1 -> Icons.Default.Speed
                        else -> Icons.Default.Shield
                    },
                    contentDescription = null,
                    tint = OrbitOrange,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OrbitOrange.copy(alpha = 0.15f))
                    .border(1.dp, OrbitOrange.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = currentStep.badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = OrbitOrange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentStep.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OrbitTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentStep.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = OrbitOrange,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            OrbitCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = currentStep.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OrbitTextSecondary,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
                    )
                }
            }
        }
    }
}

package dev.chalicechore.channelsnap.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Spot illustration: a large tonal disc holding [icon], with a smaller [badge] disc
 * overlapping its lower-right edge. Drawn in Compose, so it follows dynamic colour.
 */
@Composable
fun SpotIllustration(
    icon: ImageVector,
    badge: ImageVector?,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    badgeContainer: Color = MaterialTheme.colorScheme.tertiaryContainer,
    badgeContent: Color = MaterialTheme.colorScheme.onTertiaryContainer,
) {
    Box(Modifier.size(148.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(132.dp)
                .clip(CircleShape)
                .background(container.copy(alpha = 0.35f)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(48.dp))
        }
        if (badge != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-14).dp, y = (-14).dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(badgeContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(badge, contentDescription = null, tint = badgeContent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Centered illustration + title + body + optional actions, scrollable on short screens. */
@Composable
fun MessageState(
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        illustration()
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
    }
}

/** Loading state: the current step, a progress bar, and a pulsing skeleton of the list to come. */
@Composable
fun SkeletonListLoading(step: String, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.fillMaxSize()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            step,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Column(Modifier.alpha(pulse)) {
            repeat(4) { group ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(block))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Box(Modifier.width((110 + group * 23 % 60).dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(block))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(170.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(block))
                    }
                }
                repeat(1 + group % 2) { row ->
                    Box(
                        Modifier
                            .padding(start = 70.dp, top = 8.dp, bottom = 8.dp)
                            .width((150 + row * 40 + group * 11).dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(block),
                    )
                }
            }
        }
    }
}

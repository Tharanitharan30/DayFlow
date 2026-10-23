package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.AlarmService
import com.example.ui.theme.DayFlowTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen, bypass keyguard, keep screen on while alarm is ringing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val reminderId = intent?.getLongExtra(AlarmService.EXTRA_REMINDER_ID, 0L) ?: 0L
        val title = intent?.getStringExtra(AlarmService.EXTRA_TITLE).orEmpty().ifBlank {
            AlarmService.activeTitle.ifBlank { "Task Alarm" }
        }
        val description = intent?.getStringExtra(AlarmService.EXTRA_DESCRIPTION).orEmpty().ifBlank {
            AlarmService.activeDescription
        }
        val snoozeMinutes = intent?.getIntExtra(AlarmService.EXTRA_SNOOZE_MINUTES, 10) ?: AlarmService.activeSnoozeMinutes

        setContent {
            DayFlowTheme {
                AlarmRingingScreen(
                    title = title,
                    description = description,
                    snoozeMinutes = snoozeMinutes,
                    onDismiss = {
                        val stopIntent = Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_STOP_ALARM
                        }
                        startService(stopIntent)
                        finish()
                    },
                    onSnooze = {
                        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
                            action = AlarmService.ACTION_SNOOZE_ALARM
                            putExtra(AlarmService.EXTRA_REMINDER_ID, reminderId)
                            putExtra(AlarmService.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                        }
                        startService(snoozeIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmRingingScreen(
    title: String,
    description: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    // Back gesture defaults to Snooze to prevent accidental silent dismissals
    BackHandler(onBack = onSnooze)

    var currentTimeStr by remember { mutableStateOf(formatCurrentTime()) }
    var currentSecondsStr by remember { mutableStateOf(formatCurrentSeconds()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = formatCurrentTime()
            currentSecondsStr = formatCurrentSeconds()
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "alarmPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("alarm_ringing_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar / App Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "DayFlow Alarm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 2. Center Content: Large Digital Clock + Pulsing Ring Icon + Task Details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Large Clock Display
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentTimeStr,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentSecondsStr,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Pulsing Ring Animation
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Ringing",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Task Details Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("alarm_task_title")
                        )

                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Bottom Controls: One-Tap Snooze + Slide to Dismiss
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // One-Tap Snooze Button
                Button(
                    onClick = onSnooze,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("alarm_snooze_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Snooze ($snoozeMinutes min)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Interactive Slide-to-Dismiss Track (Prevents accidental dismissal)
                SlideToDismissTrack(
                    onDismiss = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SlideToDismissTrack(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbSize = 54.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
            .testTag("alarm_dismiss_track"),
        contentAlignment = Alignment.CenterStart
    ) {
        val maxOffsetPx = with(density) { (maxWidth - thumbSize - 6.dp).toPx() }

        // Track Text
        Text(
            text = "Slide to Dismiss  >>>",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Draggable Slider Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(x = (offsetX + with(density) { 3.dp.toPx() }).roundToInt(), y = 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = (offsetX + delta).coerceIn(0f, maxOffsetPx)
                        offsetX = newOffset
                    },
                    onDragStopped = {
                        if (offsetX >= maxOffsetPx * 0.75f) {
                            onDismiss()
                        } else {
                            offsetX = 0f
                        }
                    }
                )
                .testTag("alarm_dismiss_slider_thumb"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Slide to Dismiss",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatCurrentTime(): String {
    val formatter = DateTimeFormatter.ofPattern("hh:mm", Locale.US)
    return LocalTime.now().format(formatter)
}

private fun formatCurrentSeconds(): String {
    val formatter = DateTimeFormatter.ofPattern("ss a", Locale.US)
    return LocalTime.now().format(formatter)
}

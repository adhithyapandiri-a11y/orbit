package com.orbit.feature.attention.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.orbit.core.domain.model.CounterAction
import com.orbit.core.domain.model.SessionStatus
import com.orbit.core.domain.repository.ReelCounterRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class ReelCounterOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject
    lateinit var reelCounterRepository: ReelCounterRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    private val _counterState = MutableStateFlow(0)
    private var currentSessionId: String? = null

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "orbit_overlay_channel"

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, ReelCounterOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ReelCounterOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        startInForeground()
        initSessionAndCounter()
        setupOverlayWindow()
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orbit Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Orbit Attention Tracker")
                .setContentText("Floating counter is active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initSessionAndCounter() {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val now = System.currentTimeMillis()

        serviceScope.launch {
            val session = reelCounterRepository.startSession(today, now)
            currentSessionId = session.id

            reelCounterRepository.getEventsForSession(session.id).collect { events ->
                val increments = events.count { it.action == CounterAction.INCREMENT }
                _counterState.value = increments
            }
        }
    }

    private fun setupOverlayWindow() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ReelCounterOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ReelCounterOverlayService)
            setContent {
                val count by _counterState.asStateFlow().collectAsState()

                // Floating Counter Pill (Screen 14 UI design)
                SurfaceOverlayWidget(
                    count = count,
                    onIncrement = {
                        val sessionId = currentSessionId
                        if (sessionId != null) {
                            serviceScope.launch {
                                try {
                                    reelCounterRepository.recordEvent(
                                        sessionId = sessionId,
                                        action = CounterAction.INCREMENT,
                                        timestamp = System.currentTimeMillis()
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    onDismiss = {
                        stopActiveSession()
                        stopSelf()
                    },
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager?.updateViewLayout(this, params)
                    }
                )
            }
        }

        overlayView = composeView
        windowManager?.addView(composeView, params)
    }

    private fun stopActiveSession() {
        val sessionId = currentSessionId ?: return
        currentSessionId = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reelCounterRepository.stopSession(sessionId, System.currentTimeMillis())
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopActiveSession()
        serviceScope.cancel()

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@androidx.compose.runtime.Composable
private fun SurfaceOverlayWidget(
    count: Int,
    onIncrement: () -> Unit,
    onDismiss: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF191919))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Reels",
                tint = Color(0xFFFF793E),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFFF5F2EF)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // (+) Increment Button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF793E))
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Reel",
                    tint = Color(0xFF0B0B0B),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // (✕) Dismiss Button
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF222222))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Overlay",
                    tint = Color(0xFF969696),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

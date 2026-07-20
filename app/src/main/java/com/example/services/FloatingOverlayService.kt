package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.PreferencesManager
import com.example.engine.CrackState
import com.example.engine.CrackerEngine
import com.example.engine.PasswordGenerator

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PreferencesManager
    private val engine = CrackerEngine.getInstance()

    private var inputView: View? = null
    private var goView: View? = null
    private var progressView: View? = null
    private var pillView: View? = null

    private var isMinimized = false

    companion object {
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "cracker_channel"
        
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferencesManager(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        setupOverlays()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeOverlays()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun setupOverlays() {
        if (isMinimized) {
            showPillOverlay()
        } else {
            showInputOverlay()
            showGoOverlay()
            showProgressOverlay()
        }
    }

    private fun removeOverlays() {
        inputView?.let { windowManager.removeView(it) }; inputView = null
        goView?.let { windowManager.removeView(it) }; goView = null
        progressView?.let { windowManager.removeView(it) }; progressView = null
        pillView?.let { windowManager.removeView(it) }; pillView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cracker Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows cracking progress"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Input Tester Running")
                .setContentText("Overlay buttons are active on screen.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Auto Input Tester Running")
                .setContentText("Overlay buttons are active.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    // BUTTON A: INPUT OVERLAY
    @OptIn(ExperimentalFoundationApi::class)
    private fun showInputOverlay() {
        if (inputView != null) return

        val sizeDp = 55
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val savedX = prefs.inputX
        val savedY = prefs.inputY

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) (savedX - sizePx / 2).toInt() else 150
            y = if (savedY >= 0) (savedY - sizePx / 2).toInt() else 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
        }

        composeView.setContent {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x994488FF), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val centerX = params.x + sizePx / 2f
                                val centerY = params.y + sizePx / 2f
                                prefs.inputX = centerX
                                prefs.inputY = centerY
                                Toast.makeText(this@FloatingOverlayService, "Input Position Saved", Toast.LENGTH_SHORT).show()
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    .combinedClickable(
                        onLongClick = {
                            Toast.makeText(this@FloatingOverlayService, "Position saved", Toast.LENGTH_SHORT).show()
                        },
                        onClick = {}
                    )
            ) {
                Text(
                    text = "IN",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        try {
            windowManager.addView(composeView, params)
            inputView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // BUTTON B: GO / STOP OVERLAY
    @OptIn(ExperimentalFoundationApi::class)
    private fun showGoOverlay() {
        if (goView != null) return

        val sizeDp = 55
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val savedX = prefs.submitX
        val savedY = prefs.submitY

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX >= 0) (savedX - sizePx / 2).toInt() else 150
            y = if (savedY >= 0) (savedY - sizePx / 2).toInt() else 450
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
        }

        composeView.setContent {
            val crackState by engine.currentState.collectAsState()
            val isTesting = crackState == CrackState.TESTING

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isTesting) Color(0x99FF4444) else Color(0x9944CC44),
                        CircleShape
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val centerX = params.x + sizePx / 2f
                                val centerY = params.y + sizePx / 2f
                                prefs.submitX = centerX
                                prefs.submitY = centerY
                                Toast.makeText(this@FloatingOverlayService, "Submit Position Saved", Toast.LENGTH_SHORT).show()
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    .combinedClickable(
                        onLongClick = {
                            Toast.makeText(this@FloatingOverlayService, "Position saved", Toast.LENGTH_SHORT).show()
                        },
                        onClick = {
                            if (isTesting) {
                                engine.stopCracking(this@FloatingOverlayService)
                            } else {
                                if (AutoClickService.instance == null) {
                                    Toast.makeText(this@FloatingOverlayService, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
                                } else {
                                    engine.startCracking(this@FloatingOverlayService)
                                }
                            }
                        }
                    )
            ) {
                Text(
                    text = if (isTesting) "✕" else "GO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTesting) 20.sp else 15.sp
                )
            }
        }

        try {
            windowManager.addView(composeView, params)
            goView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // FLOATING PROGRESS CARD
    private fun showProgressOverlay() {
        if (progressView != null) return

        val widthDp = 240
        val heightDp = 180
        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()

        val params = WindowManager.LayoutParams(
            widthPx, heightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 400
            y = 150
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
        }

        composeView.setContent {
            val crackState by engine.currentState.collectAsState()
            val currentPassword by engine.currentPassword.collectAsState()
            val testedCount by engine.testedCount.collectAsState()
            val totalCombos by engine.totalCombinations.collectAsState()
            val speed by engine.passwordsPerSecond.collectAsState()
            val elapsedSec by engine.elapsedTimeSec.collectAsState()
            val etaSec by engine.etaSec.collectAsState()

            val percent = if (totalCombos > 0) (testedCount.toFloat() / totalCombos).coerceIn(0f, 1f) else 0f

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xEB212121)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (crackState == CrackState.TESTING) "🔄 Testing..." else "💤 Ready",
                            color = if (crackState == CrackState.TESTING) Color(0xFF44CC44) else Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        IconButton(
                            onClick = {
                                isMinimized = true
                                removeOverlays()
                                setupOverlays()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Minimize",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Current: \"$currentPassword\"",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Speed: ${String.format("%.1f", speed)} p/s",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Tested: $testedCount / $totalCombos",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Time: ${elapsedSec}s  |  ETA: ${if (etaSec > 999999) "Infinite" else "${etaSec}s"}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFF44CC44),
                            trackColor = Color.DarkGray,
                        )
                        Text(
                            text = "${String.format("%.1f", percent * 100)}%",
                            color = Color(0xFF44CC44),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            progressView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // PILL OVERLAY (WHEN MINIMIZED)
    private fun showPillOverlay() {
        if (pillView != null) return

        val sizeDp = 50
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
        }

        composeView.setContent {
            val crackState by engine.currentState.collectAsState()
            val speed by engine.passwordsPerSecond.collectAsState()

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE62E2E2E), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            try {
                                windowManager.updateViewLayout(composeView, params)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    .clickable {
                        isMinimized = false
                        removeOverlays()
                        setupOverlays()
                    }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Expand",
                        tint = if (crackState == CrackState.TESTING) Color(0xFF44CC44) else Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (crackState == CrackState.TESTING) "${speed.toInt()}p" else "EXP",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, params)
            pillView = composeView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

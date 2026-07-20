package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.CrackHistoryItem
import com.example.data.CrackHistoryRepository
import com.example.data.PreferencesManager
import com.example.engine.CrackState
import com.example.engine.CrackerEngine
import com.example.engine.PasswordGenerator
import com.example.services.AutoClickService
import com.example.services.FloatingOverlayService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var repository: CrackHistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        prefs = PreferencesManager(this)
        val db = AppDatabase.getDatabase(this)
        repository = CrackHistoryRepository(db.crackHistoryDao())

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var disclaimerAgreed by remember { mutableStateOf(prefs.disclaimerAgreed) }

                    if (!disclaimerAgreed) {
                        DisclaimerScreen(
                            onAgree = {
                                prefs.disclaimerAgreed = true
                                disclaimerAgreed = true
                            },
                            onExit = { finish() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        MainScreen(
                            prefs = prefs,
                            repository = repository,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger composition reload of system states
    }
}

@Composable
fun DisclaimerScreen(
    onAgree: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(false) }

    val isButtonEnabled = check1 && check2 && check3

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning Logo",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "⚠️ IMPORTANT LEGAL NOTICE",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This tool is for PERSONAL USE ONLY to recover YOUR OWN forgotten passwords on YOUR OWN devices and YOUR OWN accounts.\n\nUnauthorized access to others' devices, networks, or accounts is ILLEGAL and punishable by law.",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Divider(color = Color.DarkGray)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = check1,
                        onCheckedChange = { check1 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I am testing ONLY my own passwords",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = check2,
                        onCheckedChange = { check2 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I have permission to test on this device",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = check3,
                        onCheckedChange = { check3 = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I understand unauthorized use is illegal",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onAgree,
                    enabled = isButtonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        disabledContainerColor = Color(0xFF332D41)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I AGREE - START USING", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onExit) {
                    Text("EXIT APP", color = Color.Red)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    prefs: PreferencesManager,
    repository: CrackHistoryRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = CrackerEngine.getInstance()

    // Screen tab selection state
    var selectedTab by remember { mutableIntStateOf(0) }

    // Live accessibility state
    var isAccessibilityEnabled by remember { mutableStateOf(AutoClickService.instance != null) }
    var isOverlayEnabled by remember { mutableStateOf(FloatingOverlayService.isRunning) }

    // Settings States
    var inputType by remember { mutableStateOf(prefs.inputType) }
    var minLength by remember { mutableIntStateOf(prefs.minLength) }
    var maxLength by remember { mutableIntStateOf(prefs.maxLength) }
    var exactLengthEnabled by remember { mutableStateOf(prefs.exactLengthEnabled) }
    var exactLength by remember { mutableIntStateOf(prefs.exactLength) }
    
    var workerCount by remember { mutableFloatStateOf(prefs.workerCount.toFloat()) }
    var delayMs by remember { mutableFloatStateOf(prefs.delayMs.toFloat()) }
    var ultraFastEnabled by remember { mutableStateOf(prefs.ultraFastEnabled) }

    // Coords check
    var inputX by remember { mutableStateOf(prefs.inputX) }
    var inputY by remember { mutableStateOf(prefs.inputY) }
    var submitX by remember { mutableStateOf(prefs.submitX) }
    var submitY by remember { mutableStateOf(prefs.submitY) }

    // Dialog state
    var showPermissionExplanation by remember { mutableStateOf(false) }

    // Cracker Engine states
    val crackState by engine.currentState.collectAsState()
    val logs by engine.logs.collectAsState()
    val currentPassword by engine.currentPassword.collectAsState()
    val testedCount by engine.testedCount.collectAsState()
    val totalCombos by engine.totalCombinations.collectAsState()
    val speed by engine.passwordsPerSecond.collectAsState()
    val elapsedSec by engine.elapsedTimeSec.collectAsState()
    val etaSec by engine.etaSec.collectAsState()
    val foundPassword by engine.foundPassword.collectAsState()

    // History log list
    val historyItems by repository.allHistory.collectAsState(initial = emptyList())

    // Update permission variables periodically or on user return
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = AutoClickService.instance != null
            isOverlayEnabled = FloatingOverlayService.isRunning
            inputX = prefs.inputX
            inputY = prefs.inputY
            submitX = prefs.submitX
            submitY = prefs.submitY
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // App Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Auto Input Tester",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Personal Password Recovery Tool",
                fontSize = 12.sp,
                color = Color.LightGray
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Warning Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚠️ Use only on YOUR OWN accounts/devices",
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Navigation Tabs (Main, Sandbox, History)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFF6750A4)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Tester", color = if (selectedTab == 0) Color.White else Color.Gray) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Demo Sandbox", color = if (selectedTab == 1) Color.White else Color.Gray) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("History", color = if (selectedTab == 2) Color.White else Color.Gray) }
            )
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> {
                    // TESTER CONFIGURATION VIEW
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Card 1: INPUT TYPE
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Card 1: INPUT TYPE",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    val types = listOf(
                                        "NUMBERS_ONLY" to "Numbers only (0-9)",
                                        "LETTERS_LOWER" to "Letters only (a-z)",
                                        "LETTERS_UPPER" to "Letters (A-Z)",
                                        "LETTERS_MIXED" to "Mixed (a-z, A-Z)",
                                        "NUMBERS_LETTERS" to "Numbers + Letters (0-9, a-z)",
                                        "ALL_CHARS" to "All characters",
                                        "WIFI_WPA2" to "WiFi/WPA2 (8-63 chars)"
                                    )

                                    types.forEach { (typeVal, typeLabel) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            RadioButton(
                                                selected = inputType == typeVal,
                                                onClick = {
                                                    inputType = typeVal
                                                    prefs.inputType = typeVal
                                                },
                                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6750A4))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(typeLabel, color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Card 2: PASSWORD LENGTH
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Card 2: PASSWORD LENGTH",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = exactLengthEnabled,
                                            onCheckedChange = {
                                                exactLengthEnabled = it
                                                prefs.exactLengthEnabled = it
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Exact length mode", color = Color.White, fontSize = 13.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (exactLengthEnabled) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Length:", color = Color.LightGray, fontSize = 13.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (exactLength > 1) {
                                                            exactLength--
                                                            prefs.exactLength = exactLength
                                                        }
                                                    }
                                                ) {
                                                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                                                }
                                                Text("$exactLength", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                                IconButton(
                                                    onClick = {
                                                        if (exactLength < 20) {
                                                            exactLength++
                                                            prefs.exactLength = exactLength
                                                        }
                                                    }
                                                ) {
                                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                                                }
                                            }
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Min Length:", color = Color.LightGray, fontSize = 13.sp)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            if (minLength > 1) {
                                                                minLength--
                                                                prefs.minLength = minLength
                                                            }
                                                        }
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                                                    }
                                                    Text("$minLength", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            if (minLength < maxLength) {
                                                                minLength++
                                                                prefs.minLength = minLength
                                                            }
                                                        }
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Max Length:", color = Color.LightGray, fontSize = 13.sp)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            if (maxLength > minLength) {
                                                                maxLength--
                                                                prefs.maxLength = maxLength
                                                            }
                                                        }
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                                                    }
                                                    Text("$maxLength", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            if (maxLength < 20) {
                                                                maxLength++
                                                                prefs.maxLength = maxLength
                                                            }
                                                        }
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: SPEED SETTINGS
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Card 3: SPEED SETTINGS",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text("Simulated Worker Threads: ${workerCount.toInt()}", color = Color.LightGray, fontSize = 13.sp)
                                    Slider(
                                        value = workerCount,
                                        onValueChange = {
                                            workerCount = it
                                            prefs.workerCount = it.toInt()
                                        },
                                        valueRange = 1f..500f,
                                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF6750A4), thumbColor = Color(0xFF6750A4))
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Ultra Fast Toggle (No delay)", color = Color.White, fontSize = 13.sp)
                                        Switch(
                                            checked = ultraFastEnabled,
                                            onCheckedChange = {
                                                ultraFastEnabled = it
                                                prefs.ultraFastEnabled = it
                                            },
                                            colors = SwitchDefaults.colors(checkedIconColor = Color(0xFF6750A4))
                                        )
                                    }

                                    if (!ultraFastEnabled) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Delay Between Attempts: ${delayMs.toInt()} ms", color = Color.LightGray, fontSize = 13.sp)
                                        Slider(
                                            value = delayMs,
                                            onValueChange = {
                                                delayMs = it
                                                prefs.delayMs = it.toLong()
                                            },
                                            valueRange = 50f..1000f,
                                            colors = SliderDefaults.colors(activeTrackColor = Color(0xFF6750A4), thumbColor = Color(0xFF6750A4))
                                        )
                                    }
                                }
                            }
                        }

                        // Card 4: TARGET SETUP
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Card 4: TARGET SETUP",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            // Check overlay permissions first
                                            if (!Settings.canDrawOverlays(context)) {
                                                showPermissionExplanation = true
                                            } else {
                                                if (isOverlayEnabled) {
                                                    FloatingOverlayService.stop(context)
                                                } else {
                                                    FloatingOverlayService.start(context)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isOverlayEnabled) Color(0xFFD32F2F) else Color(0xFF6750A4)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Default.SettingsInputComponent, contentDescription = "Overlay")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isOverlayEnabled) "⏹️ STOP OVERLAYS" else "🎯 SETUP OVERLAY BUTTONS")
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Status: ${if (isOverlayEnabled) "Overlays Active" else "Overlays Inactive"}",
                                        color = if (isOverlayEnabled) Color(0xFF44CC44) else Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = "Input Coord: " + if (inputX >= 0) "(${inputX.toInt()}, ${inputY.toInt()})" else "Not Set",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Submit Coord: " + if (submitX >= 0) "(${submitX.toInt()}, ${submitY.toInt()})" else "Not Set",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )

                                    if (inputX >= 0 || submitX >= 0) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        TextButton(
                                            onClick = {
                                                prefs.clearCoordinates()
                                                inputX = -1f
                                                inputY = -1f
                                                submitX = -1f
                                                submitY = -1f
                                            }
                                        ) {
                                            Text("Clear Coords", color = Color(0xFFFF5252))
                                        }
                                    }
                                }
                            }
                        }

                        // System Accessibility Check Banner
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (isAccessibilityEnabled) Color(0x2244CC44) else Color(0x22FF5252)),
                                border = BorderStroke(1.dp, if (isAccessibilityEnabled) Color(0xFF44CC44) else Color(0xFFFF5252)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isAccessibilityEnabled) "✓ Accessibility Active" else "⚠️ Accessibility Service OFF",
                                        color = if (isAccessibilityEnabled) Color(0xFF81C784) else Color(0xFFFF8A80),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isAccessibilityEnabled) "The tool has permissions to automate input." else "This app requires Accessibility Service enabled to simulate tapping and typing automatically.",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    if (!isAccessibilityEnabled) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Enable AutoClickService", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Core execution log console
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151515)),
                                border = BorderStroke(1.dp, Color.DarkGray),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Log view", color = Color.LightGray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (crackState == CrackState.TESTING) "CRACKING..." else crackState.name,
                                            color = when (crackState) {
                                                CrackState.TESTING -> Color(0xFF44CC44)
                                                CrackState.SUCCESS -> Color(0xFF4488FF)
                                                else -> Color.Gray
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        reverseLayout = true
                                    ) {
                                        items(logs.reversed()) { logMsg ->
                                            Text(
                                                text = logMsg,
                                                color = if (logMsg.contains("⚠️") || logMsg.contains("Error")) Color(0xFFFF8A80) else if (logMsg.contains("🔓") || logMsg.contains("SUCCESS")) Color(0xFF81C784) else Color.LightGray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                        if (logs.isEmpty()) {
                                            item {
                                                Text(
                                                    text = "Terminal logs will print here...",
                                                    color = Color.DarkGray,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating / Main Screen Start Button
                        item {
                            Button(
                                onClick = {
                                    if (crackState == CrackState.TESTING) {
                                        engine.stopCracking(context)
                                    } else {
                                        engine.startCracking(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (crackState == CrackState.TESTING) Color(0xFFD32F2F) else Color(0xFF44CC44)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = if (crackState == CrackState.TESTING) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Trigger"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (crackState == CrackState.TESTING) "STOP TESTING" else "START AUTOMATION",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // DEMO SANDBOX VIEW
                    var testPassword by remember { mutableStateOf("1234") }
                    var userEnteredText by remember { mutableStateOf("") }
                    var sandboxStatusText by remember { mutableStateOf("Sandbox Locked") }
                    var successTransition by remember { mutableStateOf(false) }

                    // Monitor sandbox entered text
                    LaunchedEffect(userEnteredText) {
                        if (userEnteredText == testPassword) {
                            sandboxStatusText = "🔓 Correct Password! Sandbox Unlocked!"
                            successTransition = true
                        } else if (userEnteredText.isNotEmpty()) {
                            sandboxStatusText = "❌ Incorrect Key \"$userEnteredText\". Try again."
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Interactive Sandbox Target Screen", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Set a password, place overlays here, and trigger 'GO' to watch the tester automate clicking, typing, and solving locally!", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                            border = BorderStroke(2.dp, if (successTransition) Color(0xFF44CC44) else Color(0xFF333333)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (successTransition) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = if (successTransition) Color(0xFF44CC44) else Color(0xFFFF5252),
                                    modifier = Modifier.size(50.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = sandboxStatusText,
                                    color = if (successTransition) Color(0xFF44CC44) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // Sandbox input targets
                                TextField(
                                    value = userEnteredText,
                                    onValueChange = { userEnteredText = it },
                                    label = { Text("📍 INPUT FIELD") },
                                    placeholder = { Text("Click IN here") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF2C2C2C),
                                        unfocusedContainerColor = Color(0xFF222222)
                                    )
                                )

                                Spacer(modifier = Modifier.height(15.dp))

                                Button(
                                    onClick = {
                                        if (userEnteredText == testPassword) {
                                            sandboxStatusText = "🔓 Correct Password! Sandbox Unlocked!"
                                            successTransition = true
                                        } else {
                                            sandboxStatusText = "❌ Wrong Password!"
                                            userEnteredText = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4488FF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🎯 SUBMIT BUTTON")
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sandbox Key:", color = Color.LightGray, fontSize = 12.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextField(
                                            value = testPassword,
                                            onValueChange = {
                                                testPassword = it
                                                userEnteredText = ""
                                                successTransition = false
                                                sandboxStatusText = "Sandbox Locked"
                                            },
                                            singleLine = true,
                                            modifier = Modifier.width(100.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent
                                            )
                                        )
                                        IconButton(onClick = {
                                            userEnteredText = ""
                                            successTransition = false
                                            sandboxStatusText = "Sandbox Locked"
                                        }) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // HISTORY LIST VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cracking History log",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            Row {
                                TextButton(
                                    onClick = {
                                        exportHistory(context, historyItems)
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Export")
                                }

                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.clearAll()
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All", color = Color.Red)
                                }
                            }
                        }

                        if (historyItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "History",
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(50.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No history records yet.", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(historyItems) { item ->
                                    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = item.configName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = if (item.success) "SUCCESS" else "FAILED",
                                                    color = if (item.success) Color(0xFF44CC44) else Color(0xFFFF5252),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Date: $formattedDate", color = Color.Gray, fontSize = 11.sp)
                                            Text("Attempts made: ${item.testedCount}", color = Color.LightGray, fontSize = 11.sp)
                                            Text("Duration: ${item.durationSec} seconds", color = Color.LightGray, fontSize = 11.sp)
                                            if (item.success && !item.foundPassword.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF2C2C2C))
                                                        .padding(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Password Found: \"${item.foundPassword}\"",
                                                            color = Color(0xFF81C784),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("password", item.foundPassword)
                                                                cm.setPrimaryClip(clip)
                                                                Toast.makeText(context, "Password copied!", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Explanation Dialog for OVERLAY DRAW permissions
    if (showPermissionExplanation) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanation = false },
            title = { Text("Overlay Permission Needed") },
            text = { Text("This app needs overlay permission to place click buttons on other apps. This is required for auto-input testing.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanation = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionExplanation = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // SUCCESS DIALOG WITH VIBRATION/RINGTONE FEEDBACK ON DISCOVERY
    if (foundPassword != null && crackState == CrackState.SUCCESS) {
        AlertDialog(
            onDismissRequest = { engine.stopCracking(context) },
            icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF44CC44), modifier = Modifier.size(48.dp)) },
            title = { Text("🔓 PASSWORD FOUND!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .border(1.dp, Color(0xFF44CC44), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = foundPassword ?: "",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81C784),
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Time taken: ${elapsedSec}s", fontSize = 13.sp, color = Color.White)
                    Text("Total attempts: $testedCount", fontSize = 13.sp, color = Color.White)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("password", foundPassword)
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, "Password copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF44CC44))
                ) {
                    Text("📋 COPY")
                }
            },
            dismissButton = {
                TextButton(onClick = { engine.stopCracking(context) }) {
                    Text("✓ OK", color = Color.White)
                }
            }
        )
    }
}

fun exportHistory(context: Context, items: List<com.example.data.CrackHistoryItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "No history to export", Toast.LENGTH_SHORT).show()
        return
    }
    val sb = StringBuilder()
    sb.append("ID,Timestamp,Configuration,Type,TestedAttempts,Duration(s),Result,FoundPassword\n")
    for (item in items) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
        sb.append("${item.id},")
            .append("$dateStr,")
            .append("\"${item.configName}\",")
            .append("${item.inputType},")
            .append("${item.testedCount},")
            .append("${item.durationSec},")
            .append(if (item.success) "SUCCESS" else "FAILED").append(",")
            .append("\"${item.foundPassword ?: ""}\"\n")
    }

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Auto Input Tester History Export")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share Export Log"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

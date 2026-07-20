package com.example.engine

import android.content.Context
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.CrackHistoryItem
import com.example.data.PreferencesManager
import com.example.services.AutoClickService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

enum class CrackState {
    IDLE, TESTING, SUCCESS, STOPPED, TIMEOUT
}

class CrackerEngine private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: CrackerEngine? = null

        fun getInstance(): CrackerEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = CrackerEngine()
                INSTANCE = instance
                instance
            }
        }
    }

    private val _currentState = MutableStateFlow(CrackState.IDLE)
    val currentState: StateFlow<CrackState> = _currentState

    private val _currentPassword = MutableStateFlow("")
    val currentPassword: StateFlow<String> = _currentPassword

    private val _testedCount = MutableStateFlow(0L)
    val testedCount: StateFlow<Long> = _testedCount

    private val _totalCombinations = MutableStateFlow(0L)
    val totalCombinations: StateFlow<Long> = _totalCombinations

    private val _passwordsPerSecond = MutableStateFlow(0.0)
    val passwordsPerSecond: StateFlow<Double> = _passwordsPerSecond

    private val _elapsedTimeSec = MutableStateFlow(0L)
    val elapsedTimeSec: StateFlow<Long> = _elapsedTimeSec

    private val _etaSec = MutableStateFlow(0L)
    val etaSec: StateFlow<Long> = _etaSec

    private val _foundPassword = MutableStateFlow<String?>(null)
    val foundPassword: StateFlow<String?> = _foundPassword

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private var job: Job? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var startTimeMs = 0L

    fun addLog(msg: String) {
        val currentList = _logs.value.toMutableList()
        currentList.add(msg)
        if (currentList.size > 150) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    fun startCracking(context: Context) {
        if (_currentState.value == CrackState.TESTING) return

        val prefs = PreferencesManager(context)
        if (!prefs.isTargetSetup()) {
            addLog("⚠️ Error: Target positions are not set! Setup overlay buttons first.")
            return
        }

        val service = AutoClickService.instance
        if (service == null) {
            addLog("⚠️ Error: Accessibility Service is not active. Please enable Auto Input Tester in Settings.")
            return
        }

        // Initialize progress state
        _currentState.value = CrackState.TESTING
        _currentPassword.value = ""
        _testedCount.value = 0L
        _foundPassword.value = null
        _logs.value = emptyList()
        _elapsedTimeSec.value = 0L
        _etaSec.value = 0L
        _passwordsPerSecond.value = 0.0

        addLog("🚀 Cracking process started...")
        addLog("📋 Type: ${PasswordGenerator.getDisplayName(prefs.inputType)}")
        addLog("📏 Length: ${if (prefs.exactLengthEnabled) prefs.exactLength else "${prefs.minLength} to ${prefs.maxLength}"}")
        addLog("👤 Worker simulated threads: ${prefs.workerCount}")
        addLog("📍 Input Coord: (${prefs.inputX}, ${prefs.inputY})")
        addLog("📍 Submit Coord: (${prefs.submitX}, ${prefs.submitY})")

        val charset = PasswordGenerator.getCharsetForType(prefs.inputType)
        val minLen = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.minLength
        val maxLen = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.maxLength

        val generator = PasswordGenerator(charset, minLen, maxLen)
        _totalCombinations.value = generator.totalCombinations
        addLog("🔢 Total possible combinations: ${generator.totalCombinations}")

        startTimeMs = System.currentTimeMillis()

        // Start stats job
        statsJob = scope.launch {
            while (isActive && _currentState.value == CrackState.TESTING) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
                _elapsedTimeSec.value = elapsed

                val tested = _testedCount.value
                val speed = if (elapsed > 0) tested.toDouble() / elapsed else tested.toDouble()
                _passwordsPerSecond.value = speed

                if (speed > 0) {
                    val remaining = generator.totalCombinations - tested
                    _etaSec.value = (remaining / speed).toLong()
                } else {
                    _etaSec.value = 0L
                }

                // Check safety timeout limit
                if (elapsed >= prefs.safetyTimeoutMins * 60) {
                    withContext(Dispatchers.Main) {
                        timeoutReached(context, tested, elapsed)
                    }
                    break
                }
            }
        }

        // Start main testing execution loop
        job = scope.launch(Dispatchers.Main) {
            val safetyMax = prefs.safetyMaxAttempts
            
            while (isActive && _currentState.value == CrackState.TESTING) {
                val nextPassword = generator.nextPassword()
                if (nextPassword == null) {
                    addLog("🏁 Charset exhausted. All combinations tested.")
                    stopCrackingInternal(CrackState.STOPPED, context)
                    break
                }

                _currentPassword.value = nextPassword
                val count = _testedCount.value + 1
                _testedCount.value = count

                if (count % 50 == 0L || count <= 5) {
                    addLog("⚡ Attempt #$count: Trying password \"$nextPassword\"")
                }

                // 1. Click Input field
                service.clickAt(prefs.inputX, prefs.inputY)
                delay(80)

                // 2. Clear field
                service.clearField()
                delay(40)

                // 3. Type Text
                service.typeText(nextPassword)
                delay(80)

                // 4. Record screen snapshot prior to submitting
                val preSubmitText = service.getScreenTextContent()

                // 5. Click Submit Button
                service.clickAt(prefs.submitX, prefs.submitY)

                // 6. Wait delay
                val currentDelay = if (prefs.ultraFastEnabled) 50L else prefs.delayMs
                delay(currentDelay)

                // 7. Verify Screen Content / Success detection
                val postSubmitText = service.getScreenTextContent()
                val isSuccess = verifySuccess(context, nextPassword, preSubmitText, postSubmitText, prefs.inputType)

                if (isSuccess) {
                    passwordFound(context, nextPassword, count)
                    break
                }

                // Check safety count limit
                if (count >= safetyMax) {
                    addLog("🛑 Safety Limit reached: $safetyMax attempts")
                    timeoutReached(context, count, (System.currentTimeMillis() - startTimeMs) / 1000L)
                    break
                }
            }
        }
    }

    private fun verifySuccess(
        context: Context,
        password: String,
        preText: String,
        postText: String,
        inputType: String
    ): Boolean {
        // Method A: Check for positive connectivity status (specifically for Wifi mode)
        if (inputType == "WIFI_WPA2") {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(activeNetwork)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // If we became connected to WiFi, we succeeded!
                addLog("📶 WiFi connectivity detected! Successful connection.")
                return true
            }
        }

        // Method B: Text heuristic matching on screen
        val successKeywords = listOf(
            "success", "welcome", "dashboard", "logged in", "approved", "valid",
            "connected", "unlocked", "granted", "correct password", "successful"
        )
        val failureKeywords = listOf(
            "incorrect", "wrong", "failed", "invalid", "error", "try again",
            "incorrect password", "wrong password", "mismatch", "unauthorized"
        )

        val postLower = postText.lowercase()
        val preLower = preText.lowercase()

        // If screen state changed dramatically and contains success terms
        val hasSuccessKeyword = successKeywords.any { postLower.contains(it) && !preLower.contains(it) }
        if (hasSuccessKeyword) {
            addLog("🧠 Screen check: Success keyword matched!")
            return true
        }

        // Check if there's text difference and NO error keywords
        if (postText != preText && postText.isNotEmpty()) {
            val hasFailureKeyword = failureKeywords.any { postLower.contains(it) }
            val wasAlreadyFailing = failureKeywords.any { preLower.contains(it) }
            
            // If we moved away from a screen that had failure indicators, or the screen completely changed without new errors
            if (!hasFailureKeyword && wasAlreadyFailing) {
                addLog("🧠 Screen check: Failure message disappeared.")
                return true
            }
        }

        return false
    }

    private fun passwordFound(context: Context, password: String, attempts: Long) {
        _foundPassword.value = password
        _currentState.value = CrackState.SUCCESS
        addLog("🔓 SUCCESS! Password recovered: \"$password\"")
        addLog("🏁 Total Attempts: $attempts in ${_elapsedTimeSec.value}s")

        // Trigger notifications & signals
        triggerAlerts(context)

        // Save to Database
        scope.launch {
            val prefs = PreferencesManager(context)
            val db = AppDatabase.getDatabase(context)
            val item = CrackHistoryItem(
                configName = "Test Setup (${PasswordGenerator.getDisplayName(prefs.inputType)})",
                inputType = prefs.inputType,
                minLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.minLength,
                maxLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.maxLength,
                testedCount = attempts,
                success = true,
                foundPassword = password,
                durationSec = _elapsedTimeSec.value
            )
            db.crackHistoryDao().insertHistory(item)
        }

        cleanJobs()
    }

    private fun timeoutReached(context: Context, attempts: Long, duration: Long) {
        _currentState.value = CrackState.TIMEOUT
        addLog("🛑 Terminated: Safety limit or timeout reached.")

        scope.launch {
            val prefs = PreferencesManager(context)
            val db = AppDatabase.getDatabase(context)
            val item = CrackHistoryItem(
                configName = "Test Setup (${PasswordGenerator.getDisplayName(prefs.inputType)})",
                inputType = prefs.inputType,
                minLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.minLength,
                maxLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.maxLength,
                testedCount = attempts,
                success = false,
                foundPassword = null,
                durationSec = duration
            )
            db.crackHistoryDao().insertHistory(item)
        }

        cleanJobs()
    }

    fun stopCracking(context: Context) {
        if (_currentState.value != CrackState.TESTING) return
        addLog("⏹️ Process stopped by user.")
        stopCrackingInternal(CrackState.STOPPED, context)
    }

    private fun stopCrackingInternal(finalState: CrackState, context: Context) {
        _currentState.value = finalState
        val elapsed = _elapsedTimeSec.value
        val attempts = _testedCount.value

        scope.launch {
            val prefs = PreferencesManager(context)
            val db = AppDatabase.getDatabase(context)
            val item = CrackHistoryItem(
                configName = "Test Setup (${PasswordGenerator.getDisplayName(prefs.inputType)})",
                inputType = prefs.inputType,
                minLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.minLength,
                maxLength = if (prefs.exactLengthEnabled) prefs.exactLength else prefs.maxLength,
                testedCount = attempts,
                success = false,
                foundPassword = null,
                durationSec = elapsed
            )
            db.crackHistoryDao().insertHistory(item)
        }

        cleanJobs()
    }

    private fun cleanJobs() {
        job?.cancel()
        job = null
        statsJob?.cancel()
        statsJob = null
    }

    private fun triggerAlerts(context: Context) {
        // 1. Play Sound
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("CrackerEngine", "Failed to play sound: ${e.message}")
        }

        // 2. Vibrate Device
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 400, 200, 400, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.e("CrackerEngine", "Failed to vibrate: ${e.message}")
        }
    }
}

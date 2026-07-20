package com.example.engine

import android.util.Log

class PasswordGenerator(
    val charset: String,
    val minLength: Int,
    val maxLength: Int
) {
    private var currentIndex = 0L
    val totalCombinations: Long

    init {
        var total = 0L
        for (len in minLength..maxLength) {
            val combos = try {
                val power = Math.pow(charset.length.toDouble(), len.toDouble())
                if (power > Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else power.toLong()
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
            total = if (Long.MAX_VALUE - total < combos) {
                Long.MAX_VALUE
            } else {
                total + combos
            }
        }
        totalCombinations = total
    }

    @Synchronized
    fun nextPassword(): String? {
        if (currentIndex >= totalCombinations) return null
        
        var remaining = currentIndex
        for (len in minLength..maxLength) {
            val combos = try {
                val power = Math.pow(charset.length.toDouble(), len.toDouble())
                if (power > Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else power.toLong()
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
            if (remaining < combos) {
                val pw = indexToPassword(remaining, len)
                currentIndex++
                return pw
            }
            remaining -= combos
        }
        return null
    }

    @Synchronized
    fun reset() {
        currentIndex = 0L
    }

    @Synchronized
    fun getCurrentProgressIndex(): Long {
        return currentIndex
    }

    private fun indexToPassword(index: Long, length: Int): String {
        val base = charset.length.toLong()
        var num = index
        val result = StringBuilder()
        for (i in 0 until length) {
            val remainder = (num % base).toInt()
            result.insert(0, charset[remainder])
            num /= base
        }
        return result.toString()
    }

    companion object {
        fun getCharsetForType(type: String): String {
            return when (type) {
                "NUMBERS_ONLY" -> "0123456789"
                "LETTERS_LOWER" -> "abcdefghijklmnopqrstuvwxyz"
                "LETTERS_UPPER" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                "LETTERS_MIXED" -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                "NUMBERS_LETTERS" -> "0123456789abcdefghijklmnopqrstuvwxyz"
                "ALL_CHARS" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()-_=+"
                "WIFI_WPA2" -> "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                else -> "0123456789"
            }
        }

        fun getDisplayName(type: String): String {
            return when (type) {
                "NUMBERS_ONLY" -> "Numbers only (0-9)"
                "LETTERS_LOWER" -> "Letters only (a-z)"
                "LETTERS_UPPER" -> "Letters (A-Z)"
                "LETTERS_MIXED" -> "Mixed (a-z, A-Z)"
                "NUMBERS_LETTERS" -> "Numbers + Letters (0-9, a-z)"
                "ALL_CHARS" -> "All characters"
                "WIFI_WPA2" -> "WiFi/WPA2 (8-63 chars)"
                else -> "Numbers only (0-9)"
            }
        }
    }
}

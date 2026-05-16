package com.abetworks.abetcrm.util

object PhoneUtils {

    /** Strip all non-digit chars, handle Indian +91 prefix */
    fun normalize(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"           // 9876543210  → 919876543210
            digits.startsWith("0") && digits.length == 11 -> "91${digits.drop(1)}"
            digits.startsWith("91") && digits.length == 12 -> digits
            else -> digits
        }
    }

    /** Human-readable format */
    fun format(raw: String): String {
        val n = normalize(raw)
        return if (n.length == 12 && n.startsWith("91"))
            "+91 ${n.substring(2, 7)} ${n.substring(7)}"
        else raw
    }

    /** WhatsApp deep-link */
    fun waLink(normalizedPhone: String, text: String = "") =
        "https://wa.me/$normalizedPhone${if (text.isNotEmpty()) "?text=${android.net.Uri.encode(text)}" else ""}"

    /** Dial intent string */
    fun dialUri(phone: String) = "tel:${normalize(phone)}"
}

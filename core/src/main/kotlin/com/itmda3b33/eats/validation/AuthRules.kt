package com.itmda3b33.eats.validation

/** Field checks for the sign-up, login and password reset forms. Each returns an error message or null. */
object AuthRules {

    const val MIN_PASSWORD = 8

    private val emailPattern = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun checkEmail(email: String): String? = when {
        email.isBlank() -> "Enter your email."
        !emailPattern.matches(email.trim()) -> "Enter a valid email address."
        else -> null
    }

    fun checkPassword(password: String): String? =
        if (password.length < MIN_PASSWORD) "Use at least $MIN_PASSWORD characters." else null

    fun checkLoginPassword(password: String): String? =
        if (password.isEmpty()) "Enter your password." else null

    fun checkName(name: String, label: String): String? =
        if (name.isBlank()) "Enter your $label." else null

    /** South African mobile numbers: 0821234567 or +27821234567. Spaces are ignored. */
    fun checkMobile(mobile: String): String? {
        val digits = mobile.replace(" ", "")
        return if (Regex("""^(0|\+27)[6-8]\d{8}$""").matches(digits)) null else "Enter a valid South African mobile number."
    }

    fun normaliseMobile(mobile: String): String {
        val digits = mobile.replace(" ", "")
        return if (digits.startsWith("0")) "+27" + digits.drop(1) else digits
    }
}

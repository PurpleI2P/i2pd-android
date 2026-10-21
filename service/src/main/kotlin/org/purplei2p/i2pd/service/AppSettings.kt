package org.purplei2p.i2pd.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Settings of the application itself, not of i2pd (those live in i2pd.conf). */
data class AppSettings(
    val autostartOnBoot: Boolean = false,
    val autoRestart: Boolean = true,
    val gracefulTimeoutMinutes: Int = 10,
    /** Hold a partial wake lock while the router runs, so it keeps working in deep sleep. */
    val keepAwake: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** FLAG_SECURE on the app window: no screenshots, screen recording or thumbnail in recents. */
    val secureScreen: Boolean = true,
    /** The first-run screen was shown. */
    val onboardingDone: Boolean = false,
)

/** Synchronous SharedPreferences storage: boot receiver and service need the values at once. */
class AppSettingsStore private constructor(private val prefs: SharedPreferences) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        prefs.edit {
            putBoolean(KEY_AUTOSTART, updated.autostartOnBoot)
            putBoolean(KEY_AUTO_RESTART, updated.autoRestart)
            putInt(KEY_GRACEFUL_TIMEOUT, updated.gracefulTimeoutMinutes)
            putBoolean(KEY_KEEP_AWAKE, updated.keepAwake)
            putString(KEY_THEME, updated.theme.name)
            putBoolean(KEY_SECURE_SCREEN, updated.secureScreen)
            putBoolean(KEY_ONBOARDING_DONE, updated.onboardingDone)
        }
        _settings.value = updated
    }

    private fun read() = AppSettings(
        autostartOnBoot = prefs.getBoolean(KEY_AUTOSTART, false),
        autoRestart = prefs.getBoolean(KEY_AUTO_RESTART, true),
        gracefulTimeoutMinutes = prefs.getInt(KEY_GRACEFUL_TIMEOUT, 10),
        keepAwake = prefs.getBoolean(KEY_KEEP_AWAKE, false),
        theme = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
        secureScreen = prefs.getBoolean(KEY_SECURE_SCREEN, true),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false),
    )

    companion object {
        private const val KEY_AUTOSTART = "autostart_on_boot"
        private const val KEY_AUTO_RESTART = "auto_restart"
        private const val KEY_GRACEFUL_TIMEOUT = "graceful_timeout_minutes"
        private const val KEY_KEEP_AWAKE = "keep_awake"
        private const val KEY_THEME = "theme"
        private const val KEY_SECURE_SCREEN = "secure_screen"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        val GRACEFUL_TIMEOUT_CHOICES = listOf(1, 5, 10, 15, 30)

        @Volatile
        private var instance: AppSettingsStore? = null

        fun get(context: Context): AppSettingsStore =
            instance ?: synchronized(this) {
                instance ?: AppSettingsStore(
                    context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}

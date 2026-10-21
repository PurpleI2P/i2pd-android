package org.purplei2p.i2pd

import android.app.UiModeManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.purplei2p.i2pd.service.AppSettingsStore
import org.purplei2p.i2pd.service.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = AppSettingsStore.get(this).settings
        when (settings.value.theme) {
            ThemeMode.SYSTEM -> Unit
            ThemeMode.LIGHT -> setTheme(R.style.Theme_I2pd_Light)
            ThemeMode.DARK -> setTheme(R.style.Theme_I2pd_Dark)
        }
        super.onCreate(savedInstanceState)
        applySecureScreen(settings.value.secureScreen) // before the first frame, so recents never get a thumbnail
        lifecycleScope.launch {
            settings.map { it.secureScreen }.distinctUntilChanged().collect(::applySecureScreen)
        }
        lifecycleScope.launch {
            settings.map { it.theme }.distinctUntilChanged().collect(::applyNightMode)
        }
        setContent {
            val current by settings.collectAsState()
            val dark = when (current.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app theme, not only the system one
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            I2pdTheme(dark) {
                AppScaffold()
            }
        }
    }

    /**
     * Android 12+ draws the starting window before onCreate from the system night mode, so a chosen theme
     * is passed to the system too; older versions only get setTheme above.
     */
    private fun applyNightMode(theme: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (theme) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
    }

    /** Compose dialogs and menus inherit the flag from this window (SecureFlagPolicy.Inherit). */
    private fun applySecureScreen(secure: Boolean) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
private fun I2pdTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

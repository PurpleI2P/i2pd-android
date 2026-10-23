package org.purplei2p.i2pd

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/** A component built into the app, with the license text in assets/licenses. */
private class Component(val name: String, val license: String, val asset: String)

private const val APACHE_2 = "Apache License 2.0"
private const val BSD_3 = "BSD 3-Clause License"

private val COMPONENTS = listOf(
    Component("i2pd for Android", BSD_3, "i2pd-android.txt"),
    Component("i2pd", BSD_3, "i2pd.txt"),
    Component("OpenSSL", APACHE_2, "apache-2.0.txt"),
    Component("Boost", "Boost Software License 1.0", "boost.txt"),
    Component("MiniUPnPc", BSD_3, "miniupnpc.txt"),
    // getifaddrs() of bionic for Android 6.0, binary/jni/ifaddrs
    Component("Android bionic: getifaddrs", "BSD 2-Clause License, $APACHE_2", "ifaddrs.txt"),
    Component("Kotlin, kotlinx.coroutines", APACHE_2, "apache-2.0.txt"),
    Component("AndroidX, Jetpack Compose", APACHE_2, "apache-2.0.txt"),
)

const val WEBSITE_URL = "https://i2pd.website"
const val SOURCE_URL = "https://github.com/PurpleI2P/i2pd-android"
const val ISSUES_URL = "$SOURCE_URL/issues"

/** Opens [url] in a browser; nothing happens if there is none. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        // No browser installed
    }
}

/** Components of the app and their licenses; a tap shows the license text. */
@Composable
fun LicensesScreen() {
    var shown by rememberSaveable { mutableStateOf<Int?>(null) }
    shown?.let { index ->
        BackHandler { shown = null }
        LicenseText(COMPONENTS[index])
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        COMPONENTS.forEachIndexed { index, component ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.fillMaxWidth().clickable { shown = index }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(component.name, style = MaterialTheme.typography.bodyLarge)
                Text(component.license, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LicenseText(component: Component) {
    val context = LocalContext.current
    val text = remember(component.asset) {
        reflow(context.assets.open("licenses/${component.asset}").bufferedReader().use { it.readText() })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(component.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(component.license, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp))
    }
}

/**
 * License texts are wrapped by hand at about 80 columns, which breaks badly on a phone: lines of a
 * paragraph are joined, so the text wraps to the screen. A blank line or a list item ("1.", "(a)", "-")
 * starts a new line; indentation is dropped.
 */
internal fun reflow(text: String): String {
    val out = StringBuilder()
    var previousBlank = true
    for (raw in text.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> if (!previousBlank) out.append("\n\n")
            previousBlank || LIST_ITEM.containsMatchIn(line) -> {
                if (!previousBlank) out.append('\n')
                out.append(line)
            }
            else -> out.append(' ').append(line)
        }
        previousBlank = line.isEmpty()
    }
    return out.toString().trim()
}

private val LIST_ITEM = Regex("""^(\d+\.|\([a-z0-9]+\)|[-*•])\s""")

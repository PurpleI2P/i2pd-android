package org.purplei2p.i2pd.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Tracks whether the device has a network and reports changes, debounced, on the main thread.
 * Replaces the CONNECTIVITY_CHANGE manifest receiver, which gets nothing since Android 7.
 */
class NetworkMonitor(context: Context, private val onChanged: (online: Boolean) -> Unit) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val networks = mutableSetOf<Network>() // main thread only
    private var reported: Boolean? = null
    private var registered = false

    private val report = Runnable {
        val online = networks.isNotEmpty()
        if (online != reported) {
            reported = online
            Log.i(TAG, "Network ${if (online) "available" else "lost"}")
            onChanged(online)
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onMain {
            // The default network callback may switch networks without onLost for the old one
            if (Build.VERSION.SDK_INT >= 24) networks.clear()
            networks += network
        }

        override fun onLost(network: Network) = onMain { networks -= network }
    }

    fun start() {
        if (registered) return
        registered = true
        if (connectivity.activeNetwork == null) {
            // No callback comes while there is no network: report offline right away
            handler.post(report)
        }
        if (Build.VERSION.SDK_INT >= 24) {
            connectivity.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivity.registerNetworkCallback(request, callback)
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        handler.removeCallbacks(report)
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Callback was not registered", e)
        }
        networks.clear()
        reported = null
    }

    private fun onMain(block: () -> Unit) {
        handler.post {
            block()
            handler.removeCallbacks(report)
            handler.postDelayed(report, DEBOUNCE_MS)
        }
    }

    private companion object {
        const val TAG = "i2pd-network"
        const val DEBOUNCE_MS = 2000L
    }
}

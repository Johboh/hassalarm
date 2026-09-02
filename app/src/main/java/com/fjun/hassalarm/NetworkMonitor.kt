package com.fjun.hassalarm

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Monitors network availability and triggers an alarm update immediately when
 * network connection is restored after a previous failure.
 */
object NetworkMonitor {

    private var isRegistered = false
    private var appContext: Context? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(LOG_TAG, "Network became available. Triggering update.")
            val context = appContext ?: return
            CoroutineScope(Dispatchers.IO).launch {
                AlarmUpdater.performUpdate(context)
            }
        }
    }

    @Synchronized
    fun startMonitoring(context: Context) {
        if (isRegistered) {
            return
        }
        appContext = context.applicationContext
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            }
            isRegistered = true
            Log.d(LOG_TAG, "Started monitoring network connectivity.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to register network callback: ${e.message}")
        }
    }

    @Synchronized
    fun stopMonitoring(context: Context) {
        if (!isRegistered) {
            return
        }
        val cm = (appContext ?: context).applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try {
            cm?.unregisterNetworkCallback(networkCallback)
            Log.d(LOG_TAG, "Stopped monitoring network connectivity.")
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to unregister network callback: ${e.message}")
        } finally {
            isRegistered = false
        }
    }
}

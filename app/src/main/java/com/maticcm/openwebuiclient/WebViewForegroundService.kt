package com.maticcm.openwebuiclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.ForegroundServiceDefaultBehavior
import android.util.Log

class WebViewForegroundService : Service() {

    companion object {
        private const val TAG = "WebViewForegroundService"
        private const val CHANNEL_ID = "WebViewServiceChannel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_URL = "extra_url"

        fun startService(context: Context, url: String) {
            val intent = Intent(context, WebViewForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
                putExtra(EXTRA_URL, url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WebViewForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var serviceUrl: String? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isNetworkAvailable = true

    inner class LocalBinder : Binder() {
        fun getService(): WebViewForegroundService = this@WebViewForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        setupNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                serviceUrl = intent.getStringExtra(EXTRA_URL)
                Log.d(TAG, "Service started with URL: $serviceUrl")
                startForeground(NOTIFICATION_ID, createNotification())
                monitorNetwork()
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Service stop requested")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebView Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps WebView connection alive"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, WelcomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenWebUI Active")
            .setContentText("Maintaining connection...")
            .setSmallIcon(R.drawable.ic_connect)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(ForegroundServiceDefaultBehavior.BLOCKABLE)
            .build()
    }

    private fun setupNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available")
                isNetworkAvailable = true
                // Notify app to reconnect
                sendBroadcast(Intent("com.maticcm.openwebuiclient.NETWORK_AVAILABLE"))
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Network lost")
                isNetworkAvailable = false
                // Notify app about network loss
                sendBroadcast(Intent("com.maticcm.openwebuiclient.NETWORK_LOST"))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d(TAG, "Network capabilities changed, has internet: $hasInternet")
            }
        }
    }

    private fun monitorNetwork() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(
            networkRequest,
            networkCallback
        )

        // Check initial network status
        val activeNetwork = connectivityManager.activeNetworkInfo
        isNetworkAvailable = activeNetwork?.isConnected == true
        Log.d(TAG, "Initial network status: $isNetworkAvailable")
    }

    fun getCurrentUrl(): String? = serviceUrl

    fun isNetworkConnected(): Boolean = isNetworkAvailable

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }
}

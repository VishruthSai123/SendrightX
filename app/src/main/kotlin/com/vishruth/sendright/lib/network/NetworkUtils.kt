/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vishruth.sendright.lib.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

object NetworkUtils {
    
    /**
     * Check if the device has an active internet connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if the device has strong network connection (with bandwidth check)
     */
    fun isNetworkStrong(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return false
        }
        
        // Check for strong signal - WiFi or good cellular
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Check cellular signal strength
                capabilities.signalStrength > -75 // Good signal threshold
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
    
    /**
     * Show "No Internet Connection" toast
     */
    fun showNoInternetToast(context: Context) {
        try {
            // Ensure toast is shown on main thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // Already on main thread
                Toast.makeText(context, " No Internet Connection. Please Try Again.", Toast.LENGTH_LONG).show()
            } else {
                // Post to main thread
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, " No Internet Connection. Please Try Again.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            // Fallback - log the error
            android.util.Log.w("NetworkUtils", "Failed to show no internet toast: ${e.message}")
        }
    }
    
    /**
     * Check network and show toast if not available
     * @return true if network is available, false otherwise
     */
    fun checkNetworkAndShowToast(context: Context): Boolean {
        return if (isNetworkAvailable(context)) {
            true
        } else {
            showNoInternetToast(context)
            false
        }
    }
    
    /**
     * Monitor network during API call with timeout
     * Checks network every 2 seconds for 10 seconds total
     * @param context Context for network checking
     * @param onNetworkLost Callback when network is lost
     * @return true if network remained available, false if lost
     */
    suspend fun monitorNetworkDuringApiCall(
        context: Context,
        onNetworkLost: () -> Unit = {}
    ): Boolean {
        return try {
            withTimeout(10000) { // 10 second timeout
                repeat(5) { // Check 5 times over 10 seconds
                    delay(2000) // Wait 2 seconds between checks
                    if (!isNetworkAvailable(context)) {
                        onNetworkLost()
                        return@withTimeout false
                    }
                }
                true
            }
        } catch (e: TimeoutCancellationException) {
            // Timeout reached - treat as network issue
            onNetworkLost()
            false
        }
    }
}
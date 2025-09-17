/*
 * SendRight - AI-Enhanced Android Keyboard
 * Built upon FlorisBoard by The FlorisBoard Contributors
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

package com.vishruth.sendright.app.auth

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import org.florisboard.lib.compose.FlorisTextButton
import com.vishruth.key1.user.UserManager
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoading by remember { mutableStateOf(false) }
    
    val userManager = remember { UserManager.getInstance() }
    
    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("AuthScreen", "Google Sign-In result received, result code: ${result.resultCode}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("AuthScreen", "Google account retrieved: ${account?.email}")
            if (account != null) {
                isLoading = true
                scope.launch {
                    val signInResult = userManager.signInWithGoogle(account)
                    isLoading = false
                    
                    if (signInResult.isSuccess) {
                        Toast.makeText(context, "Google sign-in successful!", Toast.LENGTH_SHORT).show()
                        // Navigate back with a small delay to ensure state propagation
                        scope.launch {
                            kotlinx.coroutines.delay(100) // Small delay for state propagation
                            navController.popBackStack()
                        }
                    } else {
                        val errorMessage = signInResult.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e("AuthScreen", "Google sign-in failed: $errorMessage")
                        Toast.makeText(context, "Google sign-in failed: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Log.e("AuthScreen", "Google account is null")
                Toast.makeText(context, "Failed to get Google account", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Log.e("AuthScreen", "Google sign-in failed with ApiException", e)
            val errorMessage = when (e.statusCode) {
                12501 -> "Sign-in was cancelled"
                12502 -> "Network error occurred"
                else -> "Google sign-in failed: ${e.message}"
            }
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App logo or title
        Text(
            text = "Welcome to SendRight",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Sign in with your Google account to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 48.dp),
            textAlign = TextAlign.Center
        )
        
        // Google Sign-In Button
        Button(
            onClick = {
                val googleSignInClient = userManager.getGoogleSignInClient()
                if (googleSignInClient != null) {
                    val signInIntent = googleSignInClient.signInIntent
                    googleSignInLauncher.launch(signInIntent)
                } else {
                    Toast.makeText(context, "Google Sign-In not available", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 16.dp),
            enabled = !isLoading
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Google icon (using a simple "G" for now - you can replace with actual Google icon)
                Text(
                    text = "G",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Terms and Privacy
        Text(
            text = "By continuing, you agree to our Terms of Service and Privacy Policy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}
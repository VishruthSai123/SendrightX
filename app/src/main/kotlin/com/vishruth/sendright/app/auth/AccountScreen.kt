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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vishruth.key1.app.LocalNavController
import com.vishruth.key1.app.Routes
import com.vishruth.key1.user.UserManager
import com.vishruth.key1.user.AuthState
import kotlinx.coroutines.launch
import com.vishruth.key1.lib.compose.FlorisScreen

@Composable
fun AccountScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val userManager = remember { UserManager.getInstance() }
    val authState by userManager.authState.collectAsState()
    val userData by userManager.userData.collectAsState()
    
    var isLoggingOut by remember { mutableStateOf(false) }
    
    Log.d("AccountScreen", "Recomposing - AuthState: $authState, UserData: ${userData?.email}")
    
    // Create local variables to avoid smart cast issues
    val currentUserData = userData
    val displayName = currentUserData?.displayName ?: currentUserData?.email?.substringBefore("@") ?: "User"
    val userEmail = currentUserData?.email
    
    // Initialize UserManager once if needed
    LaunchedEffect(Unit) {
        Log.d("AccountScreen", "Starting initialization if needed")
        userManager.initialize(context)
    }
    
    // Redirect to auth screen if user is not authenticated (but not during loading)
    LaunchedEffect(authState) {
        Log.d("AccountScreen", "Auth state changed: $authState")
        if (authState is AuthState.Unauthenticated) {
            Log.d("AccountScreen", "Redirecting to auth screen")
            navController.navigate(Routes.Auth.Screen) {
                popUpTo(Routes.Auth.Account) { inclusive = true }
            }
        }
    }
    
    FlorisScreen {
        title = "Account"
        previewFieldVisible = false
        
        content {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // User Profile Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Profile Icon
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Welcome Message
                        Text(
                            text = "Welcome, $displayName!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        if (userEmail != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // About SendRight Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "About SendRight",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Text(
                            text = "SendRight is an AI-enhanced Android keyboard built on FlorisBoard. " +
                                    "Experience intelligent text suggestions, smart autocorrect, and AI-powered " +
                                    "writing assistance that adapts to your unique writing style. Make every " +
                                    "message, email, and document better with SendRight's advanced features.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            textAlign = TextAlign.Justify
                        )
                    }
                }
                
                // Action Buttons Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rate Us Button
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.vishruth.key1")
                                    setPackage("com.android.vending")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to browser if Play Store is not available
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.vishruth.key1")
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Rate",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Rate Us on Play Store",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    // Logout Button
                    OutlinedButton(
                        onClick = {
                            isLoggingOut = true
                            scope.launch {
                                try {
                                    userManager.signOut()
                                    Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error signing out: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoggingOut = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isLoggingOut
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Logout",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = if (isLoggingOut) "Signing Out..." else "Sign Out",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // App Version or Additional Info
                Text(
                    text = "Thank you for using SendRight!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

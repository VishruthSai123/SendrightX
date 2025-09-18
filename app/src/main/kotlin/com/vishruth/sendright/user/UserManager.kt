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

package com.vishruth.key1.user

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.vishruth.key1.billing.BillingManager
import com.vishruth.key1.billing.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * UserManager class to handle user authentication and profile management
 */
class UserManager private constructor() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Google Sign-In client
    private var googleSignInClient: GoogleSignInClient? = null
    
    // Billing and subscription managers
    private var billingManager: BillingManager? = null
    private var subscriptionManager: SubscriptionManager? = null
    
    // State flow for emitting user data updates
    private val _userData = MutableStateFlow<UserData?>(null)
    val userData: StateFlow<UserData?> = _userData.asStateFlow()
    
    // State flow for authentication state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    // Flag to track if eager billing initialization was requested - REMOVED
    // private val eagerBillingRequested = MutableStateFlow(false)
    
    // Initialization state
    private var isInitialized = false
    private var isInitializing = false
    
    // Auth state listener to prevent multiple registrations
    private var authStateListenerRegistered = false

    init {
        // Set up auth state listener immediately when UserManager is created
        setupAuthStateListener()
    }
    
    companion object {
        private const val TAG = "UserManager"
        private var instance: UserManager? = null
        
        /**
         * Get the singleton instance of UserManager
         */
        @Synchronized
        fun getInstance(): UserManager {
            if (instance == null) {
                instance = UserManager()
            }
            return instance!!
        }
    }
    
    /**
     * Initialize the UserManager with the application context
     * This should be called during application startup
     *
     * @param context The application context
     */
    fun initialize(context: Context) {
        if (isInitialized || isInitializing) {
            Log.d(TAG, "UserManager already initialized or initializing, skipping")
            return
        }
        
        isInitializing = true
        Log.d(TAG, "Initializing UserManager asynchronously")
        
        // Start async initialization to avoid blocking main thread
        coroutineScope.launch {
            try {
                // Initialize Google Sign-In first
                initializeGoogleSignIn(context)
                
                // Initialize billing managers
                initializeBillingManagers(context)
                
                isInitialized = true
                isInitializing = false
                Log.d(TAG, "UserManager initialization completed")
                
                // Trigger purchase restoration if user is already authenticated
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.d(TAG, "User already authenticated during initialization, triggering subscription sync")
                    syncSubscriptionState()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during UserManager initialization", e)
                isInitializing = false
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Error(e)
                }
            }
        }
    }
    
    /**
     * Initialize Google Sign-In configuration
     */
    private suspend fun initializeGoogleSignIn(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Google Sign-In initialization")
            
            // Try to get the generated web client ID from resources first
            val webClientId = try {
                val resourceId = context.resources.getIdentifier(
                    "default_web_client_id", "string", context.packageName
                )
                if (resourceId != 0) {
                    context.getString(resourceId)
                } else {
                    // Fallback to hardcoded web client ID
                    "715038887430-kv5jungt3cpgv09agb0jc4d2v11sv5gn.apps.googleusercontent.com"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get default_web_client_id from resources, using fallback", e)
                // Fallback to hardcoded web client ID
                "715038887430-kv5jungt3cpgv09agb0jc4d2v11sv5gn.apps.googleusercontent.com"
            }
            
            Log.d(TAG, "Using web client ID: $webClientId")
            
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .requestProfile()
                .build()
            
            withContext(Dispatchers.Main) {
                googleSignInClient = GoogleSignIn.getClient(context, gso)
            }
            Log.d(TAG, "Google Sign-In initialized successfully with ID token request")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Google Sign-In", e)
            // Don't let Google Sign-In errors crash the app
            googleSignInClient = null
        }
    }
    
    /**
     * Initialize billing managers asynchronously
     */
    private suspend fun initializeBillingManagers(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing billing managers")
            billingManager = BillingManager(context)
            subscriptionManager = SubscriptionManager(context, billingManager!!)
            Log.d(TAG, "Billing managers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing billing managers", e)
            billingManager = null
            subscriptionManager = null
        }
    }
    
    /**
     * Sign up with email and password
     *
     * @param email The user's email
     * @param password The user's password
     * @param displayName The user's display name (optional)
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun signUpWithEmailAndPassword(email: String, password: String, displayName: String? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = authResult.user
                
                if (user != null) {
                    // Update display name if provided
                    if (!displayName.isNullOrBlank()) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName)
                            .build()
                        user.updateProfile(profileUpdates).await()
                    }
                    
                    // Create user document in Firestore
                    createUserDocument(user, displayName)
                    
                    Log.d(TAG, "User signed up successfully: ${user.uid}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to create user"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signing up", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign in with email and password
     *
     * @param email The user's email
     * @param password The user's password
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                
                if (user != null) {
                    Log.d(TAG, "User signed in successfully: ${user.uid}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to sign in user"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signing in", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign in anonymously
     *
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun signInAnonymously(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val authResult = auth.signInAnonymously().await()
                val user = authResult.user
                
                if (user != null) {
                    // Create user document in Firestore for anonymous user
                    createUserDocument(user, "Anonymous")
                    
                    Log.d(TAG, "Anonymous user signed in successfully: ${user.uid}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to sign in anonymously"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signing in anonymously", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get Google Sign-In intent for launching sign-in flow
     *
     * @return Task<GoogleSignInAccount> that can be used to start sign-in activity
     */
    fun getGoogleSignInIntent(): Task<GoogleSignInAccount>? {
        return googleSignInClient?.silentSignIn()
    }
    
    /**
     * Get Google Sign-In client for launching sign-in activity
     *
     * @return GoogleSignInClient or null if not initialized
     */
    fun getGoogleSignInClient(): GoogleSignInClient? {
        return googleSignInClient
    }
    
    /**
     * Sign in with Google using the provided account
     *
     * @param account The GoogleSignInAccount from the sign-in result
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Google sign-in with account: ${account.email}")
                Log.d(TAG, "ID Token available: ${account.idToken != null}")
                
                if (account.idToken == null) {
                    return@withContext Result.failure(Exception("No ID token available from Google account"))
                }
                
                // Update auth state to loading immediately for responsive UI
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Loading
                }
                
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                Log.d(TAG, "Created Google credential, signing in to Firebase...")
                
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                
                if (user != null) {
                    Log.d(TAG, "Firebase authentication successful: ${user.uid}")
                    
                    // The auth state listener will handle the rest of the flow
                    // This ensures consistent state management
                    
                    Log.d(TAG, "Google sign-in successful: ${user.uid}")
                    Result.success(Unit)
                } else {
                    Log.e(TAG, "Firebase authentication returned null user")
                    withContext(Dispatchers.Main) {
                        _authState.value = AuthState.Error(Exception("Failed to sign in with Google"))
                    }
                    Result.failure(Exception("Failed to sign in with Google"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error signing in with Google: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.Error(e)
                }
                Result.failure(e)
            }
        }
    }
    
    /**
     * Sign out the current user
     */
    fun signOut() {
        try {
            auth.signOut()
            googleSignInClient?.signOut()
            Log.d(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }
    }
    
    /**
     * Get the current Firebase user
     *
     * @return The current FirebaseUser or null if not signed in
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Check if the current user is anonymous
     *
     * @return true if the user is anonymous, false otherwise
     */
    fun isCurrentUserAnonymous(): Boolean {
        return auth.currentUser?.isAnonymous ?: false
    }
    
    /**
     * Create user document in Firestore
     *
     * @param user The FirebaseUser
     * @param displayName The user's display name
     */
    private suspend fun createUserDocument(user: FirebaseUser, displayName: String?) {
        try {
            val userData = hashMapOf(
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (displayName ?: ""),
                "subscriptionStatus" to "free",
                "lastRewardedAdDate" to null,
                "totalAdRewardsUsed" to 0,
                "createdAt" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(user.uid)
                .set(userData)
                .await()
            
            Log.d(TAG, "User document created successfully for: ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user document", e)
        }
    }
    
    /**
     * Load user data from Firestore
     *
     * @param user The FirebaseUser
     */
    private suspend fun loadUserData(user: FirebaseUser) {
        try {
            Log.d(TAG, "Loading user data for: ${user.uid}")
            
            val document = firestore.collection("users")
                .document(user.uid)
                .get()
                .await()
            
            if (document.exists()) {
                val userData = document.toObject(UserData::class.java)
                if (userData != null) {
                    withContext(Dispatchers.Main) {
                        _userData.value = userData
                        _authState.value = AuthState.Authenticated(userData)
                        Log.d(TAG, "Auth state updated to Authenticated: ${userData.email}")
                    }
                    Log.d(TAG, "User data loaded successfully for: ${user.uid}")
                } else {
                    Log.w(TAG, "User document exists but couldn't deserialize data")
                    // Fall back to creating new user data
                    createAndSetNewUserData(user)
                }
            } else {
                Log.d(TAG, "User document doesn't exist, creating new one")
                // Create user document if it doesn't exist
                createAndSetNewUserData(user)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user data", e)
            
            // Check if it's a permission error or Firestore database setup issue
            if (e.message?.contains("PERMISSION_DENIED") == true || 
                e.message?.contains("Missing or insufficient permissions") == true ||
                e.message?.contains("does not exist") == true || 
                e.message?.contains("NOT_FOUND") == true) {
                
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.e(TAG, "Firestore permission denied. Please set up security rules in Firebase Console for project: sendright-25")
                    Log.e(TAG, "For development, you can use: allow read, write: if request.auth != null;")
                } else {
                    Log.e(TAG, "Firestore database not found. Please set up Firestore in Firebase Console for project: sendright-25")
                }
                
                // Continue with local-only mode for now
                createAndSetLocalUserData(user)
            } else {
                // For other errors, try local mode first, then show error
                Log.w(TAG, "Unexpected error, trying local mode")
                createAndSetLocalUserData(user)
            }
        }
    }
    
    private suspend fun createAndSetNewUserData(user: FirebaseUser) {
        try {
            createUserDocument(user, user.displayName)
            val newUserData = UserData(
                userId = user.uid,
                email = user.email ?: "",
                displayName = user.displayName ?: "",
                subscriptionStatus = "free",
                lastRewardedAdDate = null,
                totalAdRewardsUsed = 0
            )
            withContext(Dispatchers.Main) {
                _userData.value = newUserData
                _authState.value = AuthState.Authenticated(newUserData)
                Log.d(TAG, "Auth state updated to Authenticated (new user): ${newUserData.email}")
            }
            Log.d(TAG, "User document created and data loaded for: ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating new user data", e)
            createAndSetLocalUserData(user)
        }
    }
    
    private suspend fun createAndSetLocalUserData(user: FirebaseUser) {
        val localUserData = UserData(
            userId = user.uid,
            email = user.email ?: "",
            displayName = user.displayName ?: "",
            subscriptionStatus = "free",
            lastRewardedAdDate = null,
            totalAdRewardsUsed = 0
        )
        withContext(Dispatchers.Main) {
            _userData.value = localUserData
            _authState.value = AuthState.Authenticated(localUserData)
            Log.d(TAG, "Auth state updated to Authenticated (local mode): ${localUserData.email}")
        }
        Log.w(TAG, "Operating in local-only mode due to Firestore setup issue")
    }

    /**
     * Force refresh of user subscription status from billing manager
     * Based on reference: checkSubscriptionStatusFromServer function
     */
    suspend fun refreshSubscriptionStatus() {
        try {
            Log.d(TAG, "Refreshing subscription status...")
            subscriptionManager?.forceRefreshSubscriptionStatus()
            Log.d(TAG, "Subscription status refresh completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing subscription status", e)
        }
    }
    
    /**
     * Check subscription status on app resume
     * Based on reference: onResume() method
     */
    suspend fun onAppResume() {
        try {
            Log.d(TAG, "App resumed - checking subscription status...")
            subscriptionManager?.checkSubscriptionStatusOnResume()
            Log.d(TAG, "App resume subscription check completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during app resume subscription check", e)
        }
    }

    /**
     * Update user's subscription status
     *
     * @param status The new subscription status
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun updateSubscriptionStatus(status: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
        
        return withContext(Dispatchers.IO) {
            try {
                firestore.collection("users")
                    .document(user.uid)
                    .update("subscriptionStatus", status)
                    .await()
                
                // Update local user data and force UI refresh
                val currentData = _userData.value
                if (currentData != null) {
                    val updatedData = currentData.copy(subscriptionStatus = status)
                    _userData.value = updatedData
                    Log.d(TAG, "Local userData updated with subscription status: $status")
                } else {
                    Log.w(TAG, "No current user data to update with subscription status")
                }
                
                Log.d(TAG, "Subscription status updated to: $status")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating subscription status", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Record a rewarded ad usage
     *
     * @return Result containing Unit on success or Exception on failure
     */
    suspend fun recordRewardedAdUsage(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("No user signed in"))
        
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                firestore.collection("users")
                    .document(user.uid)
                    .update(
                        mapOf(
                            "lastRewardedAdDate" to currentTime,
                            "totalAdRewardsUsed" to com.google.firebase.firestore.FieldValue.increment(1)
                        )
                    )
                    .await()
                
                // Update local user data
                val currentData = _userData.value
                if (currentData != null) {
                    val updatedData = currentData.copy(
                        lastRewardedAdDate = currentTime,
                        totalAdRewardsUsed = currentData.totalAdRewardsUsed + 1
                    )
                    _userData.value = updatedData
                }
                
                Log.d(TAG, "Rewarded ad usage recorded for user: ${user.uid}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Error recording rewarded ad usage", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if user can use rewarded ad (once per day instead of once per month)
     *
     * @return true if user can use rewarded ad, false otherwise
     */
    fun canUseRewardedAd(): Boolean {
        val userData = _userData.value ?: return true // Allow if no user data
        val lastAdDate = userData.lastRewardedAdDate ?: return true // Allow if no previous ad usage
        
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24L * 60 * 60 * 1000 // One day in milliseconds
        
        return (currentTime - lastAdDate) > oneDayInMillis
    }
    
    // MARK: - Subscription Methods
    
    /**
     * Get the billing manager instance
     */
    fun getBillingManager(): BillingManager? = billingManager
    
    /**
     * Get the subscription manager instance
     */
    fun getSubscriptionManager(): SubscriptionManager? = subscriptionManager
    
    /**
     * Check if user is a premium subscriber
     */
    fun isPremiumUser(): Boolean {
        return subscriptionManager?.isPro?.value ?: false
    }
    
    /**
     * Check if user can use AI action
     */
    fun canUseAiAction(): Boolean {
        return subscriptionManager?.canUseAiAction() ?: true
    }
    
    /**
     * Use an AI action (increment counter for free users) with integrity verification
     */
    suspend fun useAiAction(): Boolean {
        return subscriptionManager?.useAiAction() ?: true
    }
    
    /**
     * Get remaining AI actions for free users
     */
    fun getRemainingAiActions(): Int {
        return subscriptionManager?.getRemainingAiActions() ?: -1
    }
    
    /**
     * Check if ads should be shown
     */
    fun shouldShowAds(): Boolean {
        return !isPremiumUser()
    }
    
    /**
     * Get subscription status message
     */
    fun getSubscriptionStatusMessage(): String {
        return subscriptionManager?.getSubscriptionStatusMessage() ?: "Free Plan"
    }
    
    /**
     * Restore purchases for the current authenticated user
     * This should be called after successful authentication to recover subscriptions
     */
    fun restorePurchases(callback: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot restore purchases - user not authenticated")
            callback(false, "User must be signed in to restore purchases")
            return
        }
        
        Log.d(TAG, "Restoring purchases for user: ${currentUser.uid}")
        
        billingManager?.restorePurchases { success, message ->
            if (success) {
                Log.d(TAG, "Purchase restoration successful")
                // Sync with server-side entitlements
                coroutineScope.launch {
                    billingManager?.syncWithServerAndRestore(currentUser.uid)
                }
            } else {
                Log.w(TAG, "Purchase restoration failed: $message")
            }
            callback(success, message)
        }
    }
    
    /**
     * Sync subscription state with server after authentication
     * This ensures consistent subscription state across app instances
     */
    suspend fun syncSubscriptionState() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot sync subscription state - user not authenticated")
            return
        }
        
        Log.d(TAG, "Syncing subscription state for user: ${currentUser.uid}")
        
        try {
            // Sync with server and restore if needed
            billingManager?.syncWithServerAndRestore(currentUser.uid)
            
            // Update local user data with latest subscription info
            updateUserDataFromServer(currentUser.uid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing subscription state", e)
        }
    }
    
    /**
     * Update user data from server after authentication or subscription changes
     */
    private suspend fun updateUserDataFromServer(userId: String) {
        try {
            Log.d(TAG, "Updating user data from server for user: $userId")
            
            val userDoc = firestore.collection("users").document(userId).get().await()
            
            if (userDoc.exists()) {
                val subscriptionStatus = userDoc.getString("subscriptionStatus") ?: "free"
                val subscriptionExpiryTime = userDoc.getLong("subscriptionExpiryTime")
                val lastRewardedAdDate = userDoc.getLong("lastRewardedAdDate")
                val totalAdRewardsUsed = userDoc.getLong("totalAdRewardsUsed")?.toInt() ?: 0
                
                val currentUserData = _userData.value
                if (currentUserData != null) {
                    val updatedUserData = currentUserData.copy(
                        subscriptionStatus = subscriptionStatus,
                        subscriptionExpiryTime = subscriptionExpiryTime,
                        lastRewardedAdDate = lastRewardedAdDate,
                        totalAdRewardsUsed = totalAdRewardsUsed
                    )
                    
                    _userData.value = updatedUserData
                    Log.d(TAG, "User data updated from server")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user data from server", e)
        }
    }
    
    /**
     * Enhanced authentication state setup with purchase restoration
     */
    private fun setupAuthStateListener() {
        if (authStateListenerRegistered) {
            Log.d(TAG, "Auth state listener already registered")
            return
        }
        
        Log.d(TAG, "Setting up auth state listener with purchase restoration")
        
        auth.addAuthStateListener { firebaseAuth ->
            coroutineScope.launch {
                try {
                    val user = firebaseAuth.currentUser
                    if (user != null) {
                        Log.d(TAG, "User authenticated: ${user.uid}")
                        
                        // Create user data
                        val userData = UserData(
                            userId = user.uid,
                            email = user.email ?: "",
                            displayName = user.displayName ?: ""
                        )
                        
                        _userData.value = userData
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.Authenticated(userData)
                        }
                        
                        // Auto-sync subscription state after authentication
                        if (isInitialized) {
                            syncSubscriptionState()
                        }
                        
                    } else {
                        Log.d(TAG, "User signed out")
                        _userData.value = null
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in auth state listener", e)
                    withContext(Dispatchers.Main) {
                        _authState.value = AuthState.Error(e)
                    }
                }
            }
        }
        
        authStateListenerRegistered = true
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        subscriptionManager?.destroy()
        billingManager?.destroy()
    }
    
    /**
     * Trigger purchase restoration manually (can be called from UI)
     * This is useful for "Restore Purchases" buttons in settings
     */
    fun triggerPurchaseRestoration(callback: (Boolean, String?) -> Unit) {
        if (!isInitialized) {
            callback(false, "UserManager not initialized")
            return
        }
        
        restorePurchases(callback)
    }
    
    /**
     * Check if current user has server-side entitlements
     * This is useful for debugging subscription issues
     */
    suspend fun checkServerSideEntitlements(): Boolean {
        val currentUser = auth.currentUser ?: return false
        return billingManager?.verifyServerSideEntitlements(currentUser.uid) ?: false
    }
}

/**
 * Data class representing user data
 */
data class UserData(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val subscriptionStatus: String = "free",
    val subscriptionExpiryTime: Long? = null,
    val lastRewardedAdDate: Long? = null,
    val totalAdRewardsUsed: Int = 0
)

/**
 * Sealed class representing authentication states
 */
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val userData: UserData) : AuthState()
    data class Error(val exception: Exception) : AuthState()
}
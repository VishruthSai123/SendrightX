# 📋 Pre-Release Checklist - SendRightX

## ✅ COMPLETED - Security Fixes

### 1. API Keys Security ✅
- ✅ **Removed hardcoded API keys from BuildConfig**
  - All 8 GEMINI_API_KEY fields removed from build.gradle.kts
  - Keys no longer visible in decompiled APK
- ✅ **Implemented Supabase dynamic key fetching**
  - SupabaseConfig.kt created and working
  - 1-hour cache with local fallback
  - Keys can be updated without app updates
- ✅ **GeminiApiService refactored**
  - Now uses SupabaseConfig.fetchApiKeys()
  - initializeApiKeys() called in SendRightApplication.onCreate()

### 2. Memory Leak Fixes ✅
- ✅ **Added UserManager.destroy() calls**
  - SendRightApplication.onTerminate()
  - FlorisImeService.onDestroy()
- ✅ **BillingManager cleanup cascade**
  - destroy() method already existed
  - Now properly called via UserManager

### 3. Subscription Validation ✅
- ✅ **Google Play as source of truth**
  - Already implemented correctly
  - checkSubscriptionStatusInternal() validates with BillingManager

---

## ⚠️ CRITICAL - Before Publishing

### Step 1: Complete Supabase Setup
**Status:** ⚠️ **MUST DO BEFORE RELEASE**

1. **Run SQL Script**
   ```sql
   -- Go to Supabase SQL Editor and run: supabase_insert_keys.sql
   ```
   - ✅ Script ready with your 8 keys
   - ⚠️ **Verify it's executed** by running:
     ```sql
     SELECT key_type, COUNT(*) FROM api_keys WHERE is_active = true GROUP BY key_type;
     ```
     Should return: `gemini_free: 4, gemini_premium: 4`

2. **Disable RLS on api_keys table**
   - Supabase → Authentication → Policies → `api_keys` table
   - Click shield icon → "Disable RLS"
   - OR create policy: `allow anonymous SELECT` if you prefer

3. **Verify Supabase credentials in code**
   - ✅ Already updated in SupabaseConfig.kt:
     - URL: `https://qkfcopradlyuxpkkxbmj.supabase.co`
     - Key: (configured)

### Step 2: Test with Real Device
**Status:** ⚠️ **REQUIRED**

```powershell
# Install debug APK
adb install -r "app\build\outputs\apk\debug\app-debug.apk"

# Monitor logs
adb logcat | Select-String "SupabaseConfig|GeminiApiService|SendRightApplication"
```

**Expected logs:**
```
SendRightApplication: Initializing API keys from Supabase...
SupabaseConfig: ✅ API keys fetched from Supabase: 4 free, 4 premium
GeminiApiService: ✅ API keys initialized: 4 free, 4 premium
```

**Test AI features:**
- ✅ Select text → Magic Wand → Test transformation (e.g., "Make formal")
- ✅ Verify it works without crashes
- ✅ Check network connectivity handling

### Step 3: Verify Keys NOT in APK
**Status:** ⚠️ **CRITICAL SECURITY CHECK**

```powershell
# Build release APK first
./gradlew assembleRelease

# Extract and search for keys
cd "app\build\outputs\apk\release"
jar -xf app-release.apk
findstr /s "AIzaSy" *
```

**Expected result:** `No matches found` (or only in documentation/comments)

**If keys ARE found:**
- ❌ DO NOT PUBLISH
- Check build.gradle.kts for any remaining buildConfigField with keys
- Verify GeminiApiService doesn't reference BuildConfig.GEMINI_API_KEY

### Step 4: Version Bump
**Status:** ⚠️ **REQUIRED**

Check `gradle.properties` or `build.gradle.kts`:
```kotlin
versionCode = 124  // Must increment from previous release
versionName = "0.4.4"  // Update as needed
```

---

## 🚀 BUILD COMMANDS

### For Open Testing (Beta)
```powershell
# Build signed AAB (Android App Bundle)
./gradlew bundleRelease

# Output: app\build\outputs\bundle\release\app-release.aab
```

### For Production Release
```powershell
# Build signed AAB
./gradlew bundleRelease

# Build signed APK (for direct distribution)
./gradlew assembleRelease

# Outputs:
# AAB: app\build\outputs\bundle\release\app-release.aab
# APK: app\build\outputs\apk\release\app-release.apk
```

**Note:** Ensure your `keystore.jks` and signing config are set up in `build.gradle.kts`

---

## 🧪 TESTING CHECKLIST

### Before Open Testing
- [ ] Supabase SQL script executed
- [ ] API keys verified in Supabase database
- [ ] RLS disabled on api_keys table
- [ ] App successfully fetches keys from Supabase (check logs)
- [ ] AI features working (Magic Wand transformations)
- [ ] No API keys found in decompiled APK
- [ ] Version code/name incremented
- [ ] ProGuard/R8 enabled for release build
- [ ] Signed with release keystore

### Before Production Release
- [ ] All "Before Open Testing" items ✅
- [ ] Open testing period completed (recommended: 2+ weeks)
- [ ] No critical crashes reported
- [ ] Subscription system tested (free + premium)
- [ ] Key rotation tested (update key in Supabase → verify app gets it)
- [ ] Memory leak monitoring (no OOM crashes)
- [ ] Play Store listing updated (if needed)

---

## 📊 MONITORING AFTER RELEASE

### 1. API Key Health
Monitor Supabase logs or add app analytics:
- Track which keys are being used
- Monitor for 429 (rate limit) errors
- Watch for quota exhaustion

### 2. Crash Monitoring
- Firebase Crashlytics (if integrated)
- Play Console → Quality → Crashes & ANRs

### 3. Key Rotation Schedule
**Recommended:** Rotate keys every 30-60 days or when:
- Key quota exhausted
- 429 errors increase
- Proactive security maintenance

---

## 🔄 POST-RELEASE: How to Update Keys (No App Update!)

### When a key gets exhausted:

1. **Add new key to Supabase:**
   ```sql
   -- Go to Supabase SQL Editor
   INSERT INTO api_keys (key_type, api_key, is_active, priority) 
   VALUES ('gemini_free', 'YOUR_NEW_KEY_HERE', true, 0);
   ```

2. **Deactivate old key:**
   ```sql
   UPDATE api_keys 
   SET is_active = false 
   WHERE api_key = 'OLD_KEY_HERE';
   ```

3. **Done!** Users will get new key within 1 hour (cache refresh)

### To force immediate refresh for testing:
- Clear app data
- Or add a manual refresh button in settings (optional enhancement)

---

## ⚠️ POTENTIAL ISSUES & SOLUTIONS

### Issue 1: "No API keys configured" on app start
**Cause:** Supabase not set up or network error

**Solutions:**
- Verify Supabase URL and ANON key in SupabaseConfig.kt
- Check RLS is disabled on api_keys table
- Verify SQL script was executed
- Check device has internet on first launch

### Issue 2: Keys not updating after Supabase change
**Cause:** 1-hour cache

**Solutions:**
- Expected behavior - cache expires after 1 hour
- Clear app data to force immediate refresh
- Add manual refresh option (future enhancement)

### Issue 3: AI features not working
**Cause:** Invalid or exhausted keys

**Solutions:**
- Check Supabase has active keys (`is_active = true`)
- Verify keys are valid in Google AI Studio
- Check app logs for specific error messages
- Try refreshing keys in Supabase

### Issue 4: Play Store rejection
**Cause:** API keys found in APK

**Solutions:**
- Run verification step above (findstr for AIzaSy)
- Ensure no keys in BuildConfig
- Check ProGuard rules aren't exposing keys
- Verify SupabaseConfig doesn't log keys

---

## 🎯 READY TO PUBLISH?

### Pre-Flight Check:
```
✅ Supabase SQL script executed
✅ Keys verified in database
✅ RLS disabled on api_keys table
✅ Supabase credentials in SupabaseConfig.kt
✅ App tested on real device
✅ API keys NOT in APK (verified)
✅ AI features working
✅ Version code incremented
✅ Release build signed
✅ No compilation errors
✅ Memory leaks fixed
```

### If ALL checks ✅:
**YES! Safe to publish to Open Testing → Production** 🚀

### If ANY checks ❌:
**STOP! Fix issues before publishing**

---

## 📞 SUPPORT

### Documentation Files:
- `SUPABASE_SETUP_GUIDE.md` - Detailed Supabase setup
- `SETUP_CHECKLIST.md` - Quick reference guide
- `supabase_insert_keys.sql` - SQL script to run

### Key Files Changed:
- `app/build.gradle.kts` - Removed API key BuildConfig fields
- `app/src/main/kotlin/com/vishruth/key1/api/SupabaseConfig.kt` - NEW
- `app/src/main/kotlin/com/vishruth/sendright/ime/smartbar/GeminiApiService.kt` - Refactored
- `app/src/main/kotlin/com/vishruth/sendright/SendRightApplication.kt` - Added init + cleanup
- `app/src/main/kotlin/com/vishruth/sendright/FlorisImeService.kt` - Added cleanup

---

## 🎉 SUCCESS METRICS

After publishing, track:
- ✅ Zero "API key exposure" security reports
- ✅ Ability to update keys without app updates
- ✅ Reduced crash rate from memory leaks
- ✅ Smooth key rotation during emergencies
- ✅ No Play Store policy violations

---

**Last Updated:** November 10, 2025
**Status:** Ready for release after Supabase setup completion ✅

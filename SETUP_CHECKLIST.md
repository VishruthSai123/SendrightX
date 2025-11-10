# 🚀 Quick Setup Checklist

## ✅ What We've Fixed

### 1. **CRITICAL - API Keys Security** 
- ❌ **Before:** API keys hardcoded in BuildConfig (visible in decompiled APK)
- ✅ **After:** Keys fetched from Supabase dynamically (secure & updateable)

### 2. **CRITICAL - Memory Leaks**
- ❌ **Before:** BillingManager, SubscriptionManager never cleaned up
- ✅ **After:** Proper `destroy()` calls added in Application and Service

### 3. **HIGH - Subscription Validation**
- ✅ **Already Good:** Your code already validates with Google Play first

---

## 📋 Setup Steps (Do This Now!)

### Step 1: Create Supabase Project (5 minutes)
1. Go to https://supabase.com
2. Click "New Project"
3. Name it: `sendright-api-keys`
4. Save the password somewhere safe
5. Wait 2 minutes for project to be ready

### Step 2: Run SQL Script (2 minutes)
1. In Supabase, go to **"SQL Editor"** (left sidebar)
2. Click **"New Query"**
3. Copy-paste the entire content of `supabase_insert_keys.sql`
4. Click **"Run"** (or press Ctrl+Enter)
5. You should see: "Success. 8 rows affected" (4 free + 4 premium keys)

### Step 3: Disable RLS (1 minute)
1. Go to **"Table Editor"** → `api_keys` table
2. Click the **shield icon** at top
3. Select **"Disable RLS"** (or create a policy allowing anonymous SELECT)
4. This allows your app to read keys without authentication

### Step 4: Get Supabase Credentials (2 minutes)
1. Go to **"Settings"** → **"API"**
2. Copy:
   - **Project URL**: `https://xxxyyyzzzz.supabase.co`
   - **anon public**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

### Step 5: Update SupabaseConfig.kt (1 minute)
Open `app/src/main/kotlin/com/vishruth/key1/api/SupabaseConfig.kt`

Replace lines 23-24:
```kotlin
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_ANON_KEY = "your-supabase-anon-key"
```

With your actual values from Step 4.

### Step 6: Test (5 minutes)
1. Build and run the app (debug mode)
2. Check Android Studio Logcat for:
   ```
   SendRightApplication: ✅ API keys initialized from Supabase
   GeminiApiService: ✅ API keys initialized: 4 free, 4 premium
   ```
3. Try using an AI feature (Magic Wand)
4. It should work! 🎉

---

## 🧪 Verify API Keys NOT in APK

After building, verify keys are secure:

```powershell
# Build release APK
./gradlew assembleRelease

# Extract strings from APK (Windows)
cd app\build\outputs\apk\release
jar -xf app-release.apk
findstr /s "AIzaSy" *

# Should return: NO RESULTS (keys not found in APK) ✅
```

---

## 🔄 How to Update Keys Later (NO APP UPDATE NEEDED!)

### When a key gets exhausted:

1. Go to Supabase SQL Editor
2. Run:
```sql
-- Deactivate old key
UPDATE api_keys 
SET is_active = false 
WHERE api_key = 'AIzaSyDFHCLr-qLTLknradWcOVZXUQtPOVrNiTo';

-- Add new key
INSERT INTO api_keys (key_type, api_key, is_active, priority) 
VALUES ('gemini_free', 'YOUR_NEW_KEY_HERE', true, 0);
```

3. Done! Users will get the new key within 1 hour (cache refresh) 🚀

---

## 📱 Production Deployment Checklist

Before releasing to Play Store:

- [ ] Supabase API keys configured and tested
- [ ] Verified keys NOT in APK (see verification step above)
- [ ] Removed API keys from `local.properties` (or keep for reference only)
- [ ] Tested AI features work with Supabase keys
- [ ] Tested with both free and premium accounts
- [ ] Increment version number in `gradle.properties`
- [ ] Build signed release APK/AAB
- [ ] Upload to Play Store

---

## ⚠️ IMPORTANT NOTES

### About local.properties
You can **keep your keys in local.properties** as a backup reference, but they are **NO LONGER USED** by the app. The app now reads from Supabase only.

### Cache Duration
- Keys are cached for **1 hour** on device
- To force immediate refresh (in app code):
  ```kotlin
  GeminiApiService.refreshApiKeys(context)
  ```

### Key Rotation Best Practice
1. Add new keys to Supabase first (keep old ones active)
2. Wait 1 hour for all users to cache new keys
3. Then deactivate old keys
4. This ensures zero downtime

---

## 🎯 Benefits You Now Have

| Benefit | Description |
|---------|-------------|
| 🔒 **Secure** | Keys never in APK, can't be extracted |
| 🔄 **Updateable** | Change keys without app updates |
| ⚡ **Fast Switching** | Update takes 1 hour max (cache) |
| 📊 **Manageable** | Easy SQL queries to manage keys |
| 🎯 **Scalable** | Add unlimited keys, prioritize them |

---

## 🐛 Troubleshooting

### "No API keys configured" error
- Check Supabase URL and ANON key in `SupabaseConfig.kt`
- Verify RLS is disabled on `api_keys` table
- Check internet connection
- Look at logcat for detailed error messages

### Keys not updating after Supabase change
- Keys are cached for 1 hour
- Force refresh by clearing app data
- Or add refresh button in settings (optional)

### App crashes on startup
- Check logcat for stack trace
- Verify Supabase project is active
- Ensure table structure matches schema

---

## 📞 Need Help?

See the detailed guide: `SUPABASE_SETUP_GUIDE.md`

---

**Next Step:** Follow the 6 setup steps above! 🚀

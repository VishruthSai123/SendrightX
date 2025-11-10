# 🚀 Supabase API Key Management Setup Guide

## **Why This Approach?**

By using Supabase to manage your Gemini API keys, you get:

✅ **Dynamic Key Rotation** - Update API keys without releasing new app versions  
✅ **No APK Decompilation Risk** - Keys never stored in app binary  
✅ **Instant Key Switching** - If a key fails, update it in Supabase and users get it within 1 hour  
✅ **Multiple Fallback Keys** - Manage free and premium key pools easily  
✅ **Usage Analytics** - Track which keys are being used (future feature)

---

## **Step 1: Create Supabase Project**

1. Go to [https://supabase.com](https://supabase.com) and sign up/login
2. Click **"New Project"**
3. Fill in:
   - **Project Name**: `sendright-api-keys` (or any name)
   - **Database Password**: Generate a strong password (save it!)
   - **Region**: Choose closest to your users
4. Click **"Create new project"** and wait ~2 minutes

---

## **Step 2: Create API Keys Table**

1. In your Supabase project, go to **"Table Editor"** (left sidebar)
2. Click **"Create a new table"**
3. Configure the table:

   **Table Name**: `api_keys`
   
   **Columns:**
   ```
   id              | uuid          | Primary Key, Default: gen_random_uuid()
   created_at      | timestamptz   | Default: now()
   key_type        | text          | Required (e.g., "gemini_free" or "gemini_premium")
   api_key         | text          | Required (the actual API key)
   is_active       | boolean       | Default: true
   priority        | int4          | Default: 0 (lower = higher priority)
   ```

4. **Disable RLS (Row Level Security) for this table**:
   - Go to **"Authentication" → "Policies"**
   - Find the `api_keys` table
   - Click the **shield icon** to **disable RLS** (or create a policy that allows anonymous SELECT)

   **Why?** Your app needs to read keys without authentication. This is safe because:
   - Keys are read-only from app side
   - Actual API usage is rate-limited by Gemini
   - Keys rotate frequently

---

## **Step 3: Insert Your API Keys**

In Supabase **SQL Editor**, run these queries to add your keys:

```sql
-- Free user API keys
INSERT INTO api_keys (key_type, api_key, is_active, priority) VALUES
  ('gemini_free', 'YOUR_PRIMARY_FREE_KEY', true, 0),
  ('gemini_free', 'YOUR_FALLBACK_FREE_KEY_1', true, 1),
  ('gemini_free', 'YOUR_FALLBACK_FREE_KEY_2', true, 2),
  ('gemini_free', 'YOUR_FALLBACK_FREE_KEY_3', true, 3);

-- Premium user API keys
INSERT INTO api_keys (key_type, api_key, is_active, priority) VALUES
  ('gemini_premium', 'YOUR_PRIMARY_PREMIUM_KEY', true, 0),
  ('gemini_premium', 'YOUR_FALLBACK_PREMIUM_KEY_1', true, 1),
  ('gemini_premium', 'YOUR_FALLBACK_PREMIUM_KEY_2', true, 2),
  ('gemini_premium', 'YOUR_FALLBACK_PREMIUM_KEY_3', true, 3);
```

**Replace** `YOUR_..._KEY` with your actual Gemini API keys from Google AI Studio.

---

## **Step 4: Get Supabase Credentials**

1. Go to **"Settings" → "API"** in Supabase
2. Copy these values:

   ```
   Project URL:  https://xxxyyyzzzz.supabase.co
   Anon/Public Key:  eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```

3. Open `SupabaseConfig.kt` and update:

   ```kotlin
   // Line 23-24
   private const val SUPABASE_URL = "https://xxxyyyzzzz.supabase.co"  // Your project URL
   private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1N..."  // Your anon key
   ```

---

## **Step 5: Test the Integration**

1. Build and run your app (debug build)
2. Check logcat for:
   ```
   SendRightApplication: ✅ API keys initialized from Supabase
   GeminiApiService: ✅ API keys initialized: 4 free, 4 premium
   ```

3. Try using an AI feature - it should work with the Supabase keys!

---

## **How to Update Keys (Without App Update)**

### **Scenario 1: One Key Exhausted**

```sql
-- Mark old key as inactive
UPDATE api_keys 
SET is_active = false 
WHERE api_key = 'OLD_EXHAUSTED_KEY';

-- Add new key
INSERT INTO api_keys (key_type, api_key, is_active, priority) 
VALUES ('gemini_free', 'BRAND_NEW_KEY', true, 0);
```

**Result:** Within 1 hour (cache expiry), all users will automatically start using the new key! 🎉

### **Scenario 2: Rotate All Keys**

```sql
-- Deactivate all old keys
UPDATE api_keys SET is_active = false WHERE key_type = 'gemini_free';

-- Add fresh key set
INSERT INTO api_keys (key_type, api_key, is_active, priority) VALUES
  ('gemini_free', 'NEW_KEY_1', true, 0),
  ('gemini_free', 'NEW_KEY_2', true, 1),
  ('gemini_free', 'NEW_KEY_3', true, 2);
```

### **Scenario 3: Force Immediate Refresh (Emergency)**

If you need users to pick up new keys **immediately** (not wait 1 hour):

**Option A:** Release a minor app update that just increments version  
**Option B:** Add a "Refresh API Keys" button in settings that calls:
```kotlin
GeminiApiService.refreshApiKeys(context)
```

---

## **Step 6: Monitor Key Usage (Optional)**

Add a `usage_count` column to track which keys are being used:

```sql
ALTER TABLE api_keys ADD COLUMN usage_count INT DEFAULT 0;

-- Create a function to increment usage (future feature)
CREATE OR REPLACE FUNCTION increment_key_usage(key_id uuid)
RETURNS void AS $$
BEGIN
  UPDATE api_keys SET usage_count = usage_count + 1 WHERE id = key_id;
END;
$$ LANGUAGE plpgsql;
```

Then modify `SupabaseConfig.kt` to call this function when a key is used.

---

## **Security Considerations**

### **Is it safe to use ANON key in the app?**

✅ **YES** - The ANON key only allows:
- Reading from `api_keys` table (public read access)
- Cannot modify/delete keys
- Actual API requests still go to Gemini with rate limits

### **Can users see the Supabase keys?**

❌ **NO** - Unlike BuildConfig, Supabase keys are:
- Fetched at runtime (not compiled into APK)
- Cached locally in encrypted SharedPreferences
- Not visible in decompiled code

### **What if someone extracts the ANON key?**

⚠️ **Risk is minimal** because:
1. They can only **read** API keys (not modify)
2. Reading keys doesn't give them quota access - they still need to call Gemini
3. You can rotate the keys in Supabase anytime
4. For extra security, implement Row Level Security (RLS) with app verification

---

## **Advanced: Add App Verification (Optional)**

For maximum security, verify requests are from your app using Play Integrity API:

1. Add Play Integrity token to Supabase requests
2. Create a Supabase Edge Function to verify the token
3. Only return keys if token is valid

This prevents unauthorized apps from reading your keys. See: [Play Integrity API Docs](https://developer.android.com/google/play/integrity)

---

## **Troubleshooting**

### **❌ "No API keys configured" error**

**Check:**
1. Supabase project URL and ANON key are correct in `SupabaseConfig.kt`
2. RLS is disabled on `api_keys` table (or policy allows SELECT)
3. You inserted keys with correct `key_type` values (`gemini_free` / `gemini_premium`)
4. Internet connection is available

**Debug:**
```kotlin
// Add to GeminiApiService initialization
Log.d("DEBUG", "Supabase URL: ${SupabaseConfig.SUPABASE_URL}")
Log.d("DEBUG", "Fetched keys: Free=${FREE_API_KEYS.size}, Premium=${PREMIUM_API_KEYS.size}")
```

### **❌ Keys not updating after Supabase change**

- **Cache duration:** Keys are cached for 1 hour
- **Force refresh:** Call `GeminiApiService.refreshApiKeys(context)`
- **Clear app data:** Settings → Apps → SendRight → Clear Data

---

## **Benefits Recap**

| Feature | Before (BuildConfig) | After (Supabase) |
|---------|---------------------|------------------|
| Key visible in APK? | ✅ Yes (security risk) | ❌ No |
| Update without app release? | ❌ No | ✅ Yes |
| Rotate keys instantly? | ❌ No | ✅ Yes (1hr cache) |
| Manage multiple keys easily? | ❌ Hard (rebuild needed) | ✅ Easy (SQL query) |
| Track key usage? | ❌ No | ✅ Yes (future) |

---

## **Next Steps**

1. ✅ Complete Supabase setup
2. ✅ Test key fetching in debug build
3. ✅ Remove old API keys from `local.properties`
4. ✅ Build release APK and verify keys not in binary
5. 🚀 Deploy to production!

---

## **Questions?**

- Supabase Docs: https://supabase.com/docs
- Gemini API: https://ai.google.dev/docs
- Play Integrity: https://developer.android.com/google/play/integrity

**Remember:** Your API keys are now safe, manageable, and updatable! 🎉

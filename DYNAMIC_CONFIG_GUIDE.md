# 🔄 Dynamic Configuration Guide - Update App Without Play Store

## ✅ What's Fixed

### 1. **Cache Duration Reduced** (Production Issue Fixed)
- **Before:** 1 hour cache - users stuck with stale/broken keys for 60 minutes
- **After:** 10 minutes cache - issues resolve automatically within 10 minutes
- **Impact:** Prevents "premium keys not configured" errors from persisting

### 2. **Dynamic Model Configuration** (New Feature!)
- **Before:** Model name hardcoded (gemini-2.0-flash-exp) - required Play Store update to change
- **After:** Model name fetched from Supabase - update instantly without app release
- **Single Endpoint:** Uses `gemini-2.5-flash-lite` for all users to avoid rate limit issues
- **Impact:** Switch models instantly without app updates, no rate limit conflicts

---

## 📊 Supabase Table Structure

### Table: `ai_models_config` (NEW!)
Stores AI model configuration that can be updated remotely.

**Columns:**
- `model_key` (text): 'gemini_default' (single endpoint for all)
- `model_name` (text): 'gemini-2.5-flash-lite' (the actual Google AI model)
- `endpoint_suffix` (text): ':generateContent' (API endpoint)
- `is_active` (boolean): Enable/disable model
- `user_type` (text): 'all' (used by both free and premium users)

**Why Single Endpoint?**
- Avoids rate limit issues from multiple model types
- Simpler configuration management
- Consistent performance for all users
- Easier to update when Google releases new models

---

## 🚀 Setup Instructions

### Step 1: Run Updated SQL Script

Go to Supabase SQL Editor and run the updated `supabase_insert_keys.sql`:

```sql
-- This will create the new ai_models_config table and insert default models
-- Run the entire script in Supabase SQL Editor
```

**Verify it worked:**
```sql
SELECT * FROM ai_models_config ORDER BY user_type, model_key;
```

**Expected output:**
| model_key | model_name | user_type | is_active |
|-----------|------------|-----------|-----------|
| gemini_default | gemini-2.5-flash-lite | all | true |

### Step 2: Rebuild and Deploy

```powershell
# Clean build to pick up all changes
.\gradlew clean assembleRelease
```

### Step 3: Test
- Install the new APK
- Use AI features (both free and premium)
- Should work with dynamically fetched models ✅

---

## 🎯 Use Cases - Update Without Play Store Release

### Use Case 1: Google Releases New Model
**Scenario:** Google releases `gemini-3.0-flash-lite` and you want to use it.

**Solution:**
```sql
UPDATE ai_models_config 
SET model_name = 'gemini-3.0-flash-lite'
WHERE model_key = 'gemini_default';
```

**Result:** All users (free + premium) upgraded to 3.0 within 10 minutes! No Play Store release needed.

---

### Use Case 2: Emergency Rollback to Stable Model
**Scenario:** New model has bugs, need to rollback immediately.

**Solution:**
```sql
-- Rollback to known stable version
UPDATE ai_models_config 
SET model_name = 'gemini-2.0-flash-exp'
WHERE model_key = 'gemini_default';
```

**Result:** Instant rollback for all users within 10 minutes!

---

### Use Case 3: Switch to Different Model
**Scenario:** Want to try Gemini Pro for better quality.

**Solution:**
```sql
UPDATE ai_models_config 
SET model_name = 'gemini-1.5-pro'
WHERE model_key = 'gemini_default';
```

**Result:** All users get Pro quality within 10 minutes!

---

## 📱 How It Works

### On App Launch:
1. App fetches API keys from Supabase (10 min cache)
2. App fetches AI model configs from Supabase (10 min cache)
3. Both stored locally as fallback

### On Each API Call:
1. Check if cache expired (10 minutes)
2. If expired → fetch fresh models from Supabase
3. Build endpoint: `https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent`
4. Make API request with dynamic model

### Fallback System:
- **If Supabase down:** Use locally cached model
- **If cache empty:** Use hardcoded default: `gemini-2.5-flash-lite`

---

## 🛠️ Management Queries

### Check Current Model:
```sql
SELECT model_key, model_name, user_type, is_active 
FROM ai_models_config 
WHERE is_active = true;
```

### Update Model:
```sql
UPDATE ai_models_config 
SET model_name = 'gemini-3.0-flash-lite'
WHERE model_key = 'gemini_default';
```

### Temporarily Disable Model (fallback to hardcoded default):
```sql
UPDATE ai_models_config 
SET is_active = false
WHERE model_key = 'gemini_default';
```

---

## ⚡ Cache Behavior

### Before Fix:
- **Cache Duration:** 1 hour
- **Problem:** User stuck with bad keys for 60 minutes
- **User Experience:** "Premium not configured" error for 1 hour

### After Fix:
- **Cache Duration:** 10 minutes
- **Benefit:** Issues auto-resolve in 10 minutes
- **User Experience:** Transient errors clear quickly

### Cache Locations:
1. **Memory:** `cachedKeys`, `cachedModels` (in-app runtime)
2. **SharedPreferences:** `api_keys_cache`, `ai_models_cache` (persistent)

### Force Refresh (For Testing):
```kotlin
// Clear app data in Settings
// OR wait 10 minutes
// OR rebuild app
```

---

## 🎉 Benefits Summary

| Feature | Before | After |
|---------|--------|-------|
| Model Updates | Play Store release | Instant via Supabase |
| Cache Duration | 1 hour | 10 minutes |
| Stale Key Issues | 60 min wait | 10 min auto-fix |
| API Key Rotation | Manual app update | Dynamic fetch |
| Emergency Rollback | Play Store (days) | SQL query (seconds) |
| Rate Limit Issues | Multiple endpoints | Single optimized endpoint |

---

## 🚨 Important Notes

1. **Single Endpoint Strategy:**
   - All users (free & premium) use same model: `gemini-2.5-flash-lite`
   - Avoids rate limit conflicts from multiple model types
   - Simpler to manage and monitor

2. **Model Name Updates:**
   - Always test model names before updating in production
   - Invalid model names will fall back to default: `gemini-2.5-flash-lite`
   - Keep `endpoint_suffix` as `:generateContent`

2. **Monitor Supabase:**
   - If Supabase is down, app uses cached/default model
   - No single point of failure

3. **Cache Timing:**
   - 10 minutes is balance between freshness and API load
   - Can adjust `CACHE_DURATION_MS` if needed (in `SupabaseConfig.kt`)

4. **User Impact:**
   - Users don't need to update app
   - Changes propagate within 10 minutes automatically
   - Transparent to users
   - All users get same model = consistent experience

---

## 📞 Troubleshooting

### Issue: Models not updating
**Check:**
```sql
SELECT * FROM ai_models_config WHERE is_active = true;
```

**Fix:** Verify `model_name` values are correct Google AI model names

### Issue: "Not configured" error still appearing
**Cause:** Stale local cache (will auto-expire in 10 min)

**Quick Fix:**
1. Clear app data: Settings → Apps → SendRight → Clear Data
2. OR wait 10 minutes for cache to expire

### Issue: New model not available
**Check:** Verify model exists in Google AI Studio:
- https://ai.google.dev/gemini-api/docs/models

---

## 🎯 Next Steps

1. ✅ Run updated SQL script in Supabase
2. ✅ Build and test with dynamic models
3. ✅ Deploy to production
4. 📊 Monitor model usage and performance
5. 🔄 Update models via Supabase as needed (no app release!)

---

**Questions?** Check the code in:
- `app/src/main/kotlin/com/vishruth/key1/api/SupabaseConfig.kt` (lines 260-400)
- `app/src/main/kotlin/com/vishruth/sendright/ime/smartbar/GeminiApiService.kt` (lines 440-480)

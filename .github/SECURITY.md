# Security Policy

## 🔐 Sensitive Files - DO NOT COMMIT

The following files contain sensitive data and should **NEVER** be committed to the repository:

### Already Gitignored:
- ✅ `local.properties` - Contains API keys
- ✅ `supabase_insert_keys.sql` - Contains Gemini API keys for Supabase
- ✅ `*.jks` - Keystore files
- ✅ `crowdin.properties` - Crowdin credentials
- ✅ `.env` - Environment variables

### Before Committing:
Always run:
```powershell
# Check for accidentally staged sensitive files
git status

# Search for API keys in staged files
git diff --cached | Select-String "AIzaSy"
```

## 🚨 If You Accidentally Commit Secrets

### 1. API Keys Committed
If you accidentally commit API keys:

1. **Revoke the exposed keys immediately:**
   - Go to Google AI Studio
   - Delete the compromised API key
   - Generate a new one

2. **Remove from git history:**
   ```powershell
   # Remove the file from history
   git filter-branch --force --index-filter "git rm --cached --ignore-unmatch supabase_insert_keys.sql" --prune-empty --tag-name-filter cat -- --all
   
   # Force push (WARNING: Destructive)
   git push origin --force --all
   ```

3. **Update Supabase with new keys**

### 2. Keystore Committed
If you commit your keystore file:
- Generate a new keystore immediately
- Update Play Console signing (if using App Signing by Google Play)
- Re-sign all future releases

## ✅ Safe to Commit

The following files are safe and should be committed:
- ✅ `SUPABASE_SETUP_GUIDE.md` - Setup instructions (no keys)
- ✅ `SETUP_CHECKLIST.md` - Checklist (no keys)
- ✅ `PRE_RELEASE_CHECKLIST.md` - Release guide (no keys)
- ✅ `local.properties.template` - Template without actual keys
- ✅ `app/src/main/kotlin/com/vishruth/key1/api/SupabaseConfig.kt` - Code (keys come from Supabase)

## 🔍 Pre-Commit Checklist

Before every commit, verify:
```powershell
# 1. Check what's being committed
git diff --cached

# 2. Search for API keys
git diff --cached | Select-String "AIzaSy|eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"

# 3. Check for hardcoded secrets
git diff --cached | Select-String "password|secret|token"
```

If any matches found → **DO NOT COMMIT!**

## 🛡️ Security Best Practices

1. **Never hardcode API keys in source code**
   - ✅ Use Supabase for dynamic key management
   - ✅ Use environment variables for build-time secrets
   - ✅ Use encrypted SharedPreferences for runtime secrets

2. **Regular key rotation**
   - Rotate API keys every 30-60 days
   - Update in Supabase (no app update needed!)

3. **Monitor for exposed secrets**
   - Use GitHub secret scanning (if repo is public)
   - Check git history periodically

4. **Separate development and production keys**
   - Use different keys for debug/release builds
   - Never use production keys in debug builds

## 📞 Reporting Security Issues

If you discover a security vulnerability, please email:
**[Your security contact email]**

Do NOT open a public GitHub issue for security vulnerabilities.

---

**Last Updated:** November 10, 2025

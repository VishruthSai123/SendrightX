# 🚨 Emergency: Remove Exposed API Key from Git History

## The Problem
API key `AIzaSyDFHCLr-qLTLknradWcOVZXUQtPOVrNiTo` was committed to GitHub in `SETUP_CHECKLIST.md`

Google detected it at: https://github.com/VishruthSai123/SendrightX/blob/7952114f98de9bb157e50417df26031b504368a6/SETUP_CHECKLIST.md

## URGENT: Do These Steps NOW (In Order)

### Step 1: Revoke the Compromised Key (CRITICAL - Do First!)
1. Go to https://console.cloud.google.com/apis/credentials
2. Select project: **Sendright AI** (gen-lang-client-0260220367)
3. Find key: `AIzaSyDFHCLr-qLTLknradWcOVZXUQtPOVrNiTo`
4. Click **"REGENERATE KEY"** (or Delete and create new)
5. **SAVE THE NEW KEY** somewhere secure

### Step 2: Update Supabase with New Key (2 minutes)
1. Go to your Supabase project SQL Editor
2. Run this:
```sql
UPDATE api_keys 
SET api_key = 'YOUR_NEW_KEY_FROM_STEP_1'
WHERE api_key = 'AIzaSyDFHCLr-qLTLknradWcOVZXUQtPOVrNiTo';
```

### Step 3: Fix Local File (Already Done ✅)
The key has been removed from `SETUP_CHECKLIST.md` and replaced with placeholder text.

### Step 4: Remove from Git History (CRITICAL)

**Option A: Using BFG Repo Cleaner (Recommended - Easiest)**
```powershell
# Download BFG from: https://rtyley.github.io/bfg-repo-cleaner/
# Then run:
java -jar bfg.jar --replace-text passwords.txt SendrightX

# Where passwords.txt contains:
AIzaSyDFHCLr-qLTLknradWcOVZXUQtPOVrNiTo

# Then:
cd SendrightX
git reflog expire --expire=now --all
git gc --prune=now --aggressive
git push --force
```

**Option B: Using Git Filter-Branch (Built-in)**
```powershell
cd "c:\Users\VISHRUTH\Sendright - 4.0\SendrightX"

# Backup first!
git clone --mirror . ../SendrightX-backup

# Remove the key from all commits
git filter-branch --force --index-filter "git rm --cached --ignore-unmatch SETUP_CHECKLIST.md" --prune-empty --tag-name-filter cat -- --all

# Clean up
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# Re-add the fixed file
git add SETUP_CHECKLIST.md
git commit -m "Add setup checklist (keys removed)"

# Force push (WARNING: This rewrites history!)
git push origin --force --all
git push origin --force --tags
```

**Option C: Simplest - Delete and Recreate File**
```powershell
# Remove file from history
git filter-branch --force --index-filter "git rm --cached --ignore-unmatch SETUP_CHECKLIST.md" HEAD

# Add it back clean
git add SETUP_CHECKLIST.md
git commit -m "Add setup checklist without sensitive data"
git push --force
```

### Step 5: Verify Key is Gone
```powershell
# Search entire git history for the key
git log --all --full-history --source -S "AIzaSyDFHCLr" --pretty=format:"%H"

# Should return: NOTHING (empty result)
```

### Step 6: Force Collaborators to Rebase (If Any)
If others have cloned your repo:
```
IMPORTANT: Tell all collaborators to:
1. Delete their local clone
2. Re-clone from GitHub after you've force-pushed
```

---

## ✅ After Cleanup Checklist

- [ ] Old key revoked/regenerated in Google Cloud Console
- [ ] New key updated in Supabase
- [ ] Git history cleaned (key not searchable)
- [ ] Force pushed to GitHub
- [ ] Verified key not in `git log` history
- [ ] Updated `local.properties` with new key (keep local only)
- [ ] Tested app still works with new key

---

## 🛡️ Prevention for Future

1. **Before ANY commit:**
   ```powershell
   # Search for keys in staged files
   git diff --cached | Select-String "AIzaSy"
   ```

2. **Use pre-commit hook** (create `.git/hooks/pre-commit`):
   ```bash
   #!/bin/sh
   if git diff --cached | grep -q "AIzaSy"; then
       echo "❌ ERROR: API key detected in commit!"
       echo "Remove keys before committing."
       exit 1
   fi
   ```

3. **Always use placeholders in documentation:**
   - ❌ `WHERE api_key = 'AIzaSyDFHCLr...'`
   - ✅ `WHERE api_key = 'YOUR_OLD_API_KEY_HERE'`

---

## 📞 If You Need Help

This is a critical security issue. If you're unsure about any step:
1. **Priority 1:** Revoke the key immediately (Step 1)
2. **Priority 2:** Clean git history (Step 4)
3. Everything else can wait

---

**Time-sensitive:** Do Steps 1-2 within the next 30 minutes!

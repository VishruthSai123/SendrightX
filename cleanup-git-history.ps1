# Quick Git History Cleanup Script
# Run this AFTER you've revoked the key in Google Cloud Console!

Write-Host "🚨 GIT HISTORY CLEANUP - Remove Exposed API Key" -ForegroundColor Red
Write-Host ""
Write-Host "⚠️  WARNING: This will rewrite git history!" -ForegroundColor Yellow
Write-Host "Make sure you've already:" -ForegroundColor Yellow
Write-Host "  1. ✅ Revoked the old key in Google Cloud Console" -ForegroundColor Green
Write-Host "  2. ✅ Updated Supabase with new key" -ForegroundColor Green
Write-Host ""

$continue = Read-Host "Continue with history cleanup? (yes/no)"
if ($continue -ne "yes") {
    Write-Host "Cancelled." -ForegroundColor Yellow
    exit
}

Write-Host ""
Write-Host "Creating backup..." -ForegroundColor Cyan
$backupPath = "..\SendrightX-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
git clone --mirror . $backupPath
Write-Host "✅ Backup created at: $backupPath" -ForegroundColor Green

Write-Host ""
Write-Host "Cleaning git history..." -ForegroundColor Cyan
Write-Host "This may take a few minutes..." -ForegroundColor Yellow

# Remove the file with the exposed key from history
git filter-branch --force --index-filter `
    "git rm --cached --ignore-unmatch SETUP_CHECKLIST.md" `
    --prune-empty --tag-name-filter cat -- --all

Write-Host "✅ File removed from history" -ForegroundColor Green

Write-Host ""
Write-Host "Cleaning up refs..." -ForegroundColor Cyan
git for-each-ref --format="delete %(refname)" refs/original | git update-ref --stdin
git reflog expire --expire=now --all
git gc --prune=now --aggressive

Write-Host "✅ Cleanup complete" -ForegroundColor Green

Write-Host ""
Write-Host "Re-adding fixed file..." -ForegroundColor Cyan
git add SETUP_CHECKLIST.md
git commit -m "Add setup checklist (security: keys removed)"

Write-Host ""
Write-Host "Verifying key is gone..." -ForegroundColor Cyan
$found = git log --all --source -S "AIzaSyDFHCLr" --pretty=format:"%H"
if ([string]::IsNullOrWhiteSpace($found)) {
    Write-Host "✅ SUCCESS: Key not found in git history!" -ForegroundColor Green
} else {
    Write-Host "⚠️  WARNING: Key still found in commits:" -ForegroundColor Red
    Write-Host $found
    exit 1
}

Write-Host ""
Write-Host "Ready to push!" -ForegroundColor Green
Write-Host ""
Write-Host "NEXT STEP: Force push to GitHub" -ForegroundColor Yellow
Write-Host "Run: git push origin --force --all" -ForegroundColor Cyan
Write-Host ""
Write-Host "⚠️  WARNING: This will rewrite GitHub history!" -ForegroundColor Red
Write-Host "Anyone who cloned the repo must delete and re-clone." -ForegroundColor Yellow
Write-Host ""

$push = Read-Host "Push now? (yes/no)"
if ($push -eq "yes") {
    Write-Host ""
    Write-Host "Pushing to GitHub..." -ForegroundColor Cyan
    git push origin --force --all
    git push origin --force --tags
    Write-Host ""
    Write-Host "✅ DONE! Check GitHub to confirm key is gone." -ForegroundColor Green
    Write-Host "URL: https://github.com/VishruthSai123/SendrightX/blob/main/SETUP_CHECKLIST.md" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "Push when ready with:" -ForegroundColor Yellow
    Write-Host "  git push origin --force --all" -ForegroundColor Cyan
    Write-Host "  git push origin --force --tags" -ForegroundColor Cyan
}

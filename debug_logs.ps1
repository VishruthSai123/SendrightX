# Debug logs for AI Workspace state management
Write-Host "Starting AI Workspace debug logs..." -ForegroundColor Green
Write-Host "This will show logs from the AIWorkspaceManager operations." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host ""

# Clear existing logs and start monitoring
adb logcat -c
adb logcat -s "AIWorkspaceManager" -s "AIWorkspaceScreen"
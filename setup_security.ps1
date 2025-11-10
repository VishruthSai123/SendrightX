# ⚡ Quick Setup Script for SendrightX Security Fixes
# Run this after code fixes are applied

Write-Host "🔒 SendrightX Security Setup" -ForegroundColor Cyan
Write-Host "================================`n" -ForegroundColor Cyan

# Check if local.properties exists
$localPropsPath = "local.properties"
if (Test-Path $localPropsPath) {
    Write-Host "✅ local.properties found" -ForegroundColor Green
    
    # Check if Supabase credentials are configured
    $content = Get-Content $localPropsPath -Raw
    if ($content -match "SUPABASE_URL=" -and $content -match "SUPABASE_ANON_KEY=") {
        Write-Host "✅ Supabase credentials found in local.properties" -ForegroundColor Green
        
        # Check if they're placeholder values
        if ($content -match "your_supabase_project_url_here" -or $content -match "your_supabase_anon_key_here") {
            Write-Host "⚠️  WARNING: Placeholder values detected!" -ForegroundColor Yellow
            Write-Host "   Please update local.properties with your actual Supabase credentials`n" -ForegroundColor Yellow
        } else {
            Write-Host "✅ Supabase credentials appear to be configured`n" -ForegroundColor Green
        }
    } else {
        Write-Host "❌ Supabase credentials NOT found in local.properties!" -ForegroundColor Red
        Write-Host "   Add these lines to local.properties:" -ForegroundColor Yellow
        Write-Host "   SUPABASE_URL=your_project_url_here" -ForegroundColor White
        Write-Host "   SUPABASE_ANON_KEY=your_anon_key_here`n" -ForegroundColor White
    }
} else {
    Write-Host "⚠️  local.properties NOT found!" -ForegroundColor Yellow
    Write-Host "   Creating from template...`n" -ForegroundColor Yellow
    
    if (Test-Path "local.properties.template") {
        Copy-Item "local.properties.template" "local.properties"
        Write-Host "✅ Created local.properties from template" -ForegroundColor Green
        Write-Host "   ⚠️  IMPORTANT: Edit local.properties and add your Supabase credentials!`n" -ForegroundColor Yellow
    } else {
        Write-Host "❌ local.properties.template not found!" -ForegroundColor Red
        exit 1
    }
}

# Check .gitignore
Write-Host "Checking .gitignore configuration..." -ForegroundColor Cyan
$gitignorePath = ".gitignore"
if (Test-Path $gitignorePath) {
    $gitignoreContent = Get-Content $gitignorePath -Raw
    if ($gitignoreContent -match "local\.properties") {
        Write-Host "✅ local.properties is in .gitignore`n" -ForegroundColor Green
    } else {
        Write-Host "⚠️  WARNING: local.properties NOT in .gitignore!" -ForegroundColor Yellow
        Write-Host "   This is a security risk - credentials could be committed!`n" -ForegroundColor Red
    }
} else {
    Write-Host "⚠️  .gitignore not found`n" -ForegroundColor Yellow
}

# Check if Supabase credentials are in BuildConfig
Write-Host "Checking build configuration..." -ForegroundColor Cyan
$buildGradlePath = "app\build.gradle.kts"
if (Test-Path $buildGradlePath) {
    $buildContent = Get-Content $buildGradlePath -Raw
    if ($buildContent -match "SUPABASE_URL" -and $buildContent -match "SUPABASE_ANON_KEY") {
        Write-Host "✅ BuildConfig configured for Supabase credentials`n" -ForegroundColor Green
    } else {
        Write-Host "❌ BuildConfig NOT configured for Supabase!" -ForegroundColor Red
        Write-Host "   build.gradle.kts needs updating`n" -ForegroundColor Red
    }
} else {
    Write-Host "❌ app/build.gradle.kts not found`n" -ForegroundColor Red
}

# Test build
Write-Host "`n📦 Testing build..." -ForegroundColor Cyan
Write-Host "Running: .\gradlew clean assembleDebug`n" -ForegroundColor White

try {
    & .\gradlew clean assembleDebug 2>&1 | Tee-Object -Variable buildOutput
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n✅ Build successful!" -ForegroundColor Green
        
        # Check if APK was created
        $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
        if (Test-Path $apkPath) {
            Write-Host "✅ Debug APK created: $apkPath" -ForegroundColor Green
            
            # Get APK size
            $apkSize = (Get-Item $apkPath).Length / 1MB
            Write-Host "   APK size: $([math]::Round($apkSize, 2)) MB`n" -ForegroundColor White
        }
    } else {
        Write-Host "`n❌ Build failed!" -ForegroundColor Red
        Write-Host "   Check error messages above`n" -ForegroundColor Red
        
        # Check for common errors
        if ($buildOutput -match "SUPABASE_URL") {
            Write-Host "💡 TIP: Make sure local.properties has SUPABASE_URL configured" -ForegroundColor Yellow
        }
        if ($buildOutput -match "SUPABASE_ANON_KEY") {
            Write-Host "💡 TIP: Make sure local.properties has SUPABASE_ANON_KEY configured" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "`n❌ Build error: $_`n" -ForegroundColor Red
}

# Summary
Write-Host "`n================================" -ForegroundColor Cyan
Write-Host "📋 NEXT STEPS:" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. 🔑 Add your Supabase credentials to local.properties" -ForegroundColor Yellow
Write-Host "   - Go to: https://app.supabase.com/project/YOUR_PROJECT/settings/api" -ForegroundColor White
Write-Host "   - Copy Project URL and ANON key" -ForegroundColor White
Write-Host ""
Write-Host "2. 🔄 ROTATE your exposed Supabase ANON key immediately" -ForegroundColor Red
Write-Host "   - Click 'Reset' on the ANON key in Supabase dashboard" -ForegroundColor White
Write-Host "   - Update local.properties with new key" -ForegroundColor White
Write-Host ""
Write-Host "3. 🧹 Clean Git history (removes exposed credentials)" -ForegroundColor Yellow
Write-Host "   - BACKUP your repo first!" -ForegroundColor Red
Write-Host "   - See SECURITY_FIXES_COMPLETED.md for commands" -ForegroundColor White
Write-Host ""
Write-Host "4. 🔒 Set up Row Level Security on Supabase" -ForegroundColor Yellow
Write-Host "   - See SECURITY_FIXES_COMPLETED.md for SQL commands" -ForegroundColor White
Write-Host ""
Write-Host "5. ✅ Verify credentials NOT in APK" -ForegroundColor Yellow
Write-Host "   - Run: .\gradlew assembleRelease" -ForegroundColor White
Write-Host "   - Extract APK and search for 'supabase.co'" -ForegroundColor White
Write-Host "   - Should find NOTHING" -ForegroundColor White
Write-Host ""
Write-Host "📖 See SECURITY_FIXES_COMPLETED.md for detailed instructions" -ForegroundColor Cyan
Write-Host ""

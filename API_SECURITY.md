# 🔐 API Security Setup

## Overview

This project integrates with Gemini AI API for text transformation features. To protect sensitive API keys from being exposed in version control, we use a secure configuration system.

## ⚠️ Security Notice

**API keys are stored locally and are NOT committed to git!**

## 🛠️ Setup Instructions

### 1. Configure API Key

1. Copy the template file:
   ```bash
   cp local.properties.template local.properties
   ```

2. Edit `local.properties` and add your Gemini API key:
   ```properties
   # Get your API key from: https://makersuite.google.com/app/apikey
   GEMINI_API_KEY=your_actual_api_key_here
   ```

### 2. Get Gemini API Key

1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Sign in with your Google account
3. Create a new API key
4. Copy the key to your `local.properties` file

### 3. Verify Setup

1. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

2. If the API key is missing, you'll see an error message in the app.

## 🔒 Security Features

- ✅ **API keys stored in `local.properties`** (gitignored)
- ✅ **BuildConfig integration** for compile-time security
- ✅ **Template file** for easy developer setup
- ✅ **Runtime validation** to ensure keys are configured
- ✅ **Rate limiting** to prevent API abuse

## 📁 File Structure

```
├── local.properties          # ❌ Gitignored - Contains real API keys
├── local.properties.template # ✅ Tracked - Template for setup
└── app/build.gradle.kts     # ✅ Tracked - Reads from local.properties
```

## 🚨 Important Notes

1. **Never commit `local.properties`** - It's automatically gitignored
2. **Each developer needs their own API key** in their local setup
3. **Production builds** should use environment variables or secure build systems
4. **The template file** helps new developers set up correctly

## 🔧 Troubleshooting

### "API key not configured" error
- Ensure `local.properties` exists in the root directory
- Verify `GEMINI_API_KEY` is set with a valid key
- Clean and rebuild the project

### Build errors related to BuildConfig
- Make sure `buildFeatures { buildConfig = true }` is enabled
- Sync the project after modifying `local.properties`

## 📊 API Usage

The magic wand feature uses the Gemini 2.0 Flash API with:
- **Rate limiting**: 1 second between requests
- **Error handling**: Graceful failure with user feedback
- **Text transformation**: Complete field text processing

For API documentation, visit: [Gemini API Docs](https://ai.google.dev/docs)

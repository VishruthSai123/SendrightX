# 🔄 Fallback API Keys Setup Guide

## Overview

SendRight now supports fallback API keys to ensure high availability and reliability of AI features. If your primary API key fails, the system automatically tries backup keys.

## 🚀 Quick Setup

### 1. Configure Multiple API Keys

Edit your `local.properties` file:

```properties
# Primary API Key (Required)
GEMINI_API_KEY=your_primary_api_key_here

# Fallback API Keys (Optional but recommended)
GEMINI_API_KEY_FALLBACK_1=your_backup_key_1_here
GEMINI_API_KEY_FALLBACK_2=your_backup_key_2_here
GEMINI_API_KEY_FALLBACK_3=your_backup_key_3_here
```

### 2. Get Multiple API Keys

**Option A: Single Google Account**
1. Visit [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Create multiple API keys
3. Each key shares the same quota but provides redundancy

**Option B: Multiple Google Accounts (Recommended)**
1. Use different Google accounts for each API key
2. Each account gets separate rate limits
3. Better load distribution and higher total quota

## 🔧 How It Works

### Automatic Fallback Logic

1. **Primary Key First**: Always tries primary key first
2. **Smart Retry**: Retries temporary failures (503, 500, timeouts)
3. **Key Rotation**: Switches to fallback keys on authentication failures
4. **Exponential Backoff**: Increasing delays between retries

### Error Handling

| Error Type | Behavior |
|------------|----------|
| **503 Service Unavailable** | Retry with same key, then try fallback |
| **401/403 Authentication** | Immediately try next key |
| **429 Rate Limited** | Retry with exponential backoff |
| **Timeout** | Retry with same key, then try fallback |

### Key Rotation Strategy

```
Request Fails (403/401) → Try Fallback Key 1 → Try Fallback Key 2 → Try Fallback Key 3 → Return Error
```

## 📊 Benefits

### ✅ **High Availability**
- Service continues even if primary key fails
- Automatic failover without user intervention
- Up to 4 API keys for maximum redundancy

### ✅ **Load Distribution**
- Distribute requests across multiple quotas
- Reduce individual key rate limiting
- Better performance during peak usage

### ✅ **Error Recovery**
- Intelligent retry logic for temporary failures
- Automatic recovery when services restore
- User-friendly error messages

## 🛠️ Configuration Examples

### Minimal Setup (Basic Protection)
```properties
GEMINI_API_KEY=primary_key_here
GEMINI_API_KEY_FALLBACK_1=backup_key_here
```

### Recommended Setup (High Availability)
```properties
GEMINI_API_KEY=primary_key_account_1
GEMINI_API_KEY_FALLBACK_1=backup_key_account_1  
GEMINI_API_KEY_FALLBACK_2=primary_key_account_2
GEMINI_API_KEY_FALLBACK_3=backup_key_account_2
```

### Enterprise Setup (Maximum Redundancy)
```properties
GEMINI_API_KEY=prod_account_primary
GEMINI_API_KEY_FALLBACK_1=prod_account_backup
GEMINI_API_KEY_FALLBACK_2=dev_account_primary
GEMINI_API_KEY_FALLBACK_3=testing_account_key
```

## 🚨 Best Practices

### Security
- Never commit API keys to version control
- Use different keys for different environments
- Regularly rotate API keys for security

### Reliability
- Test all API keys before deploying
- Monitor usage across different keys
- Use keys from different Google accounts when possible

### Performance
- Keep primary key as your fastest/most reliable
- Order fallback keys by preference/reliability
- Monitor which keys are being used most

## 🔍 Troubleshooting

### No API Keys Working
```
Error: "All API keys failed"
```
**Solutions:**
- Check all API keys are valid
- Verify internet connectivity
- Check Google AI service status
- Ensure keys have proper permissions

### Only Some Keys Working
```
Error: Falls back to secondary keys frequently
```
**Solutions:**
- Check primary key quota usage
- Verify primary key permissions
- Monitor primary key error rates

### Frequent Fallbacks
- This is normal during high traffic
- Consider upgrading API quotas
- Monitor which keys fail most often

## 📈 Monitoring

The system provides information about API key usage:

```kotlin
// Get current API key status
val status = GeminiApiService.getApiKeyInfo()
// Returns: "4 API keys configured (1 primary + 3 fallbacks)"

// Reset to primary key (for testing)
GeminiApiService.resetToPrimaryKey()
```

## 🎯 Usage Scenarios

### Development
- Primary: Development API key
- Fallback: Testing API key

### Production
- Primary: High-quota production key
- Fallbacks: Multiple backup keys from different accounts

### High-Traffic Applications
- Use all 4 keys from different Google accounts
- Distribute load across multiple quotas
- Maximum reliability and performance

---

## 🆘 Support

If you encounter issues with fallback API keys:

1. Check the [API_SECURITY.md](./API_SECURITY.md) for basic setup
2. Verify your `local.properties` configuration
3. Test individual API keys manually
4. Check Google AI Studio for key status and quotas

Remember: Even with just one working API key, SendRight will function normally. Fallback keys are for improved reliability and performance.
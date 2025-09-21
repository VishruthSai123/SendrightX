# Server-Side Purchase Verification Setup

This document explains how to set up automatic server-side verification for in-app purchases to prevent client manipulation and handle unusual refunds.

## Overview

The system now uses **server-side verification** instead of client-only validation:

### ✅ **Before (Vulnerable)**
- Client acknowledges purchase locally
- No Google Play API validation
- Vulnerable to manipulation and refunds

### ✅ **Now (Secure)**
- Server validates with Google Play Developer API
- Real-time webhook notifications for changes
- Automatic refund and cancellation handling
- Periodic sync to catch missed events

## Components

### 1. Server-Side Verification Service
**File**: `server/verification/PurchaseVerificationService.kt`
- Validates purchase tokens with Google Play Developer API
- Uses OAuth2 service account authentication
- Returns authoritative subscription status

### 2. Purchase Validation API
**File**: `server/api/PurchaseValidationApi.kt`
- Provides endpoints for purchase validation
- Manages user entitlements database
- Handles webhook notifications automatically

### 3. Entitlement Database
**File**: `server/api/EntitlementDatabase.kt`
- Stores user subscription entitlements
- Tracks subscription status and expiry
- Provides fast local lookup

### 4. Enhanced Webhook Handler
**File**: `server/webhooks/GooglePlayWebhookHandler.kt`
- Processes real-time Google Play notifications
- Automatically revokes access on refunds/cancellations
- Integrated with validation API

### 5. Updated Client Integration
**File**: `app/src/main/kotlin/com/vishruth/key1/billing/BillingManager.kt`
- Sends purchases to server for validation
- Periodic sync with server entitlements
- Fallback to local verification in development

## Setup Instructions

### Step 1: Google Play Developer API Setup

1. **Create Service Account**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create or select your project
   - Navigate to IAM & Admin > Service Accounts
   - Create new service account

2. **Generate Service Account Key**:
   - Click on your service account
   - Go to Keys tab
   - Add Key > Create new key > JSON
   - Download the JSON file securely

3. **Enable Google Play Developer API**:
   - Go to APIs & Services > Library
   - Search for "Google Play Developer API"
   - Enable the API

4. **Configure Permissions**:
   - In Google Play Console > Setup > API access
   - Link your service account
   - Grant necessary permissions

### Step 2: Server Deployment

The server components can be deployed as:

#### Option A: Cloud Functions (Recommended)
```javascript
// Cloud Function example
const { PurchaseValidationApi } = require('./api/PurchaseValidationApi');

exports.validatePurchase = async (req, res) => {
  const api = new PurchaseValidationApi();
  const result = await api.validateSubscriptionPurchase(req.body);
  res.json(result);
};

exports.handleWebhook = async (req, res) => {
  const handler = new GooglePlayWebhookHandler();
  const result = await handler.handleWebhook(req.body);
  res.json(result);
};
```

#### Option B: Express.js Server
```javascript
const express = require('express');
const app = express();

app.post('/api/validate-purchase', async (req, res) => {
  // Handle purchase validation
});

app.post('/api/webhook', async (req, res) => {
  // Handle Google Play webhooks
});
```

#### Option C: Android Backend (Local Server)
- Run validation API as part of your Android app
- Use for development or offline scenarios
- Initialize `EntitlementDatabase` with app context

### Step 3: Environment Configuration

Set up service account credentials:

```bash
# Option 1: Environment Variable
export GOOGLE_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'

# Option 2: File Path
export GOOGLE_SERVICE_ACCOUNT_PATH='/path/to/service-account.json'
```

### Step 4: Webhook Configuration

1. **Set Webhook URL** in Google Play Console:
   - Go to Monetization > Subscriptions
   - Set webhook URL: `https://your-server.com/api/webhook`

2. **Configure Notification Types**:
   - Subscription purchases
   - Subscription cancellations
   - Subscription expirations
   - Subscription recoveries

### Step 5: Client Configuration

Update your Android app:

```kotlin
// Initialize EntitlementDatabase
EntitlementDatabase.getInstance(context)

// BillingManager automatically uses server verification
val billingManager = BillingManager(context)

// Force sync with server when needed
billingManager.forceSyncWithServer()
```

## Security Features

### 1. Purchase Token Validation
- Every purchase token validated with Google Play API
- Server-side verification prevents client manipulation
- Only legitimate purchases grant access

### 2. Real-time Webhook Processing
- Immediate notification of subscription changes
- Automatic access revocation on refunds
- Grace period handling for failed payments

### 3. Periodic Sync
- Background sync every 30 minutes
- Catches webhook notifications that might be missed
- Ensures local state matches server truth

### 4. Fallback Mechanisms
- Local verification for development
- Graceful handling of network failures
- Maintains user experience during outages

## Monitoring and Debugging

### Server Logs
Monitor these events:
- `✅ Purchase validated successfully`
- `❌ Server validation failed`
- `🔄 Subscription state mismatch detected`
- `🚨 SUBSCRIPTION EXPIRED`

### Client Logs
Watch for:
- `🔐 Starting server-side purchase verification`
- `🎉 Premium access granted after server verification`
- `⚠️ Falling back to local verification`

### Common Issues

1. **Service Account Permissions**:
   - Ensure API is enabled
   - Check service account has proper permissions
   - Verify JSON credentials are valid

2. **Webhook Delivery**:
   - Test webhook endpoint accessibility
   - Check Google Play Console webhook logs
   - Verify signature validation (if enabled)

3. **Network Connectivity**:
   - Handle offline scenarios gracefully
   - Implement retry mechanisms
   - Cache validation results appropriately

## Production Checklist

- [ ] Service account properly configured
- [ ] Google Play Developer API enabled
- [ ] Webhook endpoint deployed and accessible
- [ ] Environment variables set securely
- [ ] Fallback mechanisms tested
- [ ] Monitoring and logging configured
- [ ] Load testing completed
- [ ] Security review passed

## Benefits

### ✅ **Security**
- Prevents client-side purchase manipulation
- Validates every purchase with Google Play
- Automatic fraud detection

### ✅ **Reliability**
- Real-time refund and cancellation handling
- Webhook backup with periodic sync
- Graceful failure handling

### ✅ **Compliance**
- Proper Google Play API usage
- Audit trail for all transactions
- Automatic compliance with policy changes

### ✅ **User Experience**
- Fast local cache for quick access checks
- Seamless subscription restoration
- Consistent state across devices

This implementation ensures that your in-app purchases are automatically verified and unusual refunds are handled immediately, providing a secure and reliable subscription system.
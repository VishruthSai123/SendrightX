# Google Play Developer API Webhook Implementation

This implementation handles real-time subscription notifications from Google Play to ensure proper subscription state management.

## 🎯 What This Solves

### Google Play Notification Types Handled:

1. **SUBSCRIPTION_CANCELED (Type 2)**
   - ✅ **Keep Pro access** - User cancelled but paid period continues
   - 📝 Note cancellation in database
   - 🔄 User retains access until billing period ends

2. **SUBSCRIPTION_EXPIRED (Type 13)**
   - ❌ **Revoke Pro access** - Subscription actually expired
   - 💳 Paid period has ended
   - 🚫 User loses Pro features immediately

## 📁 Files Created

### 1. Kotlin Handler (`server/webhooks/GooglePlayWebhookHandler.kt`)
- Complete webhook processing logic
- Firestore integration
- Handles all subscription notification types
- Can be used in Android backend services

### 2. Cloud Functions Implementation (`server/cloud-functions/index.ts`)
- Firebase Cloud Functions deployment
- Real-time webhook endpoint
- Scalable server-side processing

### 3. Deployment Configuration (`server/cloud-functions/package.json`)
- Dependencies for Firebase Functions
- Build and deployment scripts

## 🚀 Deployment Instructions

### Option 1: Firebase Cloud Functions (Recommended)

1. **Install Firebase CLI**
   ```bash
   npm install -g firebase-tools
   ```

2. **Navigate to cloud functions directory**
   ```bash
   cd server/cloud-functions
   ```

3. **Install dependencies**
   ```bash
   npm install
   ```

4. **Initialize Firebase (if not done)**
   ```bash
   firebase init functions
   ```

5. **Deploy the webhook function**
   ```bash
   npm run deploy
   ```

6. **Note the deployed URL**
   ```
   https://YOUR_PROJECT.cloudfunctions.net/handleGooglePlayWebhook
   ```

### Option 2: Custom Server Implementation

1. **Use the Kotlin handler** in your existing backend
2. **Create REST endpoint** that calls `GooglePlayWebhookHandler.handleWebhook()`
3. **Deploy to your server** (AWS, GCP, Azure, etc.)

## ⚙️ Google Play Console Configuration

### 1. Set Up Real-time Developer Notifications

1. Go to **Google Play Console**
2. Navigate to **Monetization** → **Subscriptions**
3. Go to **Real-time developer notifications**
4. Set **Topic name**: `projects/YOUR_PROJECT/topics/play-notifications`
5. Set **Webhook URL**: Your deployed function URL

### 2. Configure Pub/Sub Topic (for Cloud Functions)

```bash
# Create Pub/Sub topic
gcloud pubsub topics create play-notifications

# Create subscription
gcloud pubsub subscriptions create play-webhook-subscription --topic=play-notifications
```

### 3. Set Up Service Account

1. Create service account in Google Cloud Console
2. Grant permissions:
   - `pubsub.subscriber`
   - `firebase.admin`
3. Download service account key
4. Set environment variable: `GOOGLE_APPLICATION_CREDENTIALS`

## 🔐 Security Considerations

### 1. Webhook Signature Verification

```kotlin
// Implement in GooglePlayWebhookHandler.verifySignature()
private fun verifySignature(data: String, signature: String): Boolean {
    // Use your webhook signing key from Google Play Console
    val secretKey = "YOUR_WEBHOOK_SIGNING_KEY"
    val expectedSignature = computeHmacSha256(data, secretKey)
    return expectedSignature == signature
}
```

### 2. Authentication

```typescript
// Add to Cloud Functions
export const handleGooglePlayWebhook = https.onRequest({
  cors: false,
  secrets: ["WEBHOOK_SECRET"]
}, async (req, res) => {
  // Verify webhook secret
  const providedSecret = req.headers['x-webhook-secret'];
  if (providedSecret !== process.env.WEBHOOK_SECRET) {
    res.status(401).send('Unauthorized');
    return;
  }
  // ... rest of handler
});
```

## 🧪 Testing

### 1. Local Testing

```bash
# Start Firebase emulator
cd server/cloud-functions
npm run serve

# Test webhook with curl
curl -X POST http://localhost:5001/YOUR_PROJECT/us-central1/handleGooglePlayWebhook \
  -H "Content-Type: application/json" \
  -d '{"message":{"data":"BASE64_ENCODED_NOTIFICATION"}}'
```

### 2. Google Play Test Notifications

1. Go to Google Play Console
2. Navigate to **Testing** → **Internal testing**
3. Test subscription cancellations and expirations
4. Monitor webhook calls in Firebase Functions logs

## 📊 Monitoring & Logging

### Cloud Functions Logs
```bash
# View logs
firebase functions:log

# Real-time logs
firebase functions:log --follow
```

### Firestore Monitoring
- Check user documents for `subscriptionStatus` updates
- Monitor `lastNotificationProcessed` timestamps
- Verify subscription records in collections

## 🔄 Integration with Existing Code

The webhook handlers automatically update:

1. **User documents** in Firestore
   - `subscriptionStatus`: "pro", "free", "canceled_but_active", etc.
   - Timestamp fields for each notification type

2. **Subscription records** in user subcollections
   - Status tracking for each purchase token
   - Historical notification processing

3. **Real-time UI updates** through existing StateFlow observers
   - Subscription state changes propagate to keyboard components
   - Magic wand panel, AI limit panel update automatically

## ✅ Verification

After deployment, verify:

1. **Webhook endpoint** responds to test requests
2. **Google Play Console** shows webhook URL as active
3. **Test subscription flows** generate proper notifications
4. **Firestore data** updates correctly
5. **App UI** reflects subscription changes in real-time

## 🔧 Troubleshooting

### Common Issues:

1. **Webhook not receiving notifications**
   - Check Google Play Console configuration
   - Verify Pub/Sub topic setup
   - Confirm webhook URL is accessible

2. **Signature verification fails**
   - Verify webhook signing key
   - Check signature computation algorithm

3. **Database updates fail**
   - Confirm Firestore permissions
   - Check service account credentials
   - Verify collection/document structure

### Debug Logs:
```bash
# Check Cloud Functions logs
firebase functions:log --limit 50

# Check Firestore activity
firebase firestore:activities
```

This implementation ensures that subscription state changes are handled immediately and accurately, preventing users from losing or inappropriately retaining Pro access.

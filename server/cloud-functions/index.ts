/**
 * Google Cloud Functions implementation for Google Play Developer API webhooks
 * 
 * Deploy this to handle real-time subscription notifications from Google Play
 * 
 * Setup Instructions:
 * 1. Deploy this Cloud Function
 * 2. Configure the webhook URL in Google Play Console
 * 3. Set up proper authentication and security
 */

import { https } from 'firebase-functions';
import { firestore } from 'firebase-admin';
import { initializeApp } from 'firebase-admin/app';

// Initialize Firebase Admin
initializeApp();
const db = firestore();

// Google Play notification types
const NOTIFICATION_TYPES = {
  SUBSCRIPTION_RECOVERED: 1,
  SUBSCRIPTION_CANCELED: 2,
  SUBSCRIPTION_PURCHASED: 3,
  SUBSCRIPTION_ON_HOLD: 5,
  SUBSCRIPTION_IN_GRACE_PERIOD: 6,
  SUBSCRIPTION_RESTARTED: 7,
  SUBSCRIPTION_PRICE_CHANGE_CONFIRMED: 8,
  SUBSCRIPTION_DEFERRED: 9,
  SUBSCRIPTION_PAUSED: 10,
  SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED: 11,
  SUBSCRIPTION_REVOKED: 12,
  SUBSCRIPTION_EXPIRED: 13
};

/**
 * Cloud Function to handle Google Play Developer API webhooks
 * 
 * URL: https://YOUR_PROJECT.cloudfunctions.net/handleGooglePlayWebhook
 * Method: POST
 * Content-Type: application/json
 */
export const handleGooglePlayWebhook = https.onRequest(async (req, res) => {
  try {
    console.log('Received Google Play webhook notification');
    
    // Verify this is a POST request
    if (req.method !== 'POST') {
      res.status(405).send('Method not allowed');
      return;
    }
    
    // Parse the notification
    const message = req.body.message;
    if (!message || !message.data) {
      res.status(400).send('Invalid notification format');
      return;
    }
    
    // Decode the base64 message data
    const notificationData = Buffer.from(message.data, 'base64').toString();
    const notification = JSON.parse(notificationData);
    
    console.log('Parsed notification:', notification);
    
    // Process subscription notifications
    if (notification.subscriptionNotification) {
      await processSubscriptionNotification(notification);
    }
    
    res.status(200).send('Webhook processed successfully');
    
  } catch (error) {
    console.error('Error processing webhook:', error);
    res.status(500).send('Error processing webhook');
  }
});

/**
 * Process subscription notification based on type
 */
async function processSubscriptionNotification(notification: any) {
  const subNotification = notification.subscriptionNotification;
  const notificationType = subNotification.notificationType;
  const purchaseToken = subNotification.purchaseToken;
  const subscriptionId = subNotification.subscriptionId;
  
  console.log(`Processing notification type: ${notificationType}`);
  console.log(`Purchase token: ${purchaseToken}`);
  console.log(`Subscription ID: ${subscriptionId}`);
  
  // Find user by purchase token
  const userId = await findUserByPurchaseToken(purchaseToken);
  if (!userId) {
    console.warn(`User not found for purchase token: ${purchaseToken}`);
    return;
  }
  
  const timestamp = Date.now();
  
  switch (notificationType) {
    case NOTIFICATION_TYPES.SUBSCRIPTION_CANCELED:
      // User cancelled but still has access during paid period
      console.log('🟡 SUBSCRIPTION_CANCELED: User cancelled but retains access');
      await updateUserSubscription(userId, 'canceled_but_active', {
        subscriptionCanceledAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'canceled_but_active');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_EXPIRED:
      // Subscription actually expired - revoke access
      console.log('🔴 SUBSCRIPTION_EXPIRED: Revoking Pro access');
      await updateUserSubscription(userId, 'free', {
        subscriptionExpiredAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'expired');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_REVOKED:
      // Google revoked subscription (refund, chargeback)
      console.log('🔴 SUBSCRIPTION_REVOKED: Google revoked subscription');
      await updateUserSubscription(userId, 'free', {
        subscriptionRevokedAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'revoked');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_RECOVERED:
      // User recovered from grace period or on-hold
      console.log('🟢 SUBSCRIPTION_RECOVERED: Restoring Pro access');
      await updateUserSubscription(userId, 'pro', {
        subscriptionRecoveredAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'active');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_PURCHASED:
      // New subscription purchased
      console.log('🟢 SUBSCRIPTION_PURCHASED: New subscription activated');
      await updateUserSubscription(userId, 'pro', {
        subscriptionPurchasedAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'active');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_ON_HOLD:
      // Payment issues - keep access but note status
      console.log('🟡 SUBSCRIPTION_ON_HOLD: Payment issues detected');
      await updateUserSubscription(userId, 'on_hold', {
        subscriptionOnHoldAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'on_hold');
      break;
      
    case NOTIFICATION_TYPES.SUBSCRIPTION_IN_GRACE_PERIOD:
      // Grace period - keep access
      console.log('🟡 SUBSCRIPTION_IN_GRACE_PERIOD: Grace period active');
      await updateUserSubscription(userId, 'grace_period', {
        subscriptionGracePeriodAt: timestamp
      });
      await updateSubscriptionRecord(userId, purchaseToken, 'grace_period');
      break;
      
    default:
      console.log(`Unhandled notification type: ${notificationType}`);
  }
}

/**
 * Find user ID by purchase token
 */
async function findUserByPurchaseToken(purchaseToken: string): Promise<string | null> {
  try {
    const querySnapshot = await db.collectionGroup('subscriptions')
      .where('purchaseToken', '==', purchaseToken)
      .limit(1)
      .get();
    
    if (!querySnapshot.empty) {
      const doc = querySnapshot.docs[0];
      const pathParts = doc.ref.path.split('/');
      return pathParts[1]; // Extract user ID from path: users/{userId}/subscriptions/{docId}
    }
    
    return null;
  } catch (error) {
    console.error('Error finding user by purchase token:', error);
    return null;
  }
}

/**
 * Update user's subscription status
 */
async function updateUserSubscription(
  userId: string, 
  status: string, 
  additionalFields: Record<string, any> = {}
) {
  try {
    await db.collection('users').doc(userId).update({
      subscriptionStatus: status,
      lastNotificationProcessed: Date.now(),
      ...additionalFields
    });
    
    console.log(`✅ Updated user ${userId} subscription status to: ${status}`);
  } catch (error) {
    console.error('Error updating user subscription:', error);
  }
}

/**
 * Update subscription record
 */
async function updateSubscriptionRecord(
  userId: string,
  purchaseToken: string,
  status: string
) {
  try {
    await db.collection('users')
      .doc(userId)
      .collection('subscriptions')
      .doc(purchaseToken)
      .update({
        status: status,
        lastUpdated: Date.now()
      });
      
    console.log(`✅ Updated subscription record for user ${userId}`);
  } catch (error) {
    console.error('Error updating subscription record:', error);
  }
}

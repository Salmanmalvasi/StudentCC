# 🔍 Firebase In-App Messaging Debug Guide

## 📱 Testing Your Firebase In-App Messages

### Method 1: Test with the Built-in Test Function

When you open the app, it will automatically send a test notification and in-app message after 3 seconds. Look for:

1. **System Notification**: Should appear in notification bar with "🧪 Test Notification"
2. **In-App Message**: Should be stored locally and viewable in the app

### Method 2: Check Logs in Android Studio

1. **Connect your phone via USB**
2. **Open Android Studio** → View → Tool Windows → Logcat
3. **Filter by tag**: Search for these tags:
   - `FirebaseMessaging` - Main messaging helper logs
   - `VTOPFCMService` - FCM service logs 
   - `InAppMessageManager` - Message storage logs
   - `MainActivity` - App initialization logs

4. **Look for these log messages**:
   ```
   🚀 Initializing Firebase Messaging...
   ✅ FCM Registration Token: [your-token]
   ✅ Firebase Messaging initialization completed!
   🧪 Sending test message...
   🔔 Creating notification - Title: 🧪 Test Notification
   📺 Creating notification channel: vtop_notifications
   ✅ Notification displayed with ID: [id]
   ✅ Test message sent successfully!
   ```

### Method 3: Send Firebase Message from Console

1. **Go to Firebase Console**: https://console.firebase.google.com
2. **Select your project** → Cloud Messaging
3. **Send your first message**:
   - **Title**: "Hello from Firebase!"
   - **Text**: "Testing in-app messaging"
   - **Target**: Topic → "announcements" (or "updates", "maintenance")
   - **Additional options** → Custom data:
     - Key: `type`, Value: `announcement`
     - Key: `priority`, Value: `high`
     - Key: `title`, Value: "Hello from Firebase!"
     - Key: `body`, Value: "Testing in-app messaging"

### Method 4: Check In-App Messages Activity

1. **Open the app**
2. **Look for in-app messages section** (if available in your UI)
3. **Check stored messages** - messages should persist between app restarts

## 🔧 Troubleshooting Steps

### If No Notification Appears:

1. **Check Notification Permissions**:
   - Android Settings → Apps → StudentCC → Notifications → Allow
   - Make sure "VTOP Notifications" channel is enabled

2. **Check Do Not Disturb**:
   - Disable Do Not Disturb mode
   - Check notification importance level

3. **Verify FCM Token**:
   - Look in logs for "FCM Registration Token"
   - Token should be a long string starting with something like "c8F3..."

### If In-App Messages Don't Show:

1. **Check Message Storage**:
   - Messages are stored in SharedPreferences
   - Look for InAppMessageManager logs
   - Restart app to see if messages persist

2. **Verify Message Creation**:
   - Look for "💾 Creating in-app message with ID:" in logs
   - Check if addMessage calls succeed

### If Firebase Console Messages Don't Reach App:

1. **Verify Topic Subscription**:
   - Look for "✅ Subscribed to topic: announcements" in logs
   - App subscribes to: announcements, updates, maintenance

2. **Check Message Format**:
   - Make sure you include custom data fields
   - Use correct topic names

3. **Verify App is in Foreground**:
   - Firebase In-App Messaging typically works when app is active
   - Background messages go to system notifications

## 📊 Expected Log Output

When everything works correctly, you should see:

```
MainActivity: Firebase Messaging initialized successfully
FirebaseMessaging: 🚀 Initializing Firebase Messaging...
FirebaseMessaging: ✅ FCM Registration Token: [token]
FirebaseMessaging: ✅ Firebase Messaging initialization completed!
FirebaseMessaging: 🧪 Sending test message...
FirebaseMessaging: 🔔 Creating notification - Title: 🧪 Test Notification
FirebaseMessaging: 📺 Creating notification channel: vtop_notifications
FirebaseMessaging: ✅ Notification channel created successfully
FirebaseMessaging: ✅ Notification displayed with ID: [id]
InAppMessageManager: 💾 Message added: test_[timestamp]
FirebaseMessaging: ✅ Test message sent successfully!
```

## 🚨 Common Issues & Fixes

### Issue: "FirebaseApp is not initialized"
**Fix**: Make sure google-services.json is in app/ folder and plugin is applied

### Issue: No notification sound/vibration
**Fix**: Check notification channel importance (set to HIGH) and device settings

### Issue: Messages not persisting
**Fix**: Check SharedPreferences permissions and InAppMessageManager logs

### Issue: Token not generating
**Fix**: Check internet connection and Firebase project configuration

## 🎯 Next Steps

1. **Install APK** on your phone
2. **Open Android Studio** Logcat
3. **Launch app** and wait 3 seconds
4. **Check notification bar** for test notification
5. **Review logs** for any errors
6. **Try sending message** from Firebase Console

If you see any errors in the logs, share them and I'll help debug further! 🚀

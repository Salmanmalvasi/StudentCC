# Firebase Push Notifications Setup

This guide will help you set up Firebase Cloud Messaging (FCM) to automatically send push notifications to all app users when you push updates to GitHub.

## 🔧 Setup Steps

### 1. Firebase Console Setup

1. **Go to [Firebase Console](https://console.firebase.google.com/)**
2. **Select your project** (`studentcc-16b86`)
3. **Navigate to Cloud Messaging** (Build > Cloud Messaging)
4. **Get your Server Key**:
   - Go to Settings (gear icon) > Project Settings
   - Go to "Cloud Messaging" tab
   - Copy the "Server key" (legacy)

### 2. GitHub Secrets Setup

1. **Go to your GitHub repository**
2. **Navigate to Settings > Secrets and variables > Actions**
3. **Add New Repository Secret**:
   - Name: `FCM_SERVER_KEY`
   - Value: [Your Firebase Server Key from step 1]

### 3. Test the Setup

Once set up, every time you push to the `main` or `master` branch:

1. **GitHub Actions will trigger** (see `.github/workflows/notify-update.yml`)
2. **FCM notification will be sent** to all users subscribed to `update_notifications` topic
3. **Users will receive a notification** saying "StudentCC Update Available! 🚀"

## 📱 How It Works

### App Side
- **FCM Token**: Each app installation gets a unique FCM token
- **Topic Subscription**: All users are automatically subscribed to `update_notifications` topic
- **Notification Handling**: `MyFirebaseMessagingService` handles incoming notifications
- **Auto-Initialization**: Firebase is initialized when the app starts

### GitHub Actions Side
- **Trigger**: Pushes to main/master branch (excluding README and docs changes)
- **Notification**: Sends FCM message to the topic with commit details
- **Content**: Includes commit message, author, and website link

## 🔔 Notification Content

Users will receive notifications with:
- **Title**: "StudentCC Update Available! 🚀"
- **Body**: "New version with latest improvements. Visit our website to download!"
- **Action**: Clicking opens your website (https://salmanmalvasi.github.io/studentcc-landing.html)
- **Details**: Commit message, author, and SHA in the data payload

## 🛠️ Manual Testing

You can manually send a test notification using the script:

```bash
# Make the script executable
chmod +x scripts/send-fcm-notification.js

# Send test notification
FCM_SERVER_KEY="your_server_key" node scripts/send-fcm-notification.js "Test update" "Your Name" "abc123"
```

## 📋 File Structure

```
├── .github/workflows/notify-update.yml     # GitHub Actions workflow
├── scripts/send-fcm-notification.js        # Manual notification script
├── app/src/main/java/.../services/
│   └── MyFirebaseMessagingService.java     # FCM message handler
├── app/src/main/java/.../helpers/
│   └── FirebaseHelper.java                 # Firebase initialization
└── app/src/main/AndroidManifest.xml        # FCM service registration
```

## 🔍 Troubleshooting

### Notifications Not Received
1. **Check FCM Server Key**: Ensure it's correctly set in GitHub secrets
2. **Check Topic Subscription**: Users must have the app installed and opened at least once
3. **Check Device**: Notifications might be disabled in device settings
4. **Check Logs**: Look at GitHub Actions logs for errors

### GitHub Actions Not Triggering
1. **Check branch**: Make sure you're pushing to `main` or `master`
2. **Check file changes**: Workflow ignores changes to `.md`, `.github/`, and `docs/` files
3. **Check workflow file**: Ensure `.github/workflows/notify-update.yml` is in the repository

### Firebase Issues
1. **Check google-services.json**: Ensure it's properly configured
2. **Check Firebase project**: Ensure Cloud Messaging is enabled
3. **Check app**: Ensure Firebase is properly initialized in the app

## 📊 Monitoring

### GitHub Actions
- Go to your repository > Actions tab
- Check the "Notify App Update" workflow runs
- View logs for debugging

### Firebase Console
- Go to Cloud Messaging > Reports
- View delivery statistics
- Check topic subscriptions

## 🔐 Security Notes

- **Never commit FCM Server Key** to the repository
- **Use GitHub Secrets** for sensitive information
- **Regularly rotate** your FCM Server Key if needed
- **Monitor** notification delivery and usage

## 🚀 Next Steps

After setup:
1. **Test** by making a commit to main/master branch
2. **Monitor** notification delivery in Firebase Console
3. **Customize** notification content in the workflow file
4. **Add more triggers** if needed (releases, tags, etc.)

## 📞 Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review GitHub Actions logs
3. Check Firebase Console for errors
4. Test with the manual script first

---

**Happy coding! 🎉**

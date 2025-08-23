const https = require('https');

// Get command line arguments
const args = process.argv.slice(2);
const commitMessage = args[0] || 'New update available';
const commitAuthor = args[1] || 'Developer';
const commitSha = args[2] || 'latest';

// FCM Server Key (should be set as GitHub secret)
const FCM_SERVER_KEY = process.env.FCM_SERVER_KEY;

if (!FCM_SERVER_KEY) {
    console.error('❌ FCM_SERVER_KEY environment variable is required');
    process.exit(1);
}

const message = {
    to: '/topics/update_notifications',
    notification: {
        title: 'StudentCC Update Available! 🚀',
        body: 'New version with latest improvements. Visit our website to download!',
        icon: 'ic_update_available',
        sound: 'default'
    },
    data: {
        type: 'update_available',
        title: 'StudentCC Update Available! 🚀',
        body: `Latest commit: ${commitMessage} by ${commitAuthor}. Visit our website to download the latest version!`,
        url: 'https://salmanmalvasi.github.io/studentcc-landing.html',
        commit_sha: commitSha,
        commit_message: commitMessage,
        author: commitAuthor
    },
    android: {
        priority: 'high',
        notification: {
            channel_id: 'update_notifications',
            priority: 'high',
            default_sound: true,
            default_vibrate_timings: true
        }
    }
};

const postData = JSON.stringify(message);

const options = {
    hostname: 'fcm.googleapis.com',
    port: 443,
    path: '/fcm/send',
    method: 'POST',
    headers: {
        'Authorization': `key=${FCM_SERVER_KEY}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
    }
};

console.log('🔔 Sending FCM notification...');
console.log(`📝 Commit: ${commitMessage}`);
console.log(`👤 Author: ${commitAuthor}`);
console.log(`🔗 SHA: ${commitSha}`);

const req = https.request(options, (res) => {
    let data = '';
    
    res.on('data', (chunk) => {
        data += chunk;
    });
    
    res.on('end', () => {
        if (res.statusCode === 200) {
            console.log('✅ Notification sent successfully!');
            console.log('📱 Response:', data);
        } else {
            console.error('❌ Failed to send notification');
            console.error('Status:', res.statusCode);
            console.error('Response:', data);
            process.exit(1);
        }
    });
});

req.on('error', (error) => {
    console.error('❌ Error sending notification:', error);
    process.exit(1);
});

req.write(postData);
req.end();

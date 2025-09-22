const https = require('https');
const { GoogleAuth } = require('google-auth-library');

// Get command line arguments
const args = process.argv.slice(2);
const commitMessage = args[0] || 'New update available';
const commitAuthor = args[1] || 'Developer';
const commitSha = args[2] || 'latest';

async function sendFCMNotification() {
    try {
        // Get service account from environment variable
        const serviceAccountBase64 = process.env.FIREBASE_SERVICE_ACCOUNT;
        if (!serviceAccountBase64) {
            console.error('❌ FIREBASE_SERVICE_ACCOUNT environment variable is required');
            process.exit(1);
        }

        // Decode base64 service account
        const serviceAccount = JSON.parse(Buffer.from(serviceAccountBase64, 'base64').toString());
        
        // Get access token using Google Auth
        const auth = new GoogleAuth({
            credentials: serviceAccount,
            scopes: ['https://www.googleapis.com/auth/firebase.messaging']
        });

        const accessToken = await auth.getAccessToken();
        
        // Firebase V1 API message format
        const message = {
            message: {
                topic: 'update_notifications',
                notification: {
                    title: 'StudentCC Update Available! 🚀',
                    body: `New features added! ${commitMessage.substring(0, 80)}${commitMessage.length > 80 ? '...' : ''}`
                },
                data: {
                    type: 'update_available',
                    click_action: 'OPEN_WEBSITE',
                    url: 'https://salmanmalvasi.github.io/studentcc-landing.html',
                    commit_sha: commitSha,
                    commit_message: commitMessage,
                    author: commitAuthor,
                    timestamp: new Date().toISOString()
                },
                android: {
                    priority: 'high',
                    notification: {
                        icon: 'ic_update_available',
                        color: '#1976D2',
                        sound: 'default',
                        click_action: 'OPEN_WEBSITE',
                        channel_id: 'update_notifications'
                    }
                }
            }
        };

        const postData = JSON.stringify(message);

        // Firebase V1 API endpoint
        const options = {
            hostname: 'fcm.googleapis.com',
            port: 443,
            path: '/v1/projects/studentcc-16b86/messages:send',
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(postData)
            }
        };

        console.log('🔔 Sending FCM notification...');

        const req = https.request(options, (res) => {
            let data = '';
            
            res.on('data', (chunk) => {
                data += chunk;
            });
            
            res.on('end', () => {
                if (res.statusCode === 200) {
                    console.log('✅ Notification sent successfully!');
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

    } catch (error) {
        console.error('❌ Error in sendFCMNotification:', error);
        process.exit(1);
    }
}

// Run the function
sendFCMNotification();

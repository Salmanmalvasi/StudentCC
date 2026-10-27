#!/bin/bash

# Build script to generate google-services.json from environment variables
# Works with a local .env file or CI-provided environment variables

set -e

echo "🔐 Generating google-services.json from environment variables..."

if [ -f ".env" ]; then
    source .env
fi

required_vars=("FIREBASE_API_KEY" "FIREBASE_PROJECT_ID" "FIREBASE_PROJECT_NUMBER" "FIREBASE_APP_ID" "FIREBASE_STORAGE_BUCKET")

has_missing=false
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        has_missing=true
        break
    fi
done

if [ "$has_missing" = true ]; then
    if [ -s "app/google-services.json" ]; then
        echo "⚠️ Warning: Firebase environment variables not fully set, but app/google-services.json exists. Using existing configuration."
        if [ "$1" == "--build" ]; then
            echo "🚀 Building release APK..."
            ./gradlew assembleRelease
        fi
        exit 0
    else
        echo "⚠️ Warning: Firebase environment variables missing. Generating mock configuration for build verification."
        FIREBASE_PROJECT_NUMBER="000000000000"
        FIREBASE_PROJECT_ID="studentcc-mock"
        FIREBASE_STORAGE_BUCKET="studentcc-mock.appspot.com"
        FIREBASE_APP_ID="1:000000000000:android:0000000000000000"
        FIREBASE_API_KEY="AIzaSyMockKeyForBuildVerification0000"
    fi
fi

echo "📝 Generating app/google-services.json..."

sed -e "s/\${FIREBASE_PROJECT_NUMBER}/$FIREBASE_PROJECT_NUMBER/g" \
    -e "s/\${FIREBASE_PROJECT_ID}/$FIREBASE_PROJECT_ID/g" \
    -e "s/\${FIREBASE_STORAGE_BUCKET}/$FIREBASE_STORAGE_BUCKET/g" \
    -e "s/\${FIREBASE_APP_ID}/$FIREBASE_APP_ID/g" \
    -e "s/\${FIREBASE_API_KEY}/$FIREBASE_API_KEY/g" \
    app/google-services.json.template > app/google-services.json

echo "✅ google-services.json generated successfully!"

if [ "$1" == "--build" ]; then
    echo "🚀 Building release APK..."
    ./gradlew assembleRelease
fi

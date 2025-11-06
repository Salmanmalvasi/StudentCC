#!/bin/bash

# Build script to generate google-services.json from environment variables
# This ensures sensitive data isn't committed to Git

set -e

echo "🔐 Generating google-services.json from environment variables..."

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "❌ Error: .env file not found!"
    echo "Please create .env file with your Firebase configuration."
    echo "Use .env.example as a template."
    exit 1
fi

# Load environment variables from .env file
source .env

# Check if required variables are set
required_vars=("FIREBASE_API_KEY" "FIREBASE_PROJECT_ID" "FIREBASE_PROJECT_NUMBER" "FIREBASE_APP_ID" "FIREBASE_STORAGE_BUCKET")

for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "❌ Error: Required environment variable $var is not set!"
        exit 1
    fi
done

# Generate google-services.json from template
echo "📝 Generating app/google-services.json..."

# Use sed to replace variables in template
sed -e "s/\${FIREBASE_PROJECT_NUMBER}/$FIREBASE_PROJECT_NUMBER/g" \
    -e "s/\${FIREBASE_PROJECT_ID}/$FIREBASE_PROJECT_ID/g" \
    -e "s/\${FIREBASE_STORAGE_BUCKET}/$FIREBASE_STORAGE_BUCKET/g" \
    -e "s/\${FIREBASE_APP_ID}/$FIREBASE_APP_ID/g" \
    -e "s/\${FIREBASE_API_KEY}/$FIREBASE_API_KEY/g" \
    app/google-services.json.template > app/google-services.json

echo "✅ google-services.json generated successfully!"
echo "🏗️  Ready to build your app securely!"

# Optionally run gradle build
if [ "$1" == "--build" ]; then
    echo "🚀 Building release APK..."
    ./gradlew assembleRelease
fi
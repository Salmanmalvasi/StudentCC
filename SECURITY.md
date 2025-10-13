# 🔐 Security Setup Guide

## Overview
This project uses environment variables to securely manage Firebase configuration and API keys. The actual `google-services.json` file is **NOT** committed to Git to prevent API key exposure.

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/Salmanmalvasi/test_student.git
cd test_student
```

### 2. Set Up Environment Variables
```bash
# Copy the example environment file
cp .env.example .env

# Edit .env with your actual Firebase configuration
nano .env  # or use your preferred editor
```

### 3. Generate Configuration Files
```bash
# Generate google-services.json from environment variables
./generate-config.sh

# Or generate and build in one step
./generate-config.sh --build
```

### 4. Build the App
```bash
# Clean build
./gradlew clean

# Build release APK
./gradlew assembleRelease
```

## Environment Variables Required

| Variable | Description | Example |
|----------|-------------|---------|
| `FIREBASE_API_KEY` | Firebase API Key | `AIzaSyB...` |
| `FIREBASE_PROJECT_ID` | Firebase Project ID | `studentcc-16b86` |
| `FIREBASE_PROJECT_NUMBER` | Firebase Project Number | `903476342662` |
| `FIREBASE_APP_ID` | Firebase App ID | `1:903476342662:android:...` |
| `FIREBASE_STORAGE_BUCKET` | Firebase Storage Bucket | `project.firebasestorage.app` |

## Security Features

- ✅ API keys stored in environment variables
- ✅ `.env` file excluded from Git
- ✅ Template-based configuration generation
- ✅ Build script automation
- ✅ No sensitive data in repository

## Getting Your Firebase Configuration

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Go to **Project Settings** → **General**
4. Scroll to **Your apps** section
5. Click **Add app** or select existing Android app
6. Download `google-services.json` 
7. Extract the values and add them to your `.env` file

## Troubleshooting

### Missing .env File
```bash
Error: .env file not found!
```
**Solution:** Copy `.env.example` to `.env` and fill in your values.

### Missing Environment Variables
```bash
Error: Required environment variable FIREBASE_API_KEY is not set!
```
**Solution:** Check that all required variables are set in your `.env` file.

### Build Failures
1. Make sure `google-services.json` exists: `./generate-config.sh`
2. Clean and rebuild: `./gradlew clean assembleRelease`

## For Contributors

When contributing:
1. **NEVER** commit actual API keys or `google-services.json`
2. Only commit the `.template` files
3. Update `.env.example` if adding new environment variables
4. Test the build script before submitting PRs
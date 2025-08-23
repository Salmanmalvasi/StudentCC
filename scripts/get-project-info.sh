#!/bin/bash

# Script to get Google Cloud project information for Firebase setup
# Run this after authenticating with gcloud CLI

echo "🔍 Getting Google Cloud project information..."
echo ""

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo "❌ gcloud CLI is not installed"
    echo "📥 Please install it from: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &> /dev/null; then
    echo "❌ Not authenticated with gcloud"
    echo "🔐 Please run: gcloud auth login"
    exit 1
fi

# Get project information
PROJECT_ID="studentcc-16b86"
echo "📱 Project ID: $PROJECT_ID"

# Get project number
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format="value(projectNumber)" 2>/dev/null)

if [ -z "$PROJECT_NUMBER" ]; then
    echo "❌ Could not get project number for $PROJECT_ID"
    echo "🔍 Make sure you have access to this project"
    exit 1
fi

echo "🔢 Project Number: $PROJECT_NUMBER"
echo ""

# Generate the secrets for GitHub
echo "📋 GitHub Secrets to add:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Secret Name: WIF_PROVIDER"
echo "Secret Value: projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-actions-pool/providers/github-provider"
echo ""
echo "Secret Name: WIF_SERVICE_ACCOUNT" 
echo "Secret Value: github-actions-fcm@$PROJECT_ID.iam.gserviceaccount.com"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Generate the principal for Workload Identity
echo "🔐 Principal for Workload Identity Federation:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Replace YOUR_GITHUB_USERNAME and YOUR_REPO_NAME in this principal:"
echo "principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-actions-pool/attribute.repository/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME"
echo ""
echo "Example for github.com/johndoe/my-app:"
echo "principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-actions-pool/attribute.repository/johndoe/my-app"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

echo ""
echo "✅ Copy these values to complete your Firebase setup!"

#!/usr/bin/env bash
set -e

# Usage: ./copy_and_deploy.sh <version>
if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

VERSION=$1

# Determine repo root (directory containing this script)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source files always pulled from ~/frontend
SOURCE_DIR="$HOME/frontend"

# Define target paths relative to repo root
ANDROID_DIR="$SCRIPT_DIR/front/openboard_korean"
APP_DIR="$ANDROID_DIR/app"
KEYSTORE_DIR="$APP_DIR/keystore"

# Copy configuration and keystore files from ~/frontend
echo "Copying firebase_service_account.json from $SOURCE_DIR to $ANDROID_DIR"
cp "$SOURCE_DIR/firebase_service_account.json" "$ANDROID_DIR/"

echo "Copying google-services.json from $SOURCE_DIR to $APP_DIR"
cp "$SOURCE_DIR/google-services.json" "$APP_DIR/"

echo "Ensuring keystore directory exists: $KEYSTORE_DIR"
mkdir -p "$KEYSTORE_DIR"

echo "Copying release.keystore from $SOURCE_DIR to $KEYSTORE_DIR"
cp "$SOURCE_DIR/release.keystore" "$KEYSTORE_DIR/"

# Change to Android directory
cd "$ANDROID_DIR"

echo "Configuring Bundler and Fastlane..."
bundle config set --local path 'vendor/bundle'
bundle config set --local without 'ios'

bundle install
bundle exec fastlane add_plugin firebase_app_distribution

# Trigger release with version parameter
bundle exec fastlane release \
  groups:"testers" \
  notes:"배포 v$VERSION"

echo "Deployment initiated with version v$VERSION"


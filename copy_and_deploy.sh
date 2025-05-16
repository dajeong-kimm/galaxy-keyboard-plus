#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# Usage: ./copy_and_deploy.sh <version>
if [ $# -ne 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi
VERSION=$1

# Required environment variables
: "${ANDROID_SDK_ROOT:?Environment variable ANDROID_SDK_ROOT must be set}"
: "${KAKAO_NATIVE_APP_KEY:?Environment variable KAKAO_NATIVE_APP_KEY must be set}"
: "${SERVER_BASE_URL:?Environment variable SERVER_BASE_URL must be set}"

# Determine directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$HOME/frontend"
ANDROID_DIR="$SCRIPT_DIR/front/openboard_korean"
APP_DIR="$ANDROID_DIR/app"
KEYSTORE_DIR="$APP_DIR/keystore"

# Verify source directory and required files
if [ ! -d "$SOURCE_DIR" ]; then
  echo "ERROR: SOURCE_DIR $SOURCE_DIR does not exist"
  exit 1
fi
for file in firebase_service_account.json google-services.json release.keystore; do
  if [ ! -f "$SOURCE_DIR/$file" ]; then
    echo "ERROR: $SOURCE_DIR/$file not found"
    exit 1
  fi
done

# Update local.properties with environment variables
echo "Updating local.properties with environment variables"
cat > "$ANDROID_DIR/local.properties" <<EOF
# SDK location
sdk.dir=$ANDROID_SDK_ROOT

# App environment variables
kakao.native.app.key=$KAKAO_NATIVE_APP_KEY
server.base.url=$SERVER_BASE_URL
EOF

# Copy configuration and keystore files
echo "Copying firebase_service_account.json..."
cp "$SOURCE_DIR/firebase_service_account.json" "$ANDROID_DIR/"

echo "Copying google-services.json..."
cp "$SOURCE_DIR/google-services.json" "$APP_DIR/"

echo "Ensuring keystore directory exists..."
mkdir -p "$KEYSTORE_DIR"

echo "Copying release.keystore..."
cp "$SOURCE_DIR/release.keystore" "$KEYSTORE_DIR/"

# Change to Android directory
cd "$ANDROID_DIR" || { echo "ERROR: Could not cd to $ANDROID_DIR"; exit 1; }

echo "Configuring Bundler and Fastlane..."
bundle config set --local path 'vendor/bundle'
bundle config set --local without 'ios'

echo "Installing gems..."
bundle install

echo "Checking for firebase_app_distribution plugin..."
if ! bundle show firebase_app_distribution > /dev/null 2>&1; then
  echo "Adding Fastlane plugin firebase_app_distribution"
  bundle exec fastlane add_plugin firebase_app_distribution
else
  echo "Fastlane plugin firebase_app_distribution already installed"
fi

# Trigger release with version parameter
echo "Triggering Fastlane release with version v$VERSION"
bundle exec fastlane release \
  groups:"testers" \
  notes:"배포 v$VERSION"

echo "Deployment initiated with version v$VERSION"

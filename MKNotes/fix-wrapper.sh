#!/bin/bash
# ============================================================
# fix-wrapper.sh - MKNotes Gradle Wrapper Fix Script
# ============================================================
# This script fixes the corrupted gradle-wrapper.jar that
# was damaged during ZIP import/export.
#
# Usage:
#   cd MKNotes
#   bash fix-wrapper.sh
#
# Requirements:
#   - Gradle 7.5.1+ installed on system, OR
#   - Android Studio installed (it bundles Gradle)
# ============================================================

set -e

echo "=== MKNotes Gradle Wrapper Fix ==="
echo ""

WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="gradle/wrapper/gradle-wrapper.properties"
GRADLE_VERSION="7.5.1"

# Check if wrapper JAR exists and is valid
if [ -f "$WRAPPER_JAR" ]; then
    JAR_SIZE=$(wc -c < "$WRAPPER_JAR")
    echo "Current gradle-wrapper.jar size: $JAR_SIZE bytes"
    
    # Valid JAR should be ~60KB+
    if [ "$JAR_SIZE" -gt 50000 ]; then
        # Check if it's a valid ZIP/JAR (starts with PK)
        MAGIC=$(xxd -l 2 -p "$WRAPPER_JAR" 2>/dev/null || echo "0000")
        if [ "$MAGIC" = "504b" ]; then
            echo "gradle-wrapper.jar appears to be valid. No fix needed."
            echo "Run './gradlew assembleRelease' to build."
            exit 0
        fi
    fi
    
    echo "gradle-wrapper.jar is corrupted (not a valid JAR/ZIP file)."
    echo ""
fi

echo "Attempting to regenerate Gradle wrapper..."
echo ""

# Method 1: Use system Gradle
if command -v gradle &> /dev/null; then
    SYSTEM_GRADLE_VERSION=$(gradle --version 2>/dev/null | grep "^Gradle " | awk '{print $2}')
    echo "Found system Gradle: $SYSTEM_GRADLE_VERSION"
    echo "Generating wrapper for Gradle $GRADLE_VERSION..."
    gradle wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
    echo ""
    echo "SUCCESS: Gradle wrapper generated!"
    echo "Run './gradlew assembleRelease' to build your APK."
    exit 0
fi

# Method 2: Use Android Studio's bundled Gradle
if [ -n "$ANDROID_HOME" ]; then
    STUDIO_GRADLE="$ANDROID_HOME/gradle/gradle-$GRADLE_VERSION/bin/gradle"
    if [ -x "$STUDIO_GRADLE" ]; then
        echo "Found Android SDK Gradle at: $STUDIO_GRADLE"
        "$STUDIO_GRADLE" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
        echo ""
        echo "SUCCESS: Gradle wrapper generated!"
        exit 0
    fi
fi

# Method 3: Download from official source
echo "No local Gradle found. Attempting direct download..."
DOWNLOAD_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"

if command -v curl &> /dev/null; then
    echo "Downloading from: $DOWNLOAD_URL"
    mkdir -p gradle/wrapper
    curl -L -o "$WRAPPER_JAR" "$DOWNLOAD_URL"
    
    JAR_SIZE=$(wc -c < "$WRAPPER_JAR")
    if [ "$JAR_SIZE" -gt 50000 ]; then
        echo "Downloaded: $JAR_SIZE bytes"
        echo ""
        echo "SUCCESS: gradle-wrapper.jar downloaded!"
        echo "Run './gradlew assembleRelease' to build your APK."
        exit 0
    else
        echo "Download failed or file too small ($JAR_SIZE bytes)."
    fi
elif command -v wget &> /dev/null; then
    echo "Downloading from: $DOWNLOAD_URL"
    mkdir -p gradle/wrapper
    wget -O "$WRAPPER_JAR" "$DOWNLOAD_URL"
    
    JAR_SIZE=$(wc -c < "$WRAPPER_JAR")
    if [ "$JAR_SIZE" -gt 50000 ]; then
        echo "Downloaded: $JAR_SIZE bytes"
        echo ""
        echo "SUCCESS: gradle-wrapper.jar downloaded!"
        exit 0
    fi
fi

echo ""
echo "=== MANUAL FIX REQUIRED ==="
echo ""
echo "Could not auto-fix gradle-wrapper.jar. Options:"
echo ""
echo "  1. Open this project in Android Studio"
echo "     -> It will auto-download the wrapper"
echo ""
echo "  2. Install Gradle manually:"
echo "     macOS:  brew install gradle"
echo "     Linux:  sudo apt install gradle"
echo "     Then run: gradle wrapper --gradle-version $GRADLE_VERSION"
echo ""
echo "  3. Copy gradle-wrapper.jar from any working Android project"
echo "     to: gradle/wrapper/gradle-wrapper.jar"
echo ""
exit 1

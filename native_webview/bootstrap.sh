#!/bin/bash
# Run once before first build to get the Gradle wrapper JAR.
set -euo pipefail

JAR="gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
    echo "✓ gradle-wrapper.jar already present"
else
    echo "→ Downloading gradle-wrapper.jar for Gradle 8.9..."
    curl -fsSL -o "$JAR" \
        "https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
    echo "✓ Downloaded"
fi

chmod +x gradlew
echo "✓ gradlew is executable"
echo ""
echo "Build:   ./gradlew assembleDebug"
echo "Install: adb install -r app/build/outputs/apk/debug/app-debug.apk"

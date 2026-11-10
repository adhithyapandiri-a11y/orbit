#!/bin/zsh
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "==============================================="
echo "  🚀 Packaging Orbit as a Native macOS App"
echo "==============================================="

# 1. Build release binary for Apple Silicon
echo "📦 Compiling release binary..."
swift build -c release

RELEASE_BIN="$DIR/.build/release/orbit"
APP_PATH="/Applications/Orbit.app"

# 2. Stop existing daemon if running
echo "🛑 Stopping existing daemon..."
launchctl unload "$HOME/Library/LaunchAgents/com.orbit.daemon.plist" 2>/dev/null || true
pkill -f "Orbit.app/Contents/MacOS/Orbit" 2>/dev/null || true
pkill -f ".build/debug/orbit" 2>/dev/null || true

# 3. Create bundle structure
echo "📂 Creating /Applications/Orbit.app..."
rm -rf "$APP_PATH"
mkdir -p "$APP_PATH/Contents/MacOS"
mkdir -p "$APP_PATH/Contents/Resources"

# 4. Copy binary
cp "$RELEASE_BIN" "$APP_PATH/Contents/MacOS/Orbit"
chmod +x "$APP_PATH/Contents/MacOS/Orbit"

# 5. Create Info.plist with permanent permission entitlements
cat <<EOF > "$APP_PATH/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleIdentifier</key>
    <string>com.adhithya.orbit</string>
    <key>CFBundleName</key>
    <string>Orbit</string>
    <key>CFBundleDisplayName</key>
    <string>Orbit</string>
    <key>CFBundleExecutable</key>
    <string>Orbit</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSMicrophoneUsageDescription</key>
    <string>Orbit requires microphone access to listen for 'Hey Orbit' voice commands.</string>
    <key>NSSpeechRecognitionUsageDescription</key>
    <string>Orbit uses on-device speech recognition to process your voice commands.</string>
    <key>NSAppleEventsUsageDescription</key>
    <string>Orbit controls apps like WhatsApp and Chrome on your behalf.</string>
</dict>
</plist>
EOF

# 6. Sign the app with stable ad-hoc signature so macOS remembers permissions permanently
echo "✍️ Signing Orbit.app with stable identifier..."
codesign --force --deep --sign - "$APP_PATH"

# 7. Update LaunchAgent to point to /Applications/Orbit.app
PLIST_PATH="$HOME/Library/LaunchAgents/com.orbit.daemon.plist"
LOG_DIR="$HOME/.orbit"
mkdir -p "$LOG_DIR"

cat <<EOF > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.orbit.daemon</string>
    <key>ProgramArguments</key>
    <array>
        <string>$APP_PATH/Contents/MacOS/Orbit</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>LimitLoadToSessionType</key>
    <string>Aqua</string>
    <key>StandardOutPath</key>
    <string>$LOG_DIR/orbit.log</string>
    <key>StandardErrorPath</key>
    <string>$LOG_DIR/orbit.error.log</string>
    <key>ProcessType</key>
    <string>Interactive</string>
</dict>
</plist>
EOF

# 8. Start the daemon
echo "🚀 Loading Orbit background service..."
launchctl load "$PLIST_PATH"

echo "==============================================="
echo "✅ /Applications/Orbit.app is installed and active!"
echo "✨ Permanent bundle ID: com.adhithya.orbit"
echo "🟢 Running in background. Zero terminal windows."
echo "==============================================="

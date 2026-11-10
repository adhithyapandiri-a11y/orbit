#!/bin/zsh

DIR="$(cd "$(dirname "$0")" && pwd)"
APP_PATH="/Applications/Orbit.app"
BINARY="$APP_PATH/Contents/MacOS/Orbit"
PLIST_PATH="$HOME/Library/LaunchAgents/com.orbit.daemon.plist"
LOG_DIR="$HOME/.orbit"

mkdir -p "$LOG_DIR"
mkdir -p "$HOME/Library/LaunchAgents"

echo "==============================================="
echo "  🪐 Installing Orbit as a macOS Background Daemon"
echo "==============================================="

# Unload existing if running
launchctl unload "$PLIST_PATH" 2>/dev/null || true

cat <<EOF > "$PLIST_PATH"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.orbit.daemon</string>
    <key>ProgramArguments</key>
    <array>
        <string>$BINARY</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>$LOG_DIR/orbit.log</string>
    <key>StandardErrorPath</key>
    <string>$LOG_DIR/orbit.error.log</string>
    <key>ProcessType</key>
    <string>Interactive</string>
</dict>
</plist>
EOF

launchctl load "$PLIST_PATH"

echo "✅ Orbit is now running silently in the background!"
echo "✨ Zero terminal windows needed."
echo "🟢 Always listening for 'Hey Orbit' like Siri."
echo "📝 Logs available at: $LOG_DIR/orbit.log"
echo "🛑 To stop Orbit anytime, run: ./stop-daemon.sh"

#!/bin/zsh

PLIST_PATH="$HOME/Library/LaunchAgents/com.orbit.daemon.plist"

echo "🛑 Stopping Orbit Background Daemon..."
launchctl unload "$PLIST_PATH" 2>/dev/null || true
pkill -f "Orbit.app/Contents/MacOS/Orbit" 2>/dev/null || true
pkill -f ".build/debug/orbit" 2>/dev/null || true
pkill -f ".build/release/orbit" 2>/dev/null || true
echo "✅ Orbit stopped."

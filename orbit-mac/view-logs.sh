#!/bin/zsh

LOG_FILE="$HOME/.orbit/orbit.log"
touch "$LOG_FILE"
echo "🪐 Live Orbit Logs (Press Ctrl+C to exit):"
tail -f "$LOG_FILE"

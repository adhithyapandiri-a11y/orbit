#!/bin/zsh

# Change to the directory of this script
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "==============================================="
echo "  🪐 Launching Orbit (Jarvis Edition)..."
echo "==============================================="

# Build release if not present or run existing build
if [ ! -f ".build/debug/orbit" ]; then
    echo "First time build, compiling Swift engine..."
    swift build
fi

# Run Orbit in full hands-free voice mode
./.build/debug/orbit

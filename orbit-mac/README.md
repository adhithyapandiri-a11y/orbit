# 🪐 Orbit (macOS Jarvis Edition)

A high-performance, native macOS autonomous voice assistant inspired by Jarvis. Orbit runs continuously in the background, listening for **"Hey Orbit"**, acknowledging you instantly with an acoustic chime (<10ms), executing your commands headlessly, and reporting feedback out loud.

---

## ⚡ Key Capabilities

1. **Instant Acoustic Chime (<10ms):** Plays a crisp system audio cue (`Tink`) the exact millisecond you say "Hey Orbit" so you get immediate feedback.
2. **Menu Bar Status Item (`🪐`):** Visual status indicator in your top menu bar with fallback actions (Pause/Resume, Wake Now, Toggle Music, Live Logs, Settings, Quit).
3. **Dynamic Spotify Music Playback:** Instantly searches, selects, and autoplays requested tracks on Spotify without resuming old songs.
4. **Instant YouTube Autoplay & Window Management:** Directly launches the top YouTube video with autoplay in Chrome, and closes tabs/windows on command.
5. **Strict WhatsApp Messaging & Voice Notes:** Opens contacts directly, types messages, and sends immediately—or records voice notes with automatic 2.5s silence detection.
6. **Display Window Switching:** Moves the active window between your Mac display and external monitors seamlessly.
7. **Offline Heuristics & Fast Brain:** Executes frequent tasks instantly offline with zero latency and ₹0 / $0 API cost, falling back to Groq Cloud LPU for complex queries.
8. **No Terminal Windows (Background Daemon):** Installs as a native macOS `LaunchAgent`. Starts on login and runs 24/7 in the background with zero terminal clutter.
9. **Permanent Config File:** Stored at `~/.orbit/config.json` with secure permissions.

---

## 🚀 Setting Up Your Groq API Key

To let Orbit understand **any command** with natural variations, configure your Groq API key in `~/.orbit/config.json`:

```json
{
  "groqApiKey": "YOUR_GROQ_API_KEY_HERE",
  "model": "openai/gpt-oss-120b",
  "provider": "groq",
  "wakeWord": "orbit",
  "voiceFeedback": true
}
```

---

## ⚙️ Running Orbit Without Terminal (LaunchAgent Daemon)

To package and start Orbit in the background:
```bash
./orbit-mac/package-app.sh
```

- **View Live Activity:** `./orbit-mac/view-logs.sh`
- **Stop Orbit Anytime:** `./orbit-mac/stop-daemon.sh`

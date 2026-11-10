import Foundation
import CoreGraphics

/// OrbitEngine coordinates the full Orbit voice loop:
/// Wake Word ("Hey Orbit") -> Spoken Greeting -> Ongoing Active Conversation -> OS Automation -> Spoken Feedback -> Continues listening until dismissed or silence.
@MainActor
public final class OrbitEngine {
    public let synthesizer: VoiceSynthesizer
    public let transcriber: SpeechTranscriber
    public let groq: GroqClient
    public let automation: AutomationBridge
    public let ghost: GhostUserEngine
    public var onListeningModeChanged: (@MainActor @Sendable (ListeningMode) -> Void)?

    private var isCancelled = false

    public init() {
        self.synthesizer = VoiceSynthesizer()
        self.transcriber = SpeechTranscriber()
        self.groq = GroqClient()
        self.automation = AutomationBridge.shared
        self.ghost = GhostUserEngine.shared

        setupVoiceHandlers()
    }

    /// Sets up the multi-turn conversational loop
    private func setupVoiceHandlers() {
        transcriber.onModeChanged = { [weak self] mode in
            self?.onListeningModeChanged?(mode)
        }

        // Step 1: Wake word detected -> Reset context -> Speak greeting -> Enter active conversation
        transcriber.onWakeWordDetected = { [weak self] in
            guard let self = self else { return }
            Task { @MainActor in
                await self.groq.resetConversation()
                let greetings = ["Hello!", "I'm listening.", "How can I help?"]
                let greeting = greetings.randomElement() ?? "Hello!"
                print("\n[Orbit] ✨ Woke up! Greeting: \"\(greeting)\"")
                await self.synthesizer.speak(greeting)
                self.transcriber.startActiveConversation(timeoutSeconds: 12.0)
            }
        }

        // Step 2: Command/question captured in conversation -> Execute -> Decide whether to stay conversing or sleep
        transcriber.onCommandTranscribed = { [weak self] command in
            guard let self = self else { return }
            Task { @MainActor in
                let shouldStayConversing = await self.execute(command: command)
                if shouldStayConversing {
                    print("[Orbit] 🔄 Staying awake for your follow-up...")
                    self.transcriber.startActiveConversation(timeoutSeconds: 8.0)
                } else {
                    print("[Orbit] 😴 Action completed. Sleeping. Say \"Hey Orbit\" to wake.")
                    self.transcriber.startListeningForWakeWord()
                }
            }
        }

        // Step 3: User explicitly dismissed the conversation ("thank you", "that's all", "bye", "go to sleep")
        transcriber.onSessionDismissed = { [weak self] in
            guard let self = self else { return }
            Task { @MainActor in
                print("[Orbit] 🛌 Conversation ended by user request.")
                await self.groq.resetConversation()
                let goodbyes = ["Standing by.", "All set.", "Glad I could help.", "Have a great day."]
                let reply = goodbyes.randomElement() ?? "Standing by."
                await self.synthesizer.speak(reply)
                self.transcriber.startListeningForWakeWord()
            }
        }

        // Step 4: Silence timeout after 12s -> Quietly go back to sleep
        transcriber.onSessionTimeout = { [weak self] in
            guard let self = self else { return }
            Task { @MainActor in
                print("[Orbit] ⏳ Conversation ended due to 12s of silence.")
                await self.groq.resetConversation()
                self.transcriber.startListeningForWakeWord()
            }
        }
    }

    /// Starts the ambient hands-free voice daemon
    @MainActor
    public func startVoiceDaemon() async -> Bool {
        print("[Orbit] 🎙️ Requesting microphone and speech permissions...")
        _ = await transcriber.requestPermissions()

        print("[Orbit] 🟢 Ambient Voice Mode active!")
        print("[Orbit] 🗣️ Say \"Hey Orbit\" to wake. The conversation will stay alive until you're done.\n")
        transcriber.startListeningForWakeWord()
        return true
    }

    /// Handles a command (from voice or typed input)
    /// Returns true if Orbit should remain in conversation mode (questions/chat), or false if action completed (should sleep)
    @discardableResult
    public func execute(command: String) async -> Bool {
        isCancelled = false
        print("\n[Orbit] 🎙️ Command: \"\(command)\"")

        do {
            var plan: PlannedTask? = nil

            // Tier 1: Local Offline Rule Engine (Instant 0-latency execution for core macOS actions)
            if let localPlan = LocalRuleEngine.tryLocalPlan(command: command) {
                print("[Orbit] ⚡ Local Engine: Instant match.")
                plan = localPlan
            }

            // Tier 2: Groq Cloud (Ultra-fast LPU, 120B model for conversational reasoning)
            if plan == nil && groq.hasApiKey {
                let modelName = groq.model
                print("[Orbit] 🧠 Thinking with Groq (\(modelName))...")
                do {
                    plan = try await groq.plan(userVoiceCommand: command)
                } catch {
                    print("[Orbit] ⚠️ Groq issue (\(error.localizedDescription)). Falling back to offline engine...")
                }
            }

            guard let finalPlan = plan else {
                throw NSError(
                    domain: "OrbitBrain",
                    code: 401,
                    userInfo: [NSLocalizedDescriptionKey: "I couldn't plan that action. Check your API key in ~/.orbit/config.json."]
                )
            }

            // Speak initial plan acknowledgment if available
            var hasSpoken = false
            if let speech = finalPlan.speechResponse, !speech.isEmpty {
                print("[Orbit] 🗣️ Orbit: \"\(speech)\"")
                await synthesizer.speak(speech)
                hasSpoken = true
            }

            // Execute planned actions
            var feedbackList: [String] = []
            for action in finalPlan.actions {
                if isCancelled {
                    print("[Orbit] 🛑 Execution cancelled.")
                    await synthesizer.speak("Action cancelled.")
                    return false
                }
                let feedback = try await executeAction(action)
                if !feedback.isEmpty {
                    feedbackList.append(feedback)
                }
            }

            // Only speak feedback if we didn't already speak an initial response
            if !hasSpoken, let lastFeedback = feedbackList.last, !lastFeedback.isEmpty {
                print("[Orbit] 🗣️ Feedback: \"\(lastFeedback)\"")
                await synthesizer.speak(lastFeedback)
            }

            print("[Orbit] ✅ Finished command.\n")

            // If an action was performed (playing media, WhatsApp, volume, apps), go to sleep!
            // Only stay awake for conversational questions/dialogue where actions is empty.
            let performedAction = !finalPlan.actions.isEmpty
            return !performedAction
        } catch {
            let errorMsg = error.localizedDescription
            print("[Orbit] ⚠️ Issue: \(errorMsg)")
            let spokenError = humanizeError(error)
            await synthesizer.speak(spokenError)
            return false
        }
    }

    private func humanizeError(_ error: Error) -> String {
        let msg = error.localizedDescription
        if msg.contains("execution error") || msg.contains("osascript") || msg.contains("1002") || msg.contains("System Events") || msg.contains("232") || msg.contains("225") {
            return "I couldn't control the application directly. Please make sure it is open and active."
        }
        if msg.contains("timed out") || msg.contains("timeout") {
            return "The application took too long to respond."
        }
        if msg.contains("401") || msg.contains("API key") {
            return "I had trouble verifying my AI service key."
        }
        return "I ran into a minor issue executing that."
    }

    @discardableResult
    private func executeAction(_ action: OrbitAction) async throws -> String {
        switch action {
        case .speak(let text):
            await synthesizer.speak(text)
            return ""

        case .playHotstar(let title):
            print("[Orbit] 🎬 Launching Hotstar in Chrome for \"\(title)\"...")
            return try await automation.playHotstar(query: title)

        case .playSpotify(let query):
            print("[Orbit] 🎵 Launching Spotify for \"\(query)\"...")
            return try await automation.playSpotify(query: query)

        case .playYouTube(let query):
            print("[Orbit] 📺 Launching YouTube for \"\(query)\"...")
            return try await automation.playYouTube(query: query)

        case .openChrome(let url):
            print("[Orbit] 🌐 Opening Chrome to: \(url)")
            return try await automation.openInChrome(url: url)

        case .openWhatsApp(let recipient):
            print("[Orbit] 💬 Opening WhatsApp contact: \(recipient)")
            return try await automation.openWhatsApp(recipient: recipient)

        case .sendWhatsApp(let recipient, let message, _):
            print("[Orbit] 💬 Sending WhatsApp message to \(recipient): \"\(message)\"")
            return try await automation.stageWhatsAppMessage(recipient: recipient, message: message)

        case .sendWhatsAppVoiceNote(let recipient):
            print("[Orbit] 🎙️ Recording WhatsApp voice note for \(recipient)...")
            return try await automation.recordWhatsAppVoiceNote(recipient: recipient)

        case .readRecentEmails(let count):
            print("[Orbit] 📬 Checking recent \(count) emails from Apple Mail...")
            let emailText = try await automation.getRecentEmails(count: count)
            if emailText.contains("empty") {
                return "Your inbox is empty."
            }
            let lines = emailText.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
            return "Here are your latest \(lines.count) emails: " + lines.joined(separator: ". ")

        case .setVolume(let percent):
            print("[Orbit] 🔊 Setting volume to \(percent)%")
            return try await automation.setVolume(percent: percent)

        case .togglePlayPause:
            print("[Orbit] ⏯️ Toggling playback")
            return try await automation.togglePlayPause()

        case .switchWindow:
            print("[Orbit] 🪟 Moving active window between displays...")
            return try await automation.switchActiveWindowDisplay()

        case .closeYouTube:
            print("[Orbit] 🛑 Closing YouTube window...")
            return try await automation.closeYouTube()

        case .closeApp(let name):
            print("[Orbit] 🛑 Closing \(name)...")
            return try await automation.closeApp(name: name)

        case .closeActiveWindow:
            print("[Orbit] 🛑 Closing active window...")
            return try await automation.closeActiveWindow()

        case .getBatteryStatus:
            print("[Orbit] 🔋 Checking battery status...")
            return await automation.getBatteryStatus()

        case .lockScreen:
            print("[Orbit] 🔒 Locking display...")
            return await automation.lockScreen()

        case .launchApp(let name):
            print("[Orbit] 🚀 Launching \(name)")
            return try await automation.launchApp(name: name)

        case .clickScreen(let x, let y, let label):
            print("[Orbit] 👻 Ghost User: Clicking at (\(x), \(y)) - \(label)")
            await ghost.click(at: CGPoint(x: x, y: y))
            return "Clicked \(label)."

        case .typeText(let text):
            print("[Orbit] 👻 Ghost User: Typing \"\(text)\"")
            await ghost.typeText(text)
            return "Typed text."

        case .pressKey(let key):
            print("[Orbit] 👻 Ghost User: Pressing key \(key)")
            let code: CGKeyCode = (key.lowercased() == "return") ? 36 : (key.lowercased() == "space") ? 49 : 53
            await ghost.pressKey(keyCode: code)
            return "Pressed \(key)."

        case .runAppleScript(let script):
            print("[Orbit] 📜 Executing AppleScript")
            _ = try await automation.runAppleScript(script)
            return "Executed script."

        case .fallbackToGhostUser(let reason):
            print("[Orbit] 📸 Fallback to Ghost User: \(reason)")
            _ = try await ghost.captureScreen()
            return "Captured screen for visual navigation."
        }
    }

    public func cancel() {
        isCancelled = true
        synthesizer.stop()
    }
}

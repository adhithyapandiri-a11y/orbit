import Foundation
@preconcurrency import Speech
@preconcurrency import AVFoundation
import AppKit

/// Operating modes for the Orbit conversational pipeline
public enum ListeningMode: Sendable {
    case wakeWord
    case activeConversation
    case paused
}

/// Thread-safe audio buffer relay for CoreAudio tap
final class AudioBufferHolder: @unchecked Sendable {
    private let lock = NSLock()
    private var _request: SFSpeechAudioBufferRecognitionRequest?
    private var _isPaused: Bool = false

    var request: SFSpeechAudioBufferRecognitionRequest? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _request
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            _request = newValue
        }
    }

    var isPaused: Bool {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _isPaused
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            _isPaused = newValue
        }
    }
}

/// SpeechTranscriber provides on-device, zero-latency speech recognition
/// with support for continuous multi-turn conversations and robust wake-word detection.
@MainActor
public final class SpeechTranscriber: ObservableObject {
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var audioEngine: AVAudioEngine?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let bufferHolder = AudioBufferHolder()
    
    public var onWakeWordDetected: (@MainActor @Sendable () -> Void)?
    public var onCommandTranscribed: (@MainActor @Sendable (String) -> Void)?
    public var onSessionDismissed: (@MainActor @Sendable () -> Void)?
    public var onSessionTimeout: (@MainActor @Sendable () -> Void)?
    public var onModeChanged: (@MainActor @Sendable (ListeningMode) -> Void)?
    
    public private(set) var mode: ListeningMode = .wakeWord {
        didSet {
            onModeChanged?(mode)
        }
    }
    private var silenceTask: Task<Void, Never>?
    private var timeoutTask: Task<Void, Never>?
    private var currentTranscript = ""
    private var isTapInstalled = false
    private var isListeningPaused = false

    // System sound cues
    private let wakeChime = NSSound(named: "Tink")
    private let listenChime = NSSound(named: "Pop")

    public init() {}

    /// Requests macOS speech and microphone permissions
    @MainActor
    public func requestPermissions() async -> Bool {
        let currentStatus = SFSpeechRecognizer.authorizationStatus()
        if currentStatus == .authorized {
            return true
        }
        return await withCheckedContinuation { continuation in
            nonisolated(unsafe) var resumed = false
            SFSpeechRecognizer.requestAuthorization { authStatus in
                DispatchQueue.main.async {
                    if !resumed {
                        resumed = true
                        continuation.resume(returning: authStatus == .authorized)
                    }
                }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                if !resumed {
                    resumed = true
                    continuation.resume(returning: true)
                }
            }
        }
    }

    /// Plays instant audio chime indicating Orbit woke up
    public func playWakeChime() {
        wakeChime?.play()
    }

    /// Plays subtle prompt chime indicating Orbit is waiting for your next turn
    public func playListenPrompt() {
        listenChime?.play()
    }

    /// Starts listening for the wake word ("Hey Orbit" or "Orbit")
    public func startListeningForWakeWord() {
        mode = .wakeWord
        currentTranscript = ""
        isListeningPaused = false
        bufferHolder.isPaused = false
        silenceTask?.cancel()
        timeoutTask?.cancel()
        startRecognitionTask()
        print("[Orbit Voice] 🟢 Sleeping. Say \"Hey Orbit\" to wake.")
    }

    /// Keeps Orbit in active conversational mode for multi-turn dialogue
    public func startActiveConversation(timeoutSeconds: Double = 12.0) {
        mode = .activeConversation
        currentTranscript = ""
        isListeningPaused = false
        bufferHolder.isPaused = false
        silenceTask?.cancel()
        timeoutTask?.cancel()
        startRecognitionTask()
        playListenPrompt()
        print("[Orbit Voice] 🎙️ Active Conversation: Listening (timeout in \(Int(timeoutSeconds))s)...")

        // Timeout task if user remains silent during conversation
        scheduleSessionTimeout(seconds: timeoutSeconds)
    }

    private func scheduleSessionTimeout(seconds: Double) {
        timeoutTask?.cancel()
        timeoutTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            guard !Task.isCancelled, let self = self, self.mode == .activeConversation else { return }
            print("[Orbit Voice] ⏳ Conversation timed out due to silence.")
            self.pauseListening()
            self.onSessionTimeout?()
        }
    }

    /// Temporarily pauses audio processing (e.g. while Orbit is speaking)
    public func pauseListening() {
        mode = .paused
        isListeningPaused = true
        bufferHolder.isPaused = true
        silenceTask?.cancel()
        timeoutTask?.cancel()
        stopRecognitionTask()
    }

    // MARK: - Persistent Audio Engine & Recognition Task
    private func ensureAudioEngineRunning() {
        if audioEngine == nil {
            audioEngine = AVAudioEngine()
        }
        guard let engine = audioEngine else { return }

        if !isTapInstalled {
            let inputNode = engine.inputNode
            let recordingFormat = inputNode.outputFormat(forBus: 0)
            let holder = self.bufferHolder

            inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
                guard !holder.isPaused else { return }
                holder.request?.append(buffer)
            }
            isTapInstalled = true
        }

        if !engine.isRunning {
            do {
                engine.prepare()
                try engine.start()
            } catch {
                print("[Orbit Voice] ❌ Audio engine start error: \(error)")
            }
        }
    }

    private func startRecognitionTask() {
        stopRecognitionTask()
        ensureAudioEngineRunning()

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        // Do not force requiresOnDeviceRecognition if assets aren't local; allows automatic fallback
        if speechRecognizer?.supportsOnDeviceRecognition == true {
            request.requiresOnDeviceRecognition = false
        }
        self.recognitionRequest = request
        self.bufferHolder.request = request

        recognitionTask = speechRecognizer?.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self = self, !self.isListeningPaused, self.mode != .paused else { return }

                if let result = result {
                    let text = result.bestTranscription.formattedString
                    self.processTranscript(text)
                }

                if error != nil || result?.isFinal == true {
                    if !self.isListeningPaused && self.mode != .paused {
                        try? await Task.sleep(nanoseconds: 150_000_000)
                        if !self.isListeningPaused && self.mode != .paused {
                            self.startRecognitionTask()
                        }
                    }
                }
            }
        }
    }

    private func stopRecognitionTask() {
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        bufferHolder.request = nil
    }

    // MARK: - Transcript Processor
    private func processTranscript(_ text: String) {
        let cleanText = text.replacingOccurrences(of: ",", with: "")
                            .replacingOccurrences(of: ".", with: "")
                            .replacingOccurrences(of: "!", with: "")
                            .replacingOccurrences(of: "?", with: "")
        let lower = cleanText.lowercased()

        // Normalize phonetics: catch every accent and transcription artifact for "Orbit"
        var normalized = lower
        let phoneticOrbit = [
            "or bit", "or-bit", "our bit", "ourbit", "aarbit", "arbit", "ar bit",
            "orbet", "orbeet", "urbit", "ur bit", "halbert", "norbit", "corbit",
            "allbit", "all bit", "albit", "al bit", "ar pit", "arpit"
        ]
        for p in phoneticOrbit {
            normalized = normalized.replacingOccurrences(of: p, with: "orbit")
        }

        switch mode {
        case .wakeWord:
            let triggers = [
                "hey orbit", "orbit", "hi orbit", "hello orbit",
                "okay orbit", "ok orbit", "yo orbit", "hey", "a orbit", "listen orbit"
            ]
            var foundTrigger: String?
            for trigger in triggers {
                if normalized.contains(trigger) {
                    foundTrigger = trigger
                    break
                }
            }

            if let trigger = foundTrigger, let range = normalized.range(of: trigger) {
                playWakeChime()

                let remainder = String(cleanText[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
                let wordCount = remainder.split(separator: " ").count

                // If user gave full command in one breath (e.g. "Hey Orbit play Starboy on Spotify")
                if wordCount >= 2 {
                    print("[Orbit Voice] ⚡ One-shot command: \"\(remainder)\"")
                    pauseListening()
                    onCommandTranscribed?(remainder)
                } else {
                    // Two-turn handshake: Wake up, greet user, then listen for command
                    print("[Orbit Voice] ✨ Wake word detected! Greeting...")
                    pauseListening()
                    onWakeWordDetected?()
                }
            }

        case .activeConversation:
            // Check for explicit session dismissal phrases
            let dismissPhrases = [
                "thank you", "thanks orbit", "that's all", "that is all",
                "go to sleep", "sleep", "bye", "goodbye", "dismiss",
                "never mind", "stop listening", "stop"
            ]
            for phrase in dismissPhrases {
                if lower == phrase || lower.hasSuffix(phrase) {
                    print("[Orbit Voice] 🛑 User dismissed conversation: \"\(phrase)\"")
                    pauseListening()
                    onSessionDismissed?()
                    return
                }
            }

            // User is actively speaking: cancel session timeout so active speech isn't interrupted
            timeoutTask?.cancel()
            currentTranscript = text
            resetSilenceTimer()

        case .paused:
            break
        }
    }

    private func resetSilenceTimer() {
        silenceTask?.cancel()

        // Dynamic pause detection: if the user's sentence ends in an incomplete connector, wait 2.2s.
        // Otherwise wait 1.4s of quiet before executing.
        let trimmed = currentTranscript.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let incompleteWords = ["and", "or", "to", "for", "the", "a", "an", "of", "in", "on", "with", "like", "can you", "could you", "please", "play", "play the", "open", "text", "send"]
        let isIncomplete = incompleteWords.contains(where: { trimmed.hasSuffix(" " + $0) || trimmed == $0 })
        let delayNanoseconds: UInt64 = isIncomplete ? 2_200_000_000 : 1_400_000_000

        silenceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: delayNanoseconds)
            guard !Task.isCancelled, let self = self, self.mode == .activeConversation else { return }
            let finalCommand = self.currentTranscript.trimmingCharacters(in: .whitespacesAndNewlines)
            if !finalCommand.isEmpty {
                print("[Orbit Voice] 🎯 Captured in conversation: \"\(finalCommand)\"")
                self.pauseListening()
                self.onCommandTranscribed?(finalCommand)
            }
        }
    }
}

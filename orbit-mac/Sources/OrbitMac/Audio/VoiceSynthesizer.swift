import Foundation
import AVFoundation

/// VoiceSynthesizer delivers the spoken Orbit voice using Apple's British Daniel voice.
/// Runs completely offline with 0 latency, 0 API cost, and in-process audio delivery.
@MainActor
public final class VoiceSynthesizer: NSObject, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var continuation: CheckedContinuation<Void, Never>?
    private let preferredVoice: AVSpeechSynthesisVoice?

    public override init() {
        let voices = AVSpeechSynthesisVoice.speechVoices()
        if let daniel = voices.first(where: { $0.identifier.contains("en-GB.Daniel") && !$0.identifier.contains("super-compact") }) {
            self.preferredVoice = daniel
            print("[Orbit Voice] 🎙️ Speech voice locked: Daniel (\(daniel.identifier))")
        } else if let fallbackDaniel = voices.first(where: { $0.name == "Daniel" }) {
            self.preferredVoice = fallbackDaniel
            print("[Orbit Voice] 🎙️ Speech voice locked: Daniel")
        } else {
            self.preferredVoice = AVSpeechSynthesisVoice(language: "en-GB")
            print("[Orbit Voice] 🎙️ Speech voice fallback: en-GB")
        }

        super.init()
        synthesizer.delegate = self
    }

    /// Speaks the provided text and suspends until speech playback finishes.
    /// Includes a safety watchdog timer so speech playback can never hang or deadlock the main loop.
    public func speak(_ text: String, rate: Float = 0.50, pitch: Float = 1.0) async {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        // Cleanly stop any existing speech and safely resume any pending continuation
        stop()

        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = preferredVoice
        utterance.rate = rate
        utterance.pitchMultiplier = pitch
        utterance.volume = 1.0
        utterance.preUtteranceDelay = 0.05
        utterance.postUtteranceDelay = 0.05

        // Safety watchdog: estimate speech duration + 2.0s buffer to prevent deadlocks
        let wordCount = Double(trimmed.split(separator: " ").count)
        let watchdogTimeout = max(2.5, (wordCount * 0.55) + 2.0)

        let watchdogTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(watchdogTimeout * 1_000_000_000))
            guard !Task.isCancelled, let self = self, self.continuation != nil else { return }
            print("[Orbit Voice] ⚠️ Speech watchdog triggered after \(String(format: "%.1f", watchdogTimeout))s.")
            self.stop()
        }

        if let existing = self.continuation {
            existing.resume()
            self.continuation = nil
        }

        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            self.continuation = continuation
            synthesizer.speak(utterance)
        }

        watchdogTask.cancel()
    }

    /// Stops speaking immediately and safely resolves continuation.
    public func stop() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        continuation?.resume()
        continuation = nil
    }

    // MARK: - AVSpeechSynthesizerDelegate
    public nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.continuation?.resume()
            self.continuation = nil
        }
    }

    public nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in
            self.continuation?.resume()
            self.continuation = nil
        }
    }
}

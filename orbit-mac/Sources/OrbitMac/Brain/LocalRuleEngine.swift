import Foundation

/// Fast offline rule-based heuristic parser.
/// Allows Orbit to execute core commands with 0 latency, 0 cloud calls, and 0 setup.
@MainActor
public struct LocalRuleEngine {
    public static nonisolated(unsafe) var lastWhatsAppRecipient: String = "Mom"

    public static func tryLocalPlan(command: String) -> PlannedTask? {
        let trimmed = command.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = trimmed.lowercased()

        // 0. Date & Time Queries (Instant Offline Answer with Real Local Time)
        if lower.contains("what is the time") || lower.contains("what's the time") || lower.contains("what time") || lower == "the time" || lower.contains("tell me the time") || lower.contains("current time") || lower.contains("time now") {
            let formatter = DateFormatter()
            formatter.timeStyle = .short
            let timeStr = formatter.string(from: Date())
            return PlannedTask(
                speechResponse: "It is currently \(timeStr).",
                actions: []
            )
        }

        // 1. Hotstar / Streaming
        if lower.contains("hotstar") {
            var title = "Vision Quest"
            if let playRange = lower.range(of: "play ") {
                let afterPlay = String(trimmed[playRange.upperBound...])
                title = afterPlay.replacingOccurrences(of: "on hotstar", with: "", options: .caseInsensitive)
                                 .replacingOccurrences(of: "in hotstar", with: "", options: .caseInsensitive)
                                 .trimmingCharacters(in: .whitespacesAndNewlines)
            }
            return PlannedTask(
                speechResponse: "Playing \(title) on Hotstar.",
                actions: [.playHotstar(title: title)]
            )
        }

        // 1.5 Spotify Music
        if lower.contains("spotify") || (lower.contains("play ") && !lower.contains("youtube") && !lower.contains("hotstar")) {
            var query = "Top hits"
            if let playRange = lower.range(of: "play ") {
                let afterPlay = String(trimmed[playRange.upperBound...])
                var clean = afterPlay
                for phrase in ["on spotify", "in spotify", "from spotify", "for me", "can you", "please", "the song", "song called", "a song called", "song"] {
                    clean = clean.replacingOccurrences(of: phrase, with: "", options: .caseInsensitive)
                }
                clean = clean.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty {
                    query = clean
                }
            } else if lower.contains("spotify") {
                var clean = trimmed
                for phrase in ["open spotify", "spotify", "and play", "play", "for me", "can you", "please", "the song", "song called", "a song called", "song"] {
                    clean = clean.replacingOccurrences(of: phrase, with: "", options: .caseInsensitive)
                }
                clean = clean.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty {
                    query = clean
                }
            }
            return PlannedTask(
                speechResponse: "Playing \(query) on Spotify.",
                actions: [.playSpotify(query: query)]
            )
        }

        // 1.6 YouTube Music & Videos
        if lower.contains("youtube") {
            var query = "full length movie"
            if let playRange = lower.range(of: "play ") {
                let afterPlay = String(trimmed[playRange.upperBound...])
                let cleaned = afterPlay.replacingOccurrences(of: "on youtube", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "in youtube", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "song", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "any video on youtube regarding", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "video on youtube regarding", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "video regarding", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "video about", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "a video of", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "a video on", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "any video on", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "any video", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "video", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "or something else", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "just for testing", with: "", options: .caseInsensitive)
                                       .replacingOccurrences(of: "i mean", with: "", options: .caseInsensitive)
                                       .trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: ".,?!")))
                if !cleaned.isEmpty {
                    query = cleaned
                }
            }
            return PlannedTask(
                speechResponse: "Playing \(query) on YouTube.",
                actions: [.playYouTube(query: query)]
            )
        }

        // 2. WhatsApp Voice Notes
        if lower.contains("voice note") || lower.contains("voice message") || lower.contains("audio message") || lower.contains("audio note") {
            var recipient = lastWhatsAppRecipient
            let pattern = #"(?:to|for)\s+([A-Za-z0-9+]+)"#
            if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
               let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
                if let rRange = Range(match.range(at: 1), in: trimmed) {
                    let matched = String(trimmed[rRange])
                    if matched.lowercased() == "her" || matched.lowercased() == "him" {
                        recipient = lastWhatsAppRecipient
                    } else {
                        recipient = matched.capitalized
                        lastWhatsAppRecipient = recipient
                    }
                }
            }
            return PlannedTask(
                speechResponse: nil,
                actions: [.sendWhatsAppVoiceNote(recipient: recipient)]
            )
        }

        // 2.3 WhatsApp Open Contact
        let openContactPatterns = [
            #"(?:open|show)\s+(?:the\s+)?([A-Za-z0-9+]+)(?:'s)?\s+(?:contact|chat)\s+(?:on|in)?\s*whatsapp"#,
            #"(?:open|show)\s+whatsapp\s+and\s+(?:open|show|search\s+for)\s+(?:the\s+)?([A-Za-z0-9+]+)(?:'s)?\s*(?:contact|chat)?"#,
            #"(?:open|show)\s+(?:contact|chat\s+with)\s+([A-Za-z0-9+]+)\s+(?:on|in)?\s*whatsapp"#,
            #"(?:open)\s+([A-Za-z0-9+]+)\s+on\s+whatsapp"#
        ]
        var contactToOpen: String? = nil
        for pat in openContactPatterns {
            if let regex = try? NSRegularExpression(pattern: pat, options: .caseInsensitive),
               let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)),
               match.numberOfRanges > 1,
               let rRange = Range(match.range(at: 1), in: trimmed) {
                let matched = String(trimmed[rRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !matched.isEmpty && matched.lowercased() != "whatsapp" {
                    contactToOpen = matched.capitalized
                    lastWhatsAppRecipient = contactToOpen!
                    break
                }
            }
        }
        if let contact = contactToOpen {
            return PlannedTask(
                speechResponse: "Opening \(contact) on WhatsApp.",
                actions: [.openWhatsApp(recipient: contact)]
            )
        }

        // Just "open whatsapp" or "launch whatsapp"
        if lower == "open whatsapp" || lower == "launch whatsapp" || lower == "start whatsapp" {
            return PlannedTask(
                speechResponse: "Opening WhatsApp.",
                actions: [.launchApp(name: "WhatsApp")]
            )
        }

        // 2.5 WhatsApp Text Messaging
        if (lower.contains("whatsapp") && (lower.contains("text") || lower.contains("message") || lower.contains("tell") || lower.contains("send")))
            || lower.hasPrefix("text ") || lower.hasPrefix("message ") || lower.hasPrefix("tell ") {
            var recipient = lastWhatsAppRecipient
            var message = "Good morning"

            let patterns = [
                #"(?:open\s+whatsapp\s+and\s+text|open\s+whatsapp\s+and\s+message)\s+(?:to\s+)?([A-Za-z0-9+]+)\s+(?:saying\s+)?(.+)"#,
                #"(?:send\s+a\s+text\s+to|send\s+text\s+to|send\s+message\s+to)\s+([A-Za-z0-9+]+)\s+(?:saying\s+)?(.+)"#,
                #"(?:text|message)\s+(?:to\s+)?([A-Za-z0-9+]+)\s+(?:saying\s+)?(.+)"#,
                #"(?:whatsapp)\s+(?:to\s+)?([A-Za-z0-9+]+)\s+(?:saying\s+)?(.+)"#,
                #"(?:tell)\s+([A-Za-z0-9+]+)\s+(?:that\s+)?(.+)"#
            ]
            for pat in patterns {
                if let regex = try? NSRegularExpression(pattern: pat, options: .caseInsensitive),
                   let match = regex.firstMatch(in: trimmed, range: NSRange(trimmed.startIndex..., in: trimmed)) {
                    if match.numberOfRanges > 1, let rRange = Range(match.range(at: 1), in: trimmed) {
                        let matched = String(trimmed[rRange])
                        if matched.lowercased() == "her" || matched.lowercased() == "him" {
                            recipient = lastWhatsAppRecipient
                        } else {
                            recipient = matched.capitalized
                            lastWhatsAppRecipient = recipient
                        }
                    }
                    if match.numberOfRanges > 2, let mRange = Range(match.range(at: 2), in: trimmed) {
                        let msg = String(trimmed[mRange]).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !msg.isEmpty {
                            message = msg
                        }
                    }
                    break
                }
            }

            return PlannedTask(
                speechResponse: nil,
                actions: [.sendWhatsApp(recipient: recipient, message: message, confirmFirst: false)]
            )
        }

        // 2.8 Emails (summarize/read/check emails from Apple Mail)
        if lower.contains("email") || lower.contains("mail") || lower.contains("inbox") {
            var count = 5
            let numberWords: [String: Int] = [
                "one": 1, "two": 2, "three": 3, "four": 4, "five": 5,
                "six": 6, "seven": 7, "eight": 8, "nine": 9, "ten": 10
            ]
            for (word, n) in numberWords {
                if lower.contains(word) {
                    count = n
                    break
                }
            }
            let digits = lower.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
            if let n = Int(digits), n > 0 && n <= 20 {
                count = n
            }
            return PlannedTask(
                speechResponse: "Checking your inbox for the latest \(count) emails.",
                actions: [.readRecentEmails(count: count)]
            )
        }

        // 3. Volume Control
        if lower.contains("volume") {
            let digits = lower.components(separatedBy: CharacterSet.decimalDigits.inverted).joined()
            let pct = Int(digits) ?? 50
            return PlannedTask(
                speechResponse: "Setting volume to \(pct) percent.",
                actions: [.setVolume(percent: pct)]
            )
        }

        // 4. Open Chrome / Website
        if lower.contains("open chrome") || lower.contains("google chrome") {
            return PlannedTask(
                speechResponse: "Opening Google Chrome.",
                actions: [.openChrome(url: "https://google.com")]
            )
        }

        // 5. Play / Pause media
        if lower == "pause" || lower == "play" || lower == "toggle playback" || lower.contains("stop music") || lower.contains("pause music") {
            return PlannedTask(
                speechResponse: "Toggling playback.",
                actions: [.togglePlayPause]
            )
        }

        // 6. Switch Window / Move to Monitor
        if lower.contains("switch window") || lower.contains("move window") || lower.contains("switch display") || lower.contains("switch monitor") || lower.contains("move to monitor") || lower.contains("move to mac") {
            return PlannedTask(
                speechResponse: "Switching window display.",
                actions: [.switchWindow]
            )
        }

        // 7. Close YouTube / Windows / Applications
        if lower.contains("close youtube") || lower.contains("stop youtube") || lower.contains("close the youtube") {
            return PlannedTask(
                speechResponse: "Closing YouTube.",
                actions: [.closeYouTube]
            )
        }
        if lower.contains("close spotify") || lower.contains("quit spotify") {
            return PlannedTask(
                speechResponse: "Closing Spotify.",
                actions: [.closeApp(name: "Spotify")]
            )
        }
        if lower.contains("close whatsapp") || lower.contains("quit whatsapp") {
            return PlannedTask(
                speechResponse: "Closing WhatsApp.",
                actions: [.closeApp(name: "WhatsApp")]
            )
        }
        if lower.contains("close chrome") || lower.contains("quit chrome") {
            return PlannedTask(
                speechResponse: "Closing Chrome.",
                actions: [.closeApp(name: "Google Chrome")]
            )
        }
        if lower == "close window" || lower == "close this window" || lower == "close tab" || lower == "close this tab" || lower.contains("close the window") {
            return PlannedTask(
                speechResponse: "Closing window.",
                actions: [.closeActiveWindow]
            )
        }

        // 8. Battery & Power Status
        if lower.contains("battery") || lower.contains("power status") {
            return PlannedTask(
                speechResponse: nil,
                actions: [.getBatteryStatus]
            )
        }

        // 9. Lock Screen / Sleep Display
        if lower.contains("lock screen") || lower.contains("lock mac") || lower.contains("lock the screen") || lower == "lock" {
            return PlannedTask(
                speechResponse: "Locking screen.",
                actions: [.lockScreen]
            )
        }

        return nil
    }
}

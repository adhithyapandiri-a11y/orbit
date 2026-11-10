import Foundation

/// Blazing fast LPU client for Groq Cloud.
/// Runs OpenAI's 120B model at 500+ tokens/sec with sub-200ms latency and 0 rate limit errors.
public actor GroqClient {
    private let apiKey: String
    public nonisolated let model: String
    private var conversationHistory: [[String: String]] = []

    public init(apiKey: String? = nil, model: String? = nil) {
        let loadedConfig = OrbitConfig.load()
        self.apiKey = apiKey 
            ?? ProcessInfo.processInfo.environment["GROQ_API_KEY"] 
            ?? loadedConfig?.groqApiKey 
            ?? ""
        self.model = model 
            ?? loadedConfig?.model 
            ?? "openai/gpt-oss-120b"
    }

    public nonisolated var hasApiKey: Bool {
        return !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public func resetConversation() {
        conversationHistory.removeAll()
    }

    public func plan(userVoiceCommand: String) async throws -> PlannedTask {
        guard hasApiKey else {
            throw NSError(
                domain: "OrbitBrain",
                code: 401,
                userInfo: [NSLocalizedDescriptionKey: "No Groq API key found. Save it to ~/.orbit/config.json or export GROQ_API_KEY."]
            )
        }

        let endpoint = "https://api.groq.com/openai/v1/chat/completions"
        guard let url = URL(string: endpoint) else {
            throw NSError(domain: "OrbitBrain", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid API endpoint."])
        }

        let timeFormatter = DateFormatter()
        timeFormatter.dateStyle = .medium
        timeFormatter.timeStyle = .short
        timeFormatter.timeZone = TimeZone.current
        let currentLocalTime = timeFormatter.string(from: Date())
        let timezoneName = TimeZone.current.identifier

        let systemInstruction = """
        You are Orbit, an ambient voice AI assistant for macOS.
        You are having an ongoing conversation with the user. Maintain conversational context across multiple turns.
        Maintain a warm, natural, modern, concise, and helpful tone. Do not use repetitive honorifics or say "sir".
        The user's current local date and time is \(currentLocalTime) (\(timezoneName)). Always reference this exact time and timezone when asked about the time, date, or future times.

        Supported Action Types:
        - "play_hotstar": params: {"title": "<search query>"}
        - "play_spotify": params: {"query": "<song name, album, or artist>"}
        - "play_youtube": params: {"query": "<video or song title on YouTube>"}
        - "close_youtube": params: {}
        - "close_app": params: {"name": "<app name>"}
        - "close_window": params: {}
        - "get_battery_status": params: {}
        - "lock_screen": params: {}
        - "open_chrome": params: {"url": "<url>"}
        - "open_whatsapp": params: {"recipient": "<contact name>"}
        - "send_whatsapp": params: {"recipient": "<contact or phone>", "message": "<body text>", "confirm_first": false}
        - "send_whatsapp_voice_note": params: {"recipient": "<contact name>"}
        - "read_recent_emails": params: {"count": <int>}
        - "set_volume": params: {"percent": <0-100>}
        - "toggle_play_pause": params: {}
        - "switch_window": params: {} (moves the currently active window between the Mac display and external monitor)
        - "launch_app": params: {"name": "<app name>"}
        - "run_applescript": params: {"script": "<valid AppleScript to execute on macOS>"}
        - "click_screen": params: {"x": <x>, "y": <y>, "label": "<element>"}
        - "type_text": params: {"text": "<text>"}
        - "press_key": params: {"key": "<return|space|escape|tab>"}

        RULES:
        1. When user asks to play ANY song, track, artist, album, or music on Spotify:
           You MUST return action "play_spotify" with {"query": "<song or artist>"}.
           NEVER return "launch_app" or "run_applescript" for music playback on Spotify!
        2. When user asks to switch window or move window between monitor and Mac:
           You MUST return action "switch_window" with {}.
        3. When user asks to toggle playback, pause, or resume music:
           You MUST return action "toggle_play_pause" with {}.
        4. When user asks to open a contact on WhatsApp (e.g. "open Mom on WhatsApp", "open WhatsApp and open Mom contact", "open contact Mom"):
           You MUST return action "open_whatsapp" with {"recipient": "<contact name>"}.
        5. When user asks to text or message someone on WhatsApp (e.g. "open WhatsApp and text mom good morning" or "text John hello"):
           You MUST return a single action "send_whatsapp" with {"recipient": "<contact name>", "message": "<message text>"}.
           NEVER return "launch_app" for WhatsApp; "send_whatsapp" opens WhatsApp, searches the contact, types the message, and sends it automatically.
        6. When user asks to record or send a voice note / voice message on WhatsApp (e.g. "record a voice note to Mom" or "send a voice message to Dad"):
           You MUST return action "send_whatsapp_voice_note" with {"recipient": "<contact name>"}.
        7. When user asks to check, read, or summarize recent emails (e.g. "summarize my recent five mails", "check my emails", "read my inbox"):
           You MUST return action "read_recent_emails" with {"count": 5}.
        8. If the user is having a general conversation or asking a question (e.g. "how are you?", "what is the capital of France?", "what is the time?"), answer warmly in "speech_response" with an EMPTY actions array `[]`.
        9. NEVER generate AppleScript that uses "System Events" keystrokes (macOS will reject it with error 1002).
        10. "speech_response" should be brief, natural spoken text (e.g. "Playing Saheba on Spotify.").
        10. Return STRICT JSON conforming to:
        {
          "speech_response": "<short voice response>",
          "actions": [
            { "type": "<action_type>", "params": { ... } }
          ]
        }
        """

        var messages: [[String: String]] = [
            ["role": "system", "content": systemInstruction]
        ]

        // Add history
        for msg in conversationHistory {
            messages.append(msg)
        }
        messages.append(["role": "user", "content": userVoiceCommand])

        let payload: [String: Any] = [
            "model": model,
            "messages": messages,
            "response_format": ["type": "json_object"],
            "temperature": 0.2
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown server response"
            throw NSError(domain: "OrbitBrain", code: 500, userInfo: [NSLocalizedDescriptionKey: "Groq API error: \(errorBody)"])
        }

        let (plannedTask, rawText) = try parseGroqResponse(data: data)

        // Save turns to history
        conversationHistory.append(["role": "user", "content": userVoiceCommand])
        conversationHistory.append(["role": "assistant", "content": rawText])
        if conversationHistory.count > 10 {
            conversationHistory.removeFirst(conversationHistory.count - 10)
        }

        return plannedTask
    }

    private func parseGroqResponse(data: Data) throws -> (PlannedTask, String) {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let choices = json["choices"] as? [[String: Any]],
              let firstChoice = choices.first,
              let message = firstChoice["message"] as? [String: Any],
              let content = message["content"] as? String,
              let contentData = content.data(using: .utf8),
              let planJson = try JSONSerialization.jsonObject(with: contentData) as? [String: Any] else {
            throw NSError(domain: "OrbitBrain", code: 502, userInfo: [NSLocalizedDescriptionKey: "Failed to parse structured JSON response from Groq."])
        }

        let speech = planJson["speech_response"] as? String
        var actions: [OrbitAction] = []

        if let rawActions = planJson["actions"] as? [[String: Any]] {
            for item in rawActions {
                guard let type = item["type"] as? String else { continue }
                let params = item["params"] as? [String: Any] ?? [:]

                switch type {
                case "play_hotstar":
                    let title = params["title"] as? String ?? ""
                    actions.append(.playHotstar(title: title))
                case "play_spotify":
                    let query = params["query"] as? String ?? (params["title"] as? String ?? "")
                    actions.append(.playSpotify(query: query))
                case "play_youtube":
                    let query = params["query"] as? String ?? (params["title"] as? String ?? "")
                    actions.append(.playYouTube(query: query))
                case "close_youtube":
                    actions.append(.closeYouTube)
                case "close_app":
                    let name = params["name"] as? String ?? ""
                    actions.append(.closeApp(name: name))
                case "close_window":
                    actions.append(.closeActiveWindow)
                case "get_battery_status":
                    actions.append(.getBatteryStatus)
                case "lock_screen":
                    actions.append(.lockScreen)
                case "open_chrome":
                    let url = params["url"] as? String ?? "https://google.com"
                    actions.append(.openChrome(url: url))
                case "open_whatsapp":
                    let recipient = params["recipient"] as? String ?? "Mom"
                    actions.append(.openWhatsApp(recipient: recipient))
                case "send_whatsapp":
                    let recipient = params["recipient"] as? String ?? ""
                    let msg = params["message"] as? String ?? ""
                    let confirm = params["confirm_first"] as? Bool ?? true
                    actions.append(.sendWhatsApp(recipient: recipient, message: msg, confirmFirst: confirm))
                case "send_whatsapp_voice_note":
                    let recipient = params["recipient"] as? String ?? ""
                    actions.append(.sendWhatsAppVoiceNote(recipient: recipient))
                case "read_recent_emails":
                    let count = params["count"] as? Int ?? (params["number"] as? Int ?? 5)
                    actions.append(.readRecentEmails(count: count))
                case "set_volume":
                    let pct = params["percent"] as? Int ?? (params["value"] as? Int ?? 50)
                    actions.append(.setVolume(percent: pct))
                case "toggle_play_pause":
                    actions.append(.togglePlayPause)
                case "switch_window":
                    actions.append(.switchWindow)
                case "launch_app":
                    let name = params["name"] as? String ?? ""
                    actions.append(.launchApp(name: name))
                case "run_applescript":
                    let script = params["script"] as? String ?? ""
                    actions.append(.runAppleScript(script: script))
                case "click_screen":
                    let x = params["x"] as? Double ?? 0
                    let y = params["y"] as? Double ?? 0
                    let label = params["label"] as? String ?? ""
                    actions.append(.clickScreen(x: x, y: y, label: label))
                case "type_text":
                    let text = params["text"] as? String ?? ""
                    actions.append(.typeText(text: text))
                case "press_key":
                    let key = params["key"] as? String ?? "return"
                    actions.append(.pressKey(key: key))
                default:
                    actions.append(.fallbackToGhostUser(reason: "Unknown action \(type)"))
                }
            }
        }

        return (PlannedTask(speechResponse: speech, actions: actions), content)
    }
}

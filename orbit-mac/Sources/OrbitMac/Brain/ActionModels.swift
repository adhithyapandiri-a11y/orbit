import Foundation

/// Action parsed from user intent by AI Planner
public enum OrbitAction: Sendable {
    case speak(text: String)
    case playHotstar(title: String)
    case playSpotify(query: String)
    case playYouTube(query: String)
    case openChrome(url: String)
    case openWhatsApp(recipient: String)
    case sendWhatsApp(recipient: String, message: String, confirmFirst: Bool)
    case sendWhatsAppVoiceNote(recipient: String)
    case readRecentEmails(count: Int)
    case setVolume(percent: Int)
    case togglePlayPause
    case switchWindow
    case closeYouTube
    case closeApp(name: String)
    case closeActiveWindow
    case launchApp(name: String)
    case clickScreen(x: Double, y: Double, label: String)
    case typeText(text: String)
    case pressKey(key: String)
    case runAppleScript(script: String)
    case fallbackToGhostUser(reason: String)
}

public struct PlannedTask: Sendable {
    public let speechResponse: String?
    public let actions: [OrbitAction]
    
    public init(speechResponse: String?, actions: [OrbitAction]) {
        self.speechResponse = speechResponse
        self.actions = actions
    }
}

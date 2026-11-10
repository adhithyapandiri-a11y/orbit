import AppKit
import Foundation

/// MenuBarManager hosts Orbit's native macOS Menu Bar Status Item (🪐).
/// Gives the user a visual status indicator and fallback controls (Pause, Resume, Wake, Logs, Settings, Quit).
@MainActor
public final class MenuBarManager: NSObject, NSMenuDelegate {
    private var statusItem: NSStatusItem?
    private weak var engine: OrbitEngine?
    public private(set) var currentMode: ListeningMode = .wakeWord

    public init(engine: OrbitEngine) {
        self.engine = engine
        super.init()
        setupMenuBar()
        engine.onListeningModeChanged = { [weak self] mode in
            Task { @MainActor in
                self?.updateMode(mode)
            }
        }
    }

    public func setupMenuBar() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem?.button {
            button.title = "🪐"
            button.toolTip = "Orbit for macOS"
        }
        rebuildMenu()
    }

    public func updateMode(_ mode: ListeningMode) {
        self.currentMode = mode
        if let button = statusItem?.button {
            switch mode {
            case .wakeWord:
                button.title = "🪐"
            case .activeConversation:
                button.title = "🪐🎙️"
            case .paused:
                button.title = "🪐💤"
            }
        }
        rebuildMenu()
    }

    public func updateStatus(listening: Bool) {
        updateMode(listening ? .wakeWord : .paused)
    }

    public func rebuildMenu() {
        let menu = NSMenu()
        menu.delegate = self

        // Status Header
        let statusTitle: String
        switch currentMode {
        case .wakeWord:
            statusTitle = "Orbit: 🟢 Listening (\"Hey Orbit\")"
        case .activeConversation:
            statusTitle = "Orbit: 🔵 Active Conversation"
        case .paused:
            statusTitle = "Orbit: 🔴 Paused"
        }
        let statusMenuItem = NSMenuItem(title: statusTitle, action: nil, keyEquivalent: "")
        statusMenuItem.isEnabled = false
        menu.addItem(statusMenuItem)

        menu.addItem(NSMenuItem.separator())

        // Toggle Listen / Pause
        let isPaused = (currentMode == .paused)
        let toggleTitle = isPaused ? "Resume Listening" : "Pause Listening"
        let toggleItem = NSMenuItem(title: toggleTitle, action: #selector(toggleListening), keyEquivalent: "p")
        toggleItem.target = self
        menu.addItem(toggleItem)

        // Wake manually (Fallback without speaking wake-word)
        let wakeItem = NSMenuItem(title: "Wake Orbit Now", action: #selector(wakeNow), keyEquivalent: "w")
        wakeItem.target = self
        menu.addItem(wakeItem)

        menu.addItem(NSMenuItem.separator())

        // Quick Controls
        let playPauseItem = NSMenuItem(title: "Toggle Music Play/Pause", action: #selector(togglePlayPause), keyEquivalent: " ")
        playPauseItem.target = self
        menu.addItem(playPauseItem)

        menu.addItem(NSMenuItem.separator())

        // Open Config
        let configItem = NSMenuItem(title: "Settings (Open config.json)", action: #selector(openConfig), keyEquivalent: ",")
        configItem.target = self
        menu.addItem(configItem)

        // View Logs
        let logsItem = NSMenuItem(title: "View Live Logs", action: #selector(viewLogs), keyEquivalent: "l")
        logsItem.target = self
        menu.addItem(logsItem)

        menu.addItem(NSMenuItem.separator())

        // Quit Orbit
        let quitItem = NSMenuItem(title: "Quit Orbit", action: #selector(quitOrbit), keyEquivalent: "q")
        quitItem.target = self
        menu.addItem(quitItem)

        statusItem?.menu = menu
    }

    @objc private func toggleListening() {
        guard let engine = engine else { return }
        if currentMode != .paused {
            engine.transcriber.pauseListening()
            updateMode(.paused)
            Task {
                await engine.synthesizer.speak("Orbit paused.")
            }
        } else {
            engine.transcriber.startListeningForWakeWord()
            updateMode(.wakeWord)
            Task {
                await engine.synthesizer.speak("Orbit resumed.")
            }
        }
    }

    @objc private func wakeNow() {
        guard let engine = engine else { return }
        updateStatus(listening: true)
        engine.transcriber.playWakeChime()
        Task {
            await engine.synthesizer.speak("I'm listening.")
            engine.transcriber.startActiveConversation(timeoutSeconds: 12.0)
        }
    }

    @objc private func togglePlayPause() {
        guard let engine = engine else { return }
        Task {
            _ = try? await engine.automation.togglePlayPause()
        }
    }

    @objc private func openConfig() {
        let configPath = OrbitConfig.defaultConfigPath.path
        NSWorkspace.shared.open(URL(fileURLWithPath: configPath))
    }

    @objc private func viewLogs() {
        let logPath = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent(".orbit")
            .appendingPathComponent("orbit.log").path
        NSWorkspace.shared.open(URL(fileURLWithPath: logPath))
    }

    @objc private func quitOrbit() {
        NSApplication.shared.terminate(nil)
    }
}

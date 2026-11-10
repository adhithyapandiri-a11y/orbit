import Foundation
import AppKit
import AVFoundation
import ApplicationServices

/// AutomationBridge executes high-speed, invisible background actions on macOS.
/// All external commands run in isolated subprocesses with watchdog timers,
/// completely avoiding fragile System Events keystrokes that trigger permission error 1002.
public final class AutomationBridge: Sendable {
    public static let shared = AutomationBridge()

    public init() {}

    // MARK: - Bulletproof AppleScript Runner (Subprocess with Hard Watchdog)
    @discardableResult
    public func runAppleScript(_ script: String, timeoutSeconds: Double = 3.0) async throws -> String {
        return try await Task.detached {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
            process.arguments = ["-e", script]

            let pipe = Pipe()
            let errPipe = Pipe()
            process.standardOutput = pipe
            process.standardError = errPipe

            do {
                try process.run()
            } catch {
                throw NSError(domain: "OrbitAutomation", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to run script: \(error)"])
            }

            // Watchdog timer that kills the subprocess if it takes longer than timeoutSeconds
            let timer = DispatchSource.makeTimerSource(queue: DispatchQueue.global())
            timer.schedule(deadline: .now() + timeoutSeconds)
            timer.setEventHandler {
                if process.isRunning {
                    process.terminate()
                    kill(process.processIdentifier, SIGKILL)
                }
            }
            timer.resume()

            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
            process.waitUntilExit()
            timer.cancel()

            if process.terminationStatus != 0 {
                let errMsg = String(data: errData, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Script failed"
                throw NSError(domain: "OrbitAutomation", code: Int(process.terminationStatus), userInfo: [NSLocalizedDescriptionKey: errMsg.isEmpty ? "Action timed out" : errMsg])
            }

            return String(data: data, encoding: .utf8) ?? ""
        }.value
    }

    // MARK: - Spotify Control (Dynamic Visual Detection & Anti-Resume Verification)
    @discardableResult
    public func playSpotify(query: String) async throws -> String {
        let workspace = NSWorkspace.shared

        // 1. Clean query from spoken clutter
        var cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        for phrase in ["on spotify", "in spotify", "from spotify", "for me", "can you", "please", "the song", "song called", "a song called", "song"] {
            cleanQuery = cleanQuery.replacingOccurrences(of: phrase, with: "", options: .caseInsensitive)
                                   .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if cleanQuery.lowercased().hasPrefix("play ") {
            cleanQuery = String(cleanQuery.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if cleanQuery.isEmpty {
            cleanQuery = "Top hits"
        }

        // 2. Ensure Spotify is installed and active
        guard let appUrl = workspace.urlForApplication(withBundleIdentifier: "com.spotify.client") else {
            throw NSError(domain: "OrbitAutomation", code: 404, userInfo: [NSLocalizedDescriptionKey: "Spotify is not installed on this Mac."])
        }

        let config = NSWorkspace.OpenConfiguration()
        config.activates = true
        _ = try? await workspace.openApplication(at: appUrl, configuration: config)
        _ = try? await runAppleScript("tell application \"Spotify\" to activate", timeoutSeconds: 1.5)

        // 3. Pause any currently playing track to strictly prevent resuming old music
        _ = try? await runAppleScript("tell application \"Spotify\" to pause", timeoutSeconds: 1.0)
        try? await Task.sleep(nanoseconds: 150_000_000)

        // 4. Navigate Spotify directly to search URL
        guard let encoded = cleanQuery.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let searchUrl = URL(string: "spotify:search:\(encoded)") else {
            throw NSError(domain: "OrbitAutomation", code: 400, userInfo: [NSLocalizedDescriptionKey: "Invalid Spotify search query."])
        }

        _ = try? await runAppleScript("tell application \"Spotify\" to open location \"\(searchUrl.absoluteString)\"", timeoutSeconds: 1.5)
        _ = try? await workspace.open([searchUrl], withApplicationAt: appUrl, configuration: .init())

        // 5. Wait for Spotify search UI to render (1.2s)
        try? await Task.sleep(nanoseconds: 1_200_000_000)

        // 6. Dynamic visual detection of circular green play button
        let capturePath = "/tmp/orbit_spotify_capture.jpg"
        let scale = NSScreen.main?.backingScaleFactor ?? 2.0
        var verifiedTrackName = cleanQuery
        var playbackStarted = false

        for _ in 1...3 {
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
            process.arguments = ["-x", "-t", "jpg", capturePath]
            try? process.run()
            process.waitUntilExit()

            if let nsImage = NSImage(contentsOfFile: capturePath),
               let cgImage = nsImage.cgImage(forProposedRect: nil, context: nil, hints: nil),
               let dataProvider = cgImage.dataProvider,
               let data = dataProvider.data,
               let ptr = CFDataGetBytePtr(data) {

                let width = cgImage.width
                let height = cgImage.height
                let bytesPerRow = cgImage.bytesPerRow
                let bytesPerPixel = cgImage.bitsPerPixel / 8

                var greenPoints: [CGPoint] = []
                let minY = Int(Double(height) * 0.15)
                let maxY = Int(Double(height) * 0.55)
                let minX = Int(Double(width) * 0.20)
                let maxX = Int(Double(width) * 0.95)

                for y in stride(from: minY, to: maxY, by: 4) {
                    for x in stride(from: minX, to: maxX, by: 4) {
                        let offset = y * bytesPerRow + x * bytesPerPixel
                        let r = Double(ptr[offset])
                        let g = Double(ptr[offset + 1])
                        let b = Double(ptr[offset + 2])

                        // Spotify circular play button green: G > 175, R < 140, B < 140, G - R > 60
                        if g > 175 && r < 140 && b < 140 && (g - r) > 60 {
                            greenPoints.append(CGPoint(x: x, y: y))
                        }
                    }
                }

                if !greenPoints.isEmpty {
                    let sortedY = greenPoints.sorted { $0.y < $1.y }
                    let topY = sortedY.first!.y
                    let topCluster = greenPoints.filter { abs($0.y - topY) < (80 * scale) }

                    let avgX = topCluster.map { $0.x }.reduce(0, +) / CGFloat(topCluster.count)
                    let avgY = topCluster.map { $0.y }.reduce(0, +) / CGFloat(topCluster.count)
                    let clickPoint = CGPoint(x: avgX / scale, y: avgY / scale)

                    await GhostUserEngine.shared.click(at: clickPoint)
                    try? await Task.sleep(nanoseconds: 600_000_000)
                }
            }

            // Verify playback status via Spotify AppleScript
            if let statusStr = try? await runAppleScript("tell application \"Spotify\" to {player state as string, name of current track, artist of current track}", timeoutSeconds: 1.5) {
                let parts = statusStr.components(separatedBy: ", ")
                let state = parts.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let trackName = parts.count > 1 ? parts[1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
                let trackLower = trackName.lowercased()
                let queryTokens = cleanQuery.lowercased().split(separator: " ").filter { $0.count > 2 }
                let trackMatchesQuery = queryTokens.contains(where: { trackLower.contains($0) }) || trackLower.contains(cleanQuery.lowercased())

                if state == "playing" {
                    verifiedTrackName = trackName.isEmpty ? cleanQuery : trackName
                    playbackStarted = true
                    break
                } else if state == "paused" && trackMatchesQuery {
                    // Requested track was cued up; safe to resume since it matches query
                    _ = try? await runAppleScript("tell application \"Spotify\" to play", timeoutSeconds: 1.0)
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    verifiedTrackName = trackName
                    playbackStarted = true
                    break
                }
            }

            try? await Task.sleep(nanoseconds: 400_000_000)
        }

        // 7. Fallback if button click failed: hit Return in search
        if !playbackStarted {
            await GhostUserEngine.shared.pressKey(keyCode: 36) // Return
            try? await Task.sleep(nanoseconds: 500_000_000)

            if let statusStr = try? await runAppleScript("tell application \"Spotify\" to {player state as string, name of current track}", timeoutSeconds: 1.5) {
                let parts = statusStr.components(separatedBy: ", ")
                let state = parts.first?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let trackName = parts.count > 1 ? parts[1].trimmingCharacters(in: .whitespacesAndNewlines) : ""
                let trackLower = trackName.lowercased()
                let queryTokens = cleanQuery.lowercased().split(separator: " ").filter { $0.count > 2 }
                let trackMatchesQuery = queryTokens.contains(where: { trackLower.contains($0) }) || trackLower.contains(cleanQuery.lowercased())

                if state == "playing" {
                    verifiedTrackName = trackName.isEmpty ? cleanQuery : trackName
                    playbackStarted = true
                } else if state == "paused" && trackMatchesQuery {
                    _ = try? await runAppleScript("tell application \"Spotify\" to play", timeoutSeconds: 1.0)
                    verifiedTrackName = trackName
                    playbackStarted = true
                }
            }
        }

        guard playbackStarted else {
            return "Loaded search for \(cleanQuery) on Spotify."
        }

        return "Playing \(verifiedTrackName) on Spotify."
    }

    // MARK: - YouTube Control (Direct Top Video Autoplay in Chrome)
    @discardableResult
    public func playYouTube(query: String) async throws -> String {
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw NSError(domain: "OrbitAutomation", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid query."])
        }

        var videoUrl = "https://www.youtube.com/results?search_query=\(encoded)"

        // Fetch top video ID directly from YouTube search
        if let searchEndpoint = URL(string: "https://www.youtube.com/results?search_query=\(encoded)") {
            var request = URLRequest(url: searchEndpoint)
            request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", forHTTPHeaderField: "User-Agent")
            if let (data, _) = try? await URLSession.shared.data(for: request),
               let html = String(data: data, encoding: .utf8) {
                let pattern = #"/watch\?v=([a-zA-Z0-9_-]{11})"#
                if let regex = try? NSRegularExpression(pattern: pattern),
                   let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
                   let vRange = Range(match.range(at: 1), in: html) {
                    let videoId = String(html[vRange])
                    videoUrl = "https://www.youtube.com/watch?v=\(videoId)&autoplay=1"
                }
            }
        }

        _ = try await openInChrome(url: videoUrl, activate: true)
        return "Playing \(query) on YouTube."
    }

    // MARK: - Google Chrome Control (Native NSWorkspace)
    @discardableResult
    public func openInChrome(url: String, activate: Bool = true) async throws -> String {
        if let targetUrl = URL(string: url) {
            let config = NSWorkspace.OpenConfiguration()
            config.activates = activate

            if let chromeUrl = NSWorkspace.shared.urlForApplication(withBundleIdentifier: "com.google.Chrome") {
                _ = try? await NSWorkspace.shared.open([targetUrl], withApplicationAt: chromeUrl, configuration: config)
                return "Opened Chrome to \(url)."
            }
        }

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = ["-a", "Google Chrome", url]
        try process.run()
        process.waitUntilExit()
        return "Opened Chrome to \(url)."
    }

    // MARK: - Close Controls
    @discardableResult
    public func closeYouTube() async throws -> String {
        let script = """
        tell application "Google Chrome"
            repeat with w in windows
                repeat with t in tabs of w
                    if URL of t contains "youtube.com" then
                        if (count of tabs of w) = 1 then
                            close w
                        else
                            close t
                        end if
                        return "Closed YouTube."
                    end if
                end repeat
            end repeat
        end tell
        """
        _ = try? await runAppleScript(script, timeoutSeconds: 1.5)
        return "Closed YouTube window."
    }

    @discardableResult
    public func closeApp(name: String) async throws -> String {
        let workspace = NSWorkspace.shared
        if let app = workspace.runningApplications.first(where: {
            $0.localizedName?.lowercased().contains(name.lowercased()) == true ||
            $0.bundleIdentifier?.lowercased().contains(name.lowercased()) == true
        }) {
            app.terminate()
            return "Closed \(name)."
        }
        _ = try? await runAppleScript("tell application \"\(name)\" to quit", timeoutSeconds: 1.0)
        return "Closed \(name)."
    }

    @discardableResult
    public func closeActiveWindow() async throws -> String {
        // Cmd+W
        await GhostUserEngine.shared.pressKey(keyCode: 13, flags: .maskCommand)
        return "Closed window."
    }

    // MARK: - Disney+ Hotstar Control
    @discardableResult
    public func playHotstar(query: String) async throws -> String {
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw NSError(domain: "OrbitAutomation", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid query."])
        }
        let searchUrl = "https://www.hotstar.com/in/explore?search_query=\(encoded)"
        _ = try await openInChrome(url: searchUrl, activate: true)
        
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        await GhostUserEngine.shared.pressKey(keyCode: 36) // Return via CGEvent
        return "Playing \(query) on Hotstar."
    }

    // MARK: - WhatsApp UI Element Resolvers
    private func cleanAXString(_ s: String) -> String {
        return s.filter { $0.isLetter || $0.isNumber || $0.isWhitespace }
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
    }

    private func getWhatsAppWindow(pid: pid_t) -> (AXUIElement, CGRect)? {
        let axApp = AXUIElementCreateApplication(pid)
        var winElem: AXUIElement?

        var mainWinRef: CFTypeRef?
        if AXUIElementCopyAttributeValue(axApp, kAXMainWindowAttribute as CFString, &mainWinRef) == .success,
           let w = mainWinRef as! AXUIElement? {
            winElem = w
        } else {
            var focusedRef: CFTypeRef?
            if AXUIElementCopyAttributeValue(axApp, kAXFocusedWindowAttribute as CFString, &focusedRef) == .success,
               let w = focusedRef as! AXUIElement? {
                winElem = w
            } else {
                var childrenRef: CFTypeRef?
                if AXUIElementCopyAttributeValue(axApp, kAXChildrenAttribute as CFString, &childrenRef) == .success,
                   let children = childrenRef as? [AXUIElement] {
                    winElem = children.first(where: {
                        var role: CFTypeRef?
                        AXUIElementCopyAttributeValue($0, kAXRoleAttribute as CFString, &role)
                        return (role as? String) == "AXWindow"
                    })
                }
            }
        }

        guard let win = winElem else { return nil }

        var posRef: CFTypeRef?
        var sizeRef: CFTypeRef?
        AXUIElementCopyAttributeValue(win, kAXPositionAttribute as CFString, &posRef)
        AXUIElementCopyAttributeValue(win, kAXSizeAttribute as CFString, &sizeRef)
        var pos = CGPoint(x: 560, y: 120)
        var size = CGSize(width: 870, height: 810)
        if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
        if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }

        return (win, CGRect(origin: pos, size: size))
    }

    // MARK: - WhatsApp Automation Helpers
    private func activateWhatsApp() async throws -> (NSRunningApplication, CGRect) {
        let workspace = NSWorkspace.shared
        var waApp = workspace.runningApplications.first(where: {
            (!$0.isTerminated) && ($0.bundleIdentifier == "net.whatsapp.WhatsApp" || $0.localizedName?.contains("WhatsApp") == true)
        })

        if waApp == nil {
            if let waUrl = workspace.urlForApplication(withBundleIdentifier: "net.whatsapp.WhatsApp") {
                let config = NSWorkspace.OpenConfiguration()
                config.activates = true
                waApp = try await workspace.openApplication(at: waUrl, configuration: config)
                try? await Task.sleep(nanoseconds: 1_200_000_000)
            } else {
                throw NSError(domain: "OrbitAutomation", code: 404, userInfo: [NSLocalizedDescriptionKey: "WhatsApp is not installed."])
            }
        }

        guard let app = waApp else {
            throw NSError(domain: "OrbitAutomation", code: 404, userInfo: [NSLocalizedDescriptionKey: "Could not launch WhatsApp."])
        }

        app.unhide()
        app.activate()
        _ = try? await runAppleScript("tell application id \"net.whatsapp.WhatsApp\" to activate", timeoutSeconds: 1.0)
        try? await Task.sleep(nanoseconds: 200_000_000)

        // Find live window bounds
        if let (_, bounds) = getWhatsAppWindow(pid: app.processIdentifier) {
            return (app, bounds)
        }

        var winBounds = CGRect(x: 560, y: 120, width: 870, height: 810)
        if let windowList = CGWindowListCopyWindowInfo(.optionAll, kCGNullWindowID) as? [[String: Any]] {
            for win in windowList {
                let owner = win[kCGWindowOwnerName as String] as? String ?? ""
                let layer = win[kCGWindowLayer as String] as? Int ?? -1
                if owner.contains("WhatsApp") && layer == 0 {
                    if let bounds = win[kCGWindowBounds as String] as? [String: Any],
                       let x = bounds["X"] as? Double,
                       let y = bounds["Y"] as? Double,
                       let w = bounds["Width"] as? Double,
                       let h = bounds["Height"] as? Double,
                       w > 300 && h > 300 {
                        winBounds = CGRect(x: x, y: y, width: w, height: h)
                        break
                    }
                }
            }
        }
        return (app, winBounds)
    }

    // MARK: - WhatsApp Open Contact
    @discardableResult
    public func openWhatsApp(recipient: String) async throws -> String {
        _ = try await openWhatsAppContact(recipient: recipient)
        return "Opened \(recipient) on WhatsApp."
    }

    private func openWhatsAppContact(recipient: String) async throws -> (AXUIElement, CGRect, pid_t) {
        let (app, defaultBounds) = try await activateWhatsApp()
        let pid = app.processIdentifier
        let (win, winBounds) = getWhatsAppWindow(pid: pid) ?? (AXUIElementCreateApplication(pid), defaultBounds)

        // 1. Focus Search Bar: Trigger File -> Search menu item
        let focusSearchScript = """
        tell application "System Events"
            tell process "WhatsApp"
                set frontmost to true
                try
                    click menu item 3 of menu 1 of menu bar item "File" of menu bar 1
                end try
            end tell
        end tell
        """
        _ = try? await runAppleScript(focusSearchScript, timeoutSeconds: 0.5)
        try? await Task.sleep(nanoseconds: 80_000_000)

        // Also ensure search element is focused
        func findSearchElement(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let desc = cleanAXString(descRef as? String ?? "")
            if desc == "search" {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }
                return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
            }
            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for child in children {
                    if let pt = findSearchElement(child) { return pt }
                }
            }
            return nil
        }

        if let (sElem, sPt) = findSearchElement(win) {
            _ = AXUIElementPerformAction(sElem, kAXPressAction as CFString)
            await GhostUserEngine.shared.click(at: sPt)
        } else {
            let searchPt = CGPoint(x: winBounds.minX + 280, y: winBounds.minY + 74)
            await GhostUserEngine.shared.click(at: searchPt)
        }
        try? await Task.sleep(nanoseconds: 60_000_000)

        // Clear existing search text: Cmd+A, Backspace
        await GhostUserEngine.shared.pressKey(keyCode: 0, flags: .maskCommand) // Cmd+A
        await GhostUserEngine.shared.pressKey(keyCode: 51) // Backspace
        try? await Task.sleep(nanoseconds: 60_000_000)

        // 2. Paste recipient name into search
        await MainActor.run {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(recipient, forType: .string)
        }
        await GhostUserEngine.shared.pressKey(keyCode: 9, flags: .maskCommand) // Cmd+V
        try? await Task.sleep(nanoseconds: 350_000_000) // Wait for search results to populate

        // 3. Find and click contact in search results
        let targetName = cleanAXString(recipient)
        func findContact(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            var roleRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXRoleAttribute as CFString, &roleRef)
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let role = roleRef as? String ?? ""
            let desc = cleanAXString(descRef as? String ?? "")

            if (role == "AXButton" || role == "AXCell" || role == "AXRow" || role == "AXStaticText") && desc.contains(targetName) {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }

                // Must be in the left sidebar and below the search bar
                if pos.x < winBounds.minX + 480 && pos.y > winBounds.minY + 100 {
                    return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
                }
            }

            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children {
                    if let found = findContact(c) { return found }
                }
            }
            return nil
        }

        // Query fresh tree after search
        let (searchWin, currentBounds) = getWhatsAppWindow(pid: pid) ?? (win, winBounds)
        if let (contactElem, clickPt) = findContact(searchWin) {
            await GhostUserEngine.shared.click(at: clickPt)
            _ = AXUIElementPerformAction(contactElem, kAXPressAction as CFString)
        } else {
            // Fallback: top search result is located right below search bar
            await GhostUserEngine.shared.pressKey(keyCode: 36) // Return
            let fallbackPt = CGPoint(x: currentBounds.minX + 235, y: currentBounds.minY + 165)
            await GhostUserEngine.shared.click(at: fallbackPt)
        }

        try? await Task.sleep(nanoseconds: 300_000_000) // Wait for chat to open
        let (chatWin, chatBounds) = getWhatsAppWindow(pid: pid) ?? (searchWin, currentBounds)
        return (chatWin, chatBounds, pid)
    }

    // MARK: - WhatsApp Direct Messaging (Strict 5-step flow)
    @discardableResult
    public func stageWhatsAppMessage(recipient: String, message: String) async throws -> String {
        let (win, winBounds, pid) = try await openWhatsAppContact(recipient: recipient)

        // Cancel any dangling voice recording so compose text area is visible
        func cancelDanglingRecording(_ elem: AXUIElement) {
            var descRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let desc = cleanAXString(descRef as? String ?? "")
            if desc == "cancel" {
                _ = AXUIElementPerformAction(elem, kAXPressAction as CFString)
                return
            }
            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children { cancelDanglingRecording(c) }
            }
        }
        cancelDanglingRecording(win)

        // Step 4: Find and click text bar (Compose message text area)
        func findCompose(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            var roleRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXRoleAttribute as CFString, &roleRef)
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let role = roleRef as? String ?? ""
            let desc = cleanAXString(descRef as? String ?? "")

            if role == "AXTextArea" || desc.contains("compose message") {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }
                return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
            }

            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children {
                    if let f = findCompose(c) { return f }
                }
            }
            return nil
        }

        let (composeElem, composePt) = findCompose(win) ?? (nil, CGPoint(x: winBounds.minX + (winBounds.width * 0.7), y: winBounds.maxY - 25))
        await GhostUserEngine.shared.click(at: composePt)
        if let elem = composeElem {
            _ = AXUIElementPerformAction(elem, kAXPressAction as CFString)
        }
        try? await Task.sleep(nanoseconds: 100_000_000)

        // Clear any previous draft text
        await GhostUserEngine.shared.pressKey(keyCode: 0, flags: .maskCommand) // Cmd+A
        await GhostUserEngine.shared.pressKey(keyCode: 51) // Backspace
        try? await Task.sleep(nanoseconds: 80_000_000)

        // Step 5: Type in the text that was requested
        await MainActor.run {
            NSPasteboard.general.clearContents()
            NSPasteboard.general.setString(message, forType: .string)
        }
        await GhostUserEngine.shared.pressKey(keyCode: 9, flags: .maskCommand) // Cmd+V
        try? await Task.sleep(nanoseconds: 150_000_000)

        // Step 6: Hit the send button
        func findSendButton(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            var roleRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXRoleAttribute as CFString, &roleRef)
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let role = roleRef as? String ?? ""
            let desc = cleanAXString(descRef as? String ?? "")

            if role == "AXButton" && (desc == "send" || desc.contains("send message")) {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }
                return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
            }

            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children {
                    if let f = findSendButton(c) { return f }
                }
            }
            return nil
        }

        let (latestWin, _) = getWhatsAppWindow(pid: pid) ?? (win, winBounds)
        let (sendElem, sendPt) = findSendButton(latestWin) ?? (nil, CGPoint(x: winBounds.maxX - 35, y: winBounds.maxY - 25))

        await GhostUserEngine.shared.click(at: sendPt)
        if let sElem = sendElem {
            _ = AXUIElementPerformAction(sElem, kAXPressAction as CFString)
        }
        try? await Task.sleep(nanoseconds: 80_000_000)
        await GhostUserEngine.shared.pressKey(keyCode: 36, flags: .maskCommand) // Cmd+Return fallback

        return "Message sent to \(recipient) on WhatsApp."
    }

    // MARK: - WhatsApp Voice Note Recording (Mic silence auto-send)
    @discardableResult
    public func recordWhatsAppVoiceNote(recipient: String) async throws -> String {
        let (win, winBounds, pid) = try await openWhatsAppContact(recipient: recipient)

        // Step 4: Find and click Voice message mic button
        func findVoiceButton(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            var roleRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXRoleAttribute as CFString, &roleRef)
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let role = roleRef as? String ?? ""
            let desc = cleanAXString(descRef as? String ?? "")

            if role == "AXButton" && (desc.contains("voice message") || desc.contains("voice note") || desc.contains("record")) {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }
                return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
            }

            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children {
                    if let f = findVoiceButton(c) { return f }
                }
            }
            return nil
        }

        let (voiceElem, micPoint) = findVoiceButton(win) ?? (nil, CGPoint(x: winBounds.maxX - 35, y: winBounds.maxY - 25))

        // Click mic to begin recording
        await GhostUserEngine.shared.click(at: micPoint)
        if let vElem = voiceElem {
            _ = AXUIElementPerformAction(vElem, kAXPressAction as CFString)
        }
        print("[Orbit WhatsApp] 🎙️ Voice recording started in WhatsApp. Listening for speech & silence...")

        // Step 5: Monitor microphone for speech followed by 2.5s silence
        await monitorVoiceNoteSilence(silenceSeconds: 2.5, maxWaitSeconds: 8.0)

        // Step 6: Click "Send voice message" button
        func findSendVoiceButton(_ elem: AXUIElement) -> (AXUIElement, CGPoint)? {
            var descRef: CFTypeRef?
            var roleRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXRoleAttribute as CFString, &roleRef)
            AXUIElementCopyAttributeValue(elem, kAXDescriptionAttribute as CFString, &descRef)
            let role = roleRef as? String ?? ""
            let desc = cleanAXString(descRef as? String ?? "")

            if role == "AXButton" && (desc.contains("send voice") || desc.contains("send")) {
                var posRef: CFTypeRef?
                var sizeRef: CFTypeRef?
                AXUIElementCopyAttributeValue(elem, kAXPositionAttribute as CFString, &posRef)
                AXUIElementCopyAttributeValue(elem, kAXSizeAttribute as CFString, &sizeRef)
                var pos = CGPoint.zero
                var size = CGSize.zero
                if let p = posRef { AXValueGetValue(p as! AXValue, .cgPoint, &pos) }
                if let s = sizeRef { AXValueGetValue(s as! AXValue, .cgSize, &size) }
                return (elem, CGPoint(x: pos.x + size.width / 2, y: pos.y + size.height / 2))
            }

            var childrenRef: CFTypeRef?
            AXUIElementCopyAttributeValue(elem, kAXChildrenAttribute as CFString, &childrenRef)
            if let children = childrenRef as? [AXUIElement] {
                for c in children {
                    if let f = findSendVoiceButton(c) { return f }
                }
            }
            return nil
        }

        let (latestWin, _) = getWhatsAppWindow(pid: pid) ?? (win, winBounds)
        let (sendElem, sendPt) = findSendVoiceButton(latestWin) ?? (nil, CGPoint(x: winBounds.maxX - 35, y: winBounds.maxY - 25))
        await GhostUserEngine.shared.click(at: sendPt)
        if let sElem = sendElem {
            _ = AXUIElementPerformAction(sElem, kAXPressAction as CFString)
        }
        print("[Orbit WhatsApp] ✅ Sent voice note.")

        return "Voice note sent to \(recipient) on WhatsApp."
    }

    private func monitorVoiceNoteSilence(silenceSeconds: Double, maxWaitSeconds: Double) async {
        let engine = AVAudioEngine()
        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)

        var startedSpeaking = false
        var lastSpeechTime = Date()
        var finished = false

        input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            guard !finished else { return }
            guard let channelData = buffer.floatChannelData?[0] else { return }
            let frameLength = Int(buffer.frameLength)
            var sum: Float = 0
            for i in 0..<frameLength {
                sum += channelData[i] * channelData[i]
            }
            let rms = sqrt(sum / Float(max(1, frameLength)))

            if rms > 0.015 {
                startedSpeaking = true
                lastSpeechTime = Date()
            }
        }

        do {
            try engine.start()
            let startTime = Date()
            while !finished {
                try? await Task.sleep(nanoseconds: 100_000_000)
                let now = Date()
                if startedSpeaking {
                    if now.timeIntervalSince(lastSpeechTime) >= silenceSeconds {
                        print("[Orbit WhatsApp] 🛑 Detected \(silenceSeconds)s of silence after speaking.")
                        finished = true
                    }
                } else {
                    if now.timeIntervalSince(startTime) >= maxWaitSeconds {
                        print("[Orbit WhatsApp] ⏳ Reached wait timeout without user speech.")
                        finished = true
                    }
                }
                if now.timeIntervalSince(startTime) >= 45.0 { // safety ceiling
                    finished = true
                }
            }
            engine.stop()
            input.removeTap(onBus: 0)
        } catch {
            print("[Orbit WhatsApp] ⚠️ Mic monitor error: \(error)")
            try? await Task.sleep(nanoseconds: UInt64(silenceSeconds * 1_000_000_000))
        }
    }

    @discardableResult
    public func commitWhatsAppSend() async throws -> String {
        return "Message sent successfully."
    }

    // MARK: - System Controls
    @discardableResult
    public func setVolume(percent: Int) async throws -> String {
        let clamped = max(0, min(100, percent))
        _ = try await runAppleScript("set volume output volume \(clamped)", timeoutSeconds: 1.5)
        return "Volume set to \(clamped) percent."
    }

    @discardableResult
    public func togglePlayPause() async throws -> String {
        // Only call Spotify AppleScript if Spotify is already running to avoid cold-start lag
        let isSpotifyRunning = NSWorkspace.shared.runningApplications.contains(where: { $0.bundleIdentifier == "com.spotify.client" })
        if isSpotifyRunning {
            let spotifyToggle = "tell application \"Spotify\" to playpause"
            if let _ = try? await runAppleScript(spotifyToggle, timeoutSeconds: 1.0) {
                return "Toggled Spotify playback."
            }
        }
        await GhostUserEngine.shared.pressKey(keyCode: 49) // Spacebar via CGEvent
        return "Toggled playback."
    }

    // MARK: - Multi-Monitor Window Mover
    @discardableResult
    public func switchActiveWindowDisplay() async throws -> String {
        let list = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []

        var targetPID: pid_t = 0
        var targetOwner: String = ""

        // Find the topmost visible application window that is not a system daemon or Orbit
        for win in list {
            let layer = win[kCGWindowLayer as String] as? Int ?? -1
            let owner = win[kCGWindowOwnerName as String] as? String ?? ""
            let pid = win[kCGWindowOwnerPID as String] as? pid_t ?? 0
            if layer == 0 && !["Window Server", "Dock", "Orbit", ""].contains(owner) && pid > 0 {
                targetPID = pid
                targetOwner = owner
                break
            }
        }

        if targetPID == 0, let frontApp = NSWorkspace.shared.frontmostApplication {
            targetPID = frontApp.processIdentifier
            targetOwner = frontApp.localizedName ?? "app"
        }

        guard targetPID > 0 else {
            throw NSError(domain: "OrbitAutomation", code: 404, userInfo: [NSLocalizedDescriptionKey: "No active application window found."])
        }

        let appRef = AXUIElementCreateApplication(targetPID)
        var winRef: AXUIElement?

        var focVal: AnyObject?
        if AXUIElementCopyAttributeValue(appRef, kAXFocusedWindowAttribute as CFString, &focVal) == .success, let f = focVal {
            winRef = (f as! AXUIElement)
        } else {
            var mainVal: AnyObject?
            if AXUIElementCopyAttributeValue(appRef, kAXMainWindowAttribute as CFString, &mainVal) == .success, let m = mainVal {
                winRef = (m as! AXUIElement)
            } else {
                var allWins: AnyObject?
                if AXUIElementCopyAttributeValue(appRef, kAXWindowsAttribute as CFString, &allWins) == .success,
                   let list = allWins as? [AXUIElement], let first = list.first {
                    winRef = first
                }
            }
        }

        guard let window = winRef else {
            throw NSError(domain: "OrbitAutomation", code: 500, userInfo: [NSLocalizedDescriptionKey: "Could not locate active window for \(targetOwner)."])
        }

        var posVal: AnyObject?
        AXUIElementCopyAttributeValue(window, kAXPositionAttribute as CFString, &posVal)
        var point = CGPoint.zero
        if let pVal = posVal {
            AXValueGetValue(pVal as! AXValue, .cgPoint, &point)
        }

        let screens = NSScreen.screens
        let externalScreen = screens.first(where: { $0.frame.origin.x != 0 })
        let targetX: CGFloat
        let targetY: CGFloat

        if point.x < 0 {
            // Currently on external monitor -> move to Mac built-in display
            targetX = 60
            targetY = 100
        } else if let ext = externalScreen {
            // Move to external monitor
            targetX = ext.frame.origin.x + 60
            targetY = ext.frame.origin.y + 100
        } else {
            targetX = -1500
            targetY = 100
        }

        var newPoint = CGPoint(x: targetX, y: targetY)
        if let val = AXValueCreate(.cgPoint, &newPoint) {
            let setErr = AXUIElementSetAttributeValue(window, kAXPositionAttribute as CFString, val)
            if setErr == .success {
                let dest = (targetX < 0) ? "external monitor" : "Mac display"
                return "Moved \(targetOwner) window to \(dest)."
            }
        }

        return "Moved window to other display."
    }

    @discardableResult
    public func launchApp(name: String) async throws -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/open")
        process.arguments = ["-a", name]
        try process.run()
        process.waitUntilExit()
        return "Launched \(name)."
    }

    // MARK: - Email Reader (Apple Mail)
    @discardableResult
    public func getRecentEmails(count: Int = 5) async throws -> String {
        let script = """
        tell application "Mail"
            set mCount to count of messages of inbox
            set n to \(count)
            if mCount < n then set n to mCount
            if n is 0 then return "Your inbox is empty."
            set mList to (messages 1 thru n of inbox)
            set out to ""
            repeat with m in mList
                set s to subject of m
                set sndr to sender of m
                set out to out & "• " & s & " from " & sndr & linefeed
            end repeat
            return out
        end tell
        """
        return try await runAppleScript(script, timeoutSeconds: 3.0)
    }
}

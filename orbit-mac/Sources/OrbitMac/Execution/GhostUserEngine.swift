import Foundation
import CoreGraphics
import AppKit

/// GhostUserEngine provides the physical UI fallback:
/// taking screenshots, simulating mouse movement, clicks, and keyboard strokes via CGEvent.
public final class GhostUserEngine: Sendable {
    public static let shared = GhostUserEngine()

    public init() {}

    // MARK: - Screen Capture
    /// Captures the main display silently and saves to the given path.
    public func captureScreen(outputPath: String = "/tmp/orbit_screen.jpg") async throws -> String {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
        process.arguments = ["-x", "-t", "jpg", outputPath]
        try process.run()
        process.waitUntilExit()
        return outputPath
    }

    // MARK: - Mouse Control
    /// Moves cursor and performs a click at the specified (x, y) coordinates.
    public func click(at point: CGPoint) async {
        CGWarpMouseCursorPosition(point)
        try? await Task.sleep(nanoseconds: 40_000_000)

        // Mouse Down
        let down = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: point, mouseButton: .left)
        down?.setIntegerValueField(.mouseEventClickState, value: 1)
        down?.post(tap: .cghidEventTap)
        try? await Task.sleep(nanoseconds: 60_000_000)

        // Mouse Up
        let up = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: point, mouseButton: .left)
        up?.setIntegerValueField(.mouseEventClickState, value: 1)
        up?.post(tap: .cghidEventTap)
        try? await Task.sleep(nanoseconds: 40_000_000)
    }

    /// Double click at point
    public func doubleClick(at point: CGPoint) async {
        await click(at: point)
        try? await Task.sleep(nanoseconds: 80_000_000)
        let down2 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseDown, mouseCursorPosition: point, mouseButton: .left)
        down2?.setIntegerValueField(.mouseEventClickState, value: 2)
        down2?.post(tap: .cghidEventTap)
        let up2 = CGEvent(mouseEventSource: nil, mouseType: .leftMouseUp, mouseCursorPosition: point, mouseButton: .left)
        up2?.setIntegerValueField(.mouseEventClickState, value: 2)
        up2?.post(tap: .cghidEventTap)
    }

    // MARK: - Keyboard Simulation
    /// Types a string character by character into whatever field currently has focus.
    public func typeText(_ string: String) async {
        for char in string.utf16 {
            var unichar = char
            let keyDown = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true)
            keyDown?.keyboardSetUnicodeString(stringLength: 1, unicodeString: &unichar)
            keyDown?.post(tap: .cghidEventTap)

            let keyUp = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false)
            keyUp?.keyboardSetUnicodeString(stringLength: 1, unicodeString: &unichar)
            keyUp?.post(tap: .cghidEventTap)

            try? await Task.sleep(nanoseconds: 10_000_000) // 10ms typing cadence
        }
    }

    /// Presses a standard key like Return (36), Escape (53), Space (49), Tab (48), with optional modifier flags.
    public func pressKey(keyCode: CGKeyCode, flags: CGEventFlags = []) async {
        var modifiers: [CGKeyCode] = []
        if flags.contains(.maskCommand) { modifiers.append(55) }
        if flags.contains(.maskShift) { modifiers.append(56) }
        if flags.contains(.maskAlternate) { modifiers.append(58) }
        if flags.contains(.maskControl) { modifiers.append(59) }

        // Modifier keys down
        for mod in modifiers {
            let mDown = CGEvent(keyboardEventSource: nil, virtualKey: mod, keyDown: true)
            mDown?.flags = flags
            mDown?.post(tap: .cghidEventTap)
        }
        if !modifiers.isEmpty {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        let down = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: true)
        let up = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: false)
        if !flags.isEmpty {
            down?.flags = flags
            up?.flags = flags
        }
        down?.post(tap: .cghidEventTap)
        try? await Task.sleep(nanoseconds: 40_000_000)
        up?.post(tap: .cghidEventTap)
        try? await Task.sleep(nanoseconds: 20_000_000)

        // Modifier keys up
        for mod in modifiers.reversed() {
            let mUp = CGEvent(keyboardEventSource: nil, virtualKey: mod, keyDown: false)
            mUp?.post(tap: .cghidEventTap)
        }
    }
}

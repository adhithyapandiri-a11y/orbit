import Foundation
import AppKit

@main
@MainActor
final class OrbitMac: NSObject, NSApplicationDelegate {
    private var engine: OrbitEngine?
    private var menuBar: MenuBarManager?

    static func main() async {
        // Unbuffer stdout so daemon logs write line-by-line immediately
        setlinebuf(stdout)

        let args = Array(CommandLine.arguments.dropFirst())
        let engine = OrbitEngine()

        if let firstArg = args.first, firstArg == "--cli" {
            print("""
            ====================================================
              🪐 ORBIT (macOS Edition) - CLI Mode
            ====================================================
            """)
            await runInteractiveShell(engine: engine)
            return
        } else if !args.isEmpty {
            let command = args.joined(separator: " ")
            await engine.execute(command: command)
            return
        }

        // Full Background Mode with Menu Bar (🪐)
        // Ensure logs are persisted to ~/.orbit/orbit.log
        let logDir = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(".orbit")
        try? FileManager.default.createDirectory(at: logDir, withIntermediateDirectories: true)
        let logFile = logDir.appendingPathComponent("orbit.log")
        if !FileManager.default.fileExists(atPath: logFile.path) {
            FileManager.default.createFile(atPath: logFile.path, contents: nil)
        }
        if let handle = try? FileHandle(forWritingTo: logFile) {
            handle.seekToEndOfFile()
            dup2(handle.fileDescriptor, STDOUT_FILENO)
            dup2(handle.fileDescriptor, STDERR_FILENO)
            setlinebuf(stdout)
            setlinebuf(stderr)
        }

        let app = NSApplication.shared
        let delegate = OrbitMac()
        delegate.engine = engine
        app.delegate = delegate
        app.setActivationPolicy(.accessory)

        NSApp.run()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard let engine = engine else { return }

        print("""
        ====================================================
          🪐 ORBIT (macOS Edition) - Version 1.0
          Engine: Native Swift (Apple Silicon ARM64)
          Mode: Hands-Free Voice ("Hey Orbit") + Menu Bar (🪐)
          Cost: ₹0 / $0 (Local Neural Speech + Fast LPU Brain)
        ====================================================
        """)

        // 1. Initialize native macOS Menu Bar Status Item (🪐)
        self.menuBar = MenuBarManager(engine: engine)
        print("[Orbit] 🪐 Menu Bar item initialized successfully.")

        // 2. Start ambient hands-free voice daemon
        Task { @MainActor in
            let started = await engine.startVoiceDaemon()
            if !started {
                print("[Orbit] ⚠️ Speech/Microphone permissions needed.")
            }
        }
    }

    /// Interactive terminal shell for manual testing if needed
    static func runInteractiveShell(engine: OrbitEngine) async {
        print("\nType a command (e.g. \"play Vision Quest on Hotstar\" or \"text Mom good morning\"):")
        print("Type 'exit' to quit.\n")

        while true {
            print("orbit> ", terminator: "")
            guard let line = readLine()?.trimmingCharacters(in: .whitespacesAndNewlines), !line.isEmpty else {
                continue
            }
            if line.lowercased() == "exit" || line.lowercased() == "quit" {
                print("Goodbye.")
                break
            }
            await engine.execute(command: line)
        }
    }
}

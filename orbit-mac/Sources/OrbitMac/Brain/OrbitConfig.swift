import Foundation

/// Configuration file model stored securely at ~/.orbit/config.json
public struct OrbitConfig: Codable, Sendable {
    public var provider: String? // "groq"
    public var groqApiKey: String?
    public var model: String?
    public var wakeWord: String
    public var voiceFeedback: Bool

    public static let defaultConfigPath = FileManager.default.homeDirectoryForCurrentUser
        .appendingPathComponent(".orbit")
        .appendingPathComponent("config.json")

    public static func load() -> OrbitConfig? {
        guard let data = try? Data(contentsOf: defaultConfigPath),
              let config = try? JSONDecoder().decode(OrbitConfig.self, from: data) else {
            return nil
        }
        return config
    }

    public func save() throws {
        let folder = OrbitConfig.defaultConfigPath.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = .prettyPrinted
        let data = try encoder.encode(self)
        try data.write(to: OrbitConfig.defaultConfigPath, options: .atomic)
    }
}

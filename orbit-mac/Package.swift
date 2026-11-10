// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "OrbitMac",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "orbit", targets: ["OrbitMac"])
    ],
    targets: [
        .executableTarget(
            name: "OrbitMac",
            path: "Sources/OrbitMac"
        )
    ]
)

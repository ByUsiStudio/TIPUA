# TIPUA - The Integration Package Updates Automatically

A NeoForge mod that automatically synchronizes modpacks between server and client.

## Features

- **Server-Side Modpack Monitoring**: Automatically detects changes to the server's modpack
- **HTTP File Server**: Built-in HTTP server for efficient mod distribution
- **Multi-Format Support**: Supports ZIP, folder, CurseForge, and Modrinth modpack formats
- **Auto-Download**: Clients automatically download required mods when joining
- **Hash Verification**: SHA-256 hash verification for secure downloads
- **Configurable**: Fully configurable behavior for both server and client

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.234+

## Installation

### Server Installation

1. Place the TIPUA mod JAR in your server's `mods/` directory
2. Create a `modpacks/` directory in your server root
3. Place your modpack (ZIP file or folder) in the `modpacks/` directory
4. Configure `tipua-common.toml` in the `config/` directory:
   - Set `modpackPath` to your modpack file/directory
   - Adjust `httpPort` if needed (default: 25566)
5. Start the server

### Client Installation

1. Place the TIPUA mod JAR in your client's `mods/` directory
2. Configure `tipua-common.toml` in the `config/` directory (optional):
   - Set `autoUpdate` to enable/disable automatic updates
   - Set `autoDownloadMods` to enable/disable automatic downloads
3. Connect to a server running TIPUA
4. The mod will automatically detect and download required mods

## Configuration

### Server Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `httpPort` | HTTP server port | 25566 |
| `modpackPath` | Path to modpack file/directory | modpacks/default.zip |
| `checkIntervalSeconds` | Modpack check interval | 60 |
| `enableFileServer` | Enable built-in HTTP server | true |
| `enableHashVerification` | Enable SHA-256 verification | true |
| `defaultModpackFormat` | Default format: zip, folder, curseforge, modrinth | zip |

### Client Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `autoUpdate` | Auto-check for updates on join | true |
| `autoDownloadMods` | Auto-download required mods | true |
| `enableHashVerification` | Enable SHA-256 verification | true |
| `downloadTimeoutSeconds` | Download timeout | 300 |
| `maxConcurrentDownloads` | Max concurrent downloads | 3 |
| `showUpdateNotification` | Show toast notifications | true |
| `askBeforeInstall` | Ask confirmation before install | true |
| `ignoredMods` | List of mod IDs to ignore | [] |

## Supported Modpack Formats

1. **ZIP**: Standard ZIP file containing mods/ directory
2. **Folder**: Directory containing mods/ directory
3. **CurseForge**: CurseForge modpack format with manifest.json
4. **Modrinth**: Modrinth modpack format with modrinth.index.json

## How It Works

1. **Handshake**: When a client joins, the server sends the current modpack hash
2. **Request**: Client compares hash with local hash and requests update if needed
3. **Manifest**: Server sends list of mods with download URLs and hashes
4. **Download**: Client downloads missing/updated mods via HTTP
5. **Verification**: Client verifies downloads using SHA-256 hashes
6. **Restart**: Client prompts user to restart for changes to take effect

## License

LGPL-3.0

## Credits

ByUsi
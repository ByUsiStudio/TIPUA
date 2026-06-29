# TIPUA - The Integration Package Updates Automatically
# 整合包自动更新工具

[English](#english) | [中文](#中文)

---

## English

A NeoForge mod that automatically synchronizes modpacks between server and client using ZIP archives.

### Features

- **Version Management**: Semantic version system (e.g., 1.2.1) for precise version control
- **Direct Download**: Server provides direct download URLs for modpack files
- **Download Progress Display**: Real-time download progress with speed and time remaining
- **Auto-Extract**: Automatically extracts `config` and `mods` directories, other files are replaced by default
- **Auto-Download**: Clients automatically download and extract updates when joining
- **Server Commands**: In-game commands for managing and monitoring TIPUA
- **Configurable**: Fully configurable behavior for both server and client

### Requirements

- Minecraft 1.21.1
- NeoForge 21.1.234+

### ZIP Archive Structure

The ZIP archive must contain the following structure:
```
modpack.zip
├── config/          # Required: Configuration files
│   └── ...
├── mods/            # Required: Mod JAR files
│   └── ...
└── (other files)    # Optional: Will be extracted and replaced
```

### Installation

#### Server Installation

1. Place the TIPUA mod JAR in your server's `mods/` directory
2. Configure `tipua-common.toml` in the `config/` directory:
   - Set `serverVersion` to your modpack version (e.g., "1.0.0")
   - Set `modpackDownloadUrl` to your direct download URL (e.g., "https://example.com/modpack.zip")
   - Set `httpPort` (default: 25566) for version query endpoint
3. Start the server

#### Client Installation

1. Place the TIPUA mod JAR in your client's `mods/` directory
2. Configure `tipua-common.toml` in the `config/` directory:
   - Set `serverAddress` to the server's address
   - Set `httpPort` to match the server's port
3. First startup will not check for updates
4. After entering the main menu, automatic update check begins

### Configuration

#### Server Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `httpPort` | HTTP server port (for version query) | 25566 |
| `serverVersion` | Current modpack version (e.g., 1.2.1) | 1.0.0 |
| `modpackDownloadUrl` | Direct download URL (must be configured) | (empty) |

#### Client Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `serverAddress` | Server HTTP address | localhost |
| `httpPort` | Server HTTP port | 25566 |
| `autoUpdate` | Auto-check for updates | true |
| `autoExtract` | Auto-extract ZIP archives | true |
| `downloadTimeoutSeconds` | Download timeout (seconds) | 300 |
| `showUpdateNotification` | Show notifications | true |
| `modpackDownloadUrl` | Override download URL (empty = get from server) | (empty) |

### Server Commands

Server admins can use the following commands:

| Command | Description |
|---------|-------------|
| `/tipua` | Show TIPUA help |
| `/tipua version` | Show current modpack version |
| `/tipua url` | Show configured download URL |
| `/tipua info` | Show complete server configuration |
| `/tipua reload` | Reload TIPUA config (version and URL take effect immediately) |

### How It Works

1. **Version Query**: Client queries server's `/version` endpoint
2. **URL Query**: Client queries server's `/download-url` endpoint for direct download link
3. **Comparison**: Client compares local version with server version
4. **Download**: If server version is newer, client downloads ZIP from the direct URL
5. **Progress Display**: Shows download progress bar, speed, and time remaining
6. **Extract**: ZIP is extracted, `config` and `mods` are merged, other files replaced
7. **Restart**: User prompted to restart for changes

### License

AGPL-3.0

### Credits

ByUsi Studio

---

## 中文

一个NeoForge模组，通过ZIP压缩包自动同步服务端和客户端的整合包内容。

### 功能特性

- **版本管理**：语义化版本系统（如1.2.1），精确控制版本
- **直链下载**：服务端提供直链下载地址，客户端直接下载
- **下载进度显示**：实时显示下载进度、速度和剩余时间
- **自动解压**：自动解压 `config` 和 `mods` 目录，其他文件默认替换
- **自动下载**：客户端加入时自动下载并解压更新
- **服务端命令**：游戏内命令管理和监控TIPUA
- **完全可配置**：服务端和客户端行为均可配置

### 运行要求

- Minecraft 1.21.1
- NeoForge 21.1.234+

### ZIP压缩包结构

ZIP压缩包必须包含以下结构：
```
modpack.zip
├── config/          # 必须：配置文件目录
│   └── ...
├── mods/            # 必须：模组JAR文件目录
│   └── ...
└── (其他文件)        # 可选：将被解压替换
```

### 安装方法

#### 服务端安装

1. 将TIPUA模组JAR放入服务器的 `mods/` 目录
2. 在 `config/` 目录配置 `tipua-common.toml`：
   - 设置 `serverVersion` 为整合包版本号（如 "1.0.0"）
   - 设置 `modpackDownloadUrl` 为直链下载地址（如 "https://example.com/modpack.zip"）
   - 设置 `httpPort`（默认：25566）用于版本查询端点
3. 启动服务器

#### 客户端安装

1. 将TIPUA模组JAR放入客户端的 `mods/` 目录
2. 在 `config/` 目录配置 `tipua-common.toml`：
   - 设置 `serverAddress` 为服务器地址
   - 设置 `httpPort` 与服务器端口匹配
3. 第一次启动不会检查更新
4. 进入主菜单后自动开始检查更新

### 配置说明

#### 服务端设置

| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `httpPort` | HTTP服务器端口（用于版本查询） | 25566 |
| `serverVersion` | 当前整合包版本号（如1.2.1） | 1.0.0 |
| `modpackDownloadUrl` | 直链下载地址（必须配置） | (空) |

#### 客户端设置

| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `serverAddress` | 服务器HTTP地址 | localhost |
| `httpPort` | 服务器HTTP端口 | 25566 |
| `autoUpdate` | 自动检查更新 | true |
| `autoExtract` | 自动解压ZIP | true |
| `downloadTimeoutSeconds` | 下载超时（秒） | 300 |
| `showUpdateNotification` | 显示通知 | true |
| `modpackDownloadUrl` | 覆盖下载地址（留空则从服务端获取） | (空) |

### 服务端命令

服务端OP可使用以下命令：

| 命令 | 描述 |
|-----|------|
| `/tipua` | 显示TIPUA帮助信息 |
| `/tipua version` | 显示当前整合包版本 |
| `/tipua url` | 显示配置的直链下载地址 |
| `/tipua info` | 显示完整的服务器配置信息 |
| `/tipua reload` | 重载TIPUA配置（版本号和下载地址即时生效） |

### 工作原理

1. **版本查询**：客户端向服务端 `/version` 端点查询版本
2. **地址查询**：客户端向服务端 `/download-url` 端点获取直链
3. **版本对比**：客户端对比本地版本与服务端版本
4. **下载**：如果服务端版本更新，客户端从直链下载ZIP
5. **进度显示**：显示下载进度条、速度和剩余时间
6. **解压**：ZIP被解压，`config` 和 `mods` 合并，其他文件替换
7. **重启**：提示用户重启以使更改生效

### 许可证

AGPL-3.0

### 制作团队

ByUsi Studio

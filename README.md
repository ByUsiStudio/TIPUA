# TIPUA - The Integration Package Updates Automatically
# 整合包自动更新工具

[English](#english) | [中文](#中文)

---

## English

A NeoForge mod that automatically synchronizes modpacks between server and client using ZIP archives.

### Features

- **Server-Side ZIP Monitoring**: Automatically detects changes to the server's ZIP archive
- **HTTP File Server**: Built-in HTTP server for efficient file distribution
- **Version Identifier**: Local version tracking with auto-generated identifier file
- **Auto-Extract**: Automatically extracts `config` and `mods` directories, other files are replaced by default
- **Auto-Download**: Clients automatically download and extract updates when joining
- **Hash Verification**: SHA-256 hash verification for secure downloads
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
2. Create a `modpacks/` directory in your server root
3. Place your ZIP archive in the `modpacks/` directory
4. Configure `tipua-common.toml` in the `config/` directory:
   - Set `zipPath` to your ZIP archive
   - Set `httpPort` (default: 25566)
   - Set `serverAddress` for client connection
5. Start the server

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
| `httpPort` | HTTP server port | 25566 |
| `zipPath` | Path to ZIP archive | modpacks/default.zip |
| `checkIntervalSeconds` | ZIP check interval | 60 |
| `enableFileServer` | Enable HTTP server | true |
| `enableHashVerification` | Enable SHA-256 verification | true |

#### Client Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `serverAddress` | Server HTTP address | localhost |
| `httpPort` | Server HTTP port | 25566 |
| `autoUpdate` | Auto-check for updates | true |
| `autoExtract` | Auto-extract ZIP archives | true |
| `enableHashVerification` | Enable SHA-256 verification | true |
| `downloadTimeoutSeconds` | Download timeout | 300 |
| `showUpdateNotification` | Show notifications | true |

### How It Works

1. **Version Check**: Server generates version identifier from ZIP hash
2. **Client Join**: Client compares local version with server version
3. **Download**: If different, client downloads ZIP via HTTP
4. **Extract**: ZIP is extracted, `config` and `mods` are merged, other files replaced
5. **Restart**: User prompted to restart for changes

### License

AGPL-3.0

### Credits

ByUsi Studio

---

## 中文

一个NeoForge模组，通过ZIP压缩包自动同步服务端和客户端的整合包内容。

### 功能特性

- **服务端ZIP监控**：自动检测服务端ZIP压缩包的变化
- **HTTP文件服务器**：内置HTTP服务器，高效分发文件
- **版本标识系统**：本地版本追踪，自动生成版本标识文件
- **自动解压**：自动解压 `config` 和 `mods` 目录，其他文件默认替换
- **自动下载**：客户端加入时自动下载并解压更新
- **哈希验证**：SHA-256哈希验证，确保下载安全
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
2. 在服务器根目录创建 `modpacks/` 目录
3. 将ZIP压缩包放入 `modpacks/` 目录
4. 在 `config/` 目录配置 `tipua-common.toml`：
   - 设置 `zipPath` 为你的ZIP压缩包路径
   - 设置 `httpPort`（默认：25566）
   - 设置 `serverAddress` 供客户端连接
5. 启动服务器

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
| `httpPort` | HTTP服务器端口 | 25566 |
| `zipPath` | ZIP压缩包路径 | modpacks/default.zip |
| `checkIntervalSeconds` | ZIP检查间隔（秒） | 60 |
| `enableFileServer` | 启用HTTP服务器 | true |
| `enableHashVerification` | 启用SHA-256验证 | true |

#### 客户端设置

| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `serverAddress` | 服务器HTTP地址 | localhost |
| `httpPort` | 服务器HTTP端口 | 25566 |
| `autoUpdate` | 自动检查更新 | true |
| `autoExtract` | 自动解压ZIP | true |
| `enableHashVerification` | 启用SHA-256验证 | true |
| `downloadTimeoutSeconds` | 下载超时（秒） | 300 |
| `showUpdateNotification` | 显示通知 | true |

### 工作原理

1. **版本检查**：服务端从ZIP哈希生成版本标识
2. **客户端加入**：客户端对比本地版本与服务端版本
3. **下载**：如果版本不同，客户端通过HTTP下载ZIP
4. **解压**：ZIP被解压，`config` 和 `mods` 合并，其他文件替换
5. **重启**：提示用户重启以使更改生效

### 许可证

AGPL-3.0

### 制作团队

ByUsi Studio
# TIPUA - Modpack Auto Update Tool
# TIPUA - 整合包自动更新工具

[Modrinth](https://modrinth.com/mod/tipua)

A NeoForge mod that automatically syncs modpack content between server and client via `modrinth.index.json` file.
一个NeoForge模组，通过 `modrinth.index.json` 文件自动同步服务端和客户端的整合包内容。

## Features
## 功能特性

- **Version Management**: Semantic versioning system (e.g., 1.2.1) for precise version control
- **modrinth.index.json Support**: Reads `modrinth.index.json` from config directory to download mods
- **SHA-1/SHA-512 Verification**: Double hash verification for every downloaded file ensuring integrity
- **Smart File Management**:
  - Config file exists but mods folder doesn't → Download according to config
  - Config file doesn't exist but mods directory exists → Auto-delete mods directory
- **data.zip Support**: Server can provide additional `data.zip` file, extracted in replace mode at the end
- **Multi-threaded Download**: Automatic multi-threaded download acceleration with Range request detection and automatic fallback to single thread
- **Smart Network Monitoring**: Real-time network condition monitoring with dynamic thread adjustment (1-8 threads)
- **Auto Retry Mechanism**: Automatic retry on network timeout or connection failure, configurable retry count and delay
- **Detailed Error Handling**: Detailed error cause analysis and solution suggestions
- **Resumable Downloads**: Support for continuing interrupted downloads, saving time and bandwidth
- **Download Progress Display**: Real-time display of download progress, speed, and remaining time
- **Incremental Updates**: Smart analysis of large file changes, only download modified parts, saving bandwidth
- **Mod Optimization**: Automatic detection of duplicate and outdated mods with optimization suggestions
- **Smart Conflict Handling**: Automatic file conflict detection during extraction with multiple resolution strategies
- **Auto Rollback**: Automatic rollback to previous version on update failure, supports one-click manual rollback
- **Log Panel UI**: Real-time operation log display during download with scrollable history
- **Scheduled Updates**: Support for setting scheduled update time, automatic update check at scheduled time
- **Markdown Logs**: Update logs support Markdown rich text format display
- **Auto Download**: Client automatically downloads and extracts updates on join
- **Server Commands**: In-game commands for managing and monitoring TIPUA
- **Fully Configurable**: Both server and client behavior are configurable
- **Update Preview**: Display list of files to be updated before downloading
- **Rollback Feature**: One-click rollback to previous version
- **Modrinth Integration**: Automatically fetch mod information and updates from Modrinth
- **版本管理**：语义化版本系统（如1.2.1），精确控制版本
- **modrinth.index.json 支持**：读取配置目录下的 `modrinth.index.json` 文件来下载模组
- **SHA-1/SHA-512 校验**：下载的每个文件都会进行双重哈希校验，确保文件完整性
- **智能文件管理**：
  - 配置文件存在但 mods 文件夹不存在 → 按照配置文件下载
  - 配置文件不存在但 mods 目录存在 → 自动删除 mods 目录
- **data.zip 支持**：服务端可提供额外的 `data.zip` 文件，在最后进行替换模式解压
- **多线程下载**：自动使用多线程下载加速，支持Range请求检测，自动回退到单线程
- **智能网络监控**：实时监控网络状况，动态调整下载线程数（1-8线程）
- **自动重试机制**：网络超时或连接失败时自动重试，可配置重试次数和延迟
- **详细错误处理**：提供详细的错误原因分析和解决方案建议
- **断点续传**：支持下载中断后继续下载，节省时间和带宽
- **下载进度显示**：实时显示下载进度、速度和剩余时间
- **增量更新**：大文件智能分析变更，只下载修改部分，节省带宽
- **模组优化**：自动检测重复和过时模组，提供优化建议
- **智能冲突处理**：解压时文件冲突自动检测，支持多种解决策略
- **自动回滚**：更新失败时自动回滚到上一版本，支持一键手动回滚
- **日志框界面**：下载过程中实时显示操作日志，支持滚动查看历史记录
- **定时更新**：支持设置定时更新时间，到达时间后自动检查更新
- **Markdown日志**：更新日志支持Markdown富文本格式显示
- **自动下载**：客户端加入时自动下载并解压更新
- **服务端命令**：游戏内命令管理和监控TIPUA
- **完全可配置**：服务端和客户端行为均可配置
- **更新预览**：下载前展示将要更新的文件列表
- **回滚功能**：一键回滚到上一版本
- **Modrinth集成**：自动从Modrinth获取模组信息和更新

## Requirements
## 运行要求

- Minecraft 1.21.1
- NeoForge 21.1.234+
- Minecraft 1.21.1
- NeoForge 21.1.234+

## modrinth.index.json Format
## modrinth.index.json 格式

The server needs to place a `modrinth.index.json` file in the `config/tipua/` directory with the following format:
服务器需要在 `config/tipua/` 目录放置 `modrinth.index.json` 文件，格式如下：

```json
{
  "game": "minecraft",
  "formatVersion": 1,
  "versionId": "1.0.0",
  "name": "TIPUA Modpack",
  "files": [
    {
      "path": "mods/example-mod.jar",
      "hashes": {
        "sha1": "abc123...",
        "sha512": "def456..."
      },
      "downloads": [
        "https://cdn.modrinth.com/data/xxx/versions/xxx/example-mod.jar"
      ],
      "fileSize": 1234567
    }
  ],
  "dependencies": {
    "minecraft": "1.21.1",
    "neoforge": "21.1.234"
  }
}
```

## Installation
## 安装方法

### Server Installation
### 服务端安装

1. Place the TIPUA mod JAR in the server's `mods/` directory
2. Place the following files in the `config/tipua/` directory:
   - `modrinth.index.json` - Modpack index file (required)
   - `data.zip` (optional) - Additional data zip file, extracted to game directory at the end
3. A `tipua-server.toml` config file will be automatically generated in the `config/` directory:
   - Set `serverVersion` to the modpack version (e.g., "1.0.0")
   - Set `httpPort` (default: 25566) for the version query endpoint
4. Start the server
1. 将TIPUA模组JAR放入服务器的 `mods/` 目录
2. 在 `config/tipua/` 目录放置以下文件：
   - `modrinth.index.json` - 整合包索引文件（必须）
   - `data.zip`（可选）- 额外数据压缩包，最后解压到游戏目录
3. 在 `config/` 目录会自动生成 `tipua-server.toml` 配置文件：
   - 设置 `serverVersion` 为整合包版本号（如 "1.0.0"）
   - 设置 `httpPort`（默认：25566）用于版本查询端点
4. 启动服务器

### Client Installation
### 客户端安装

1. Place the TIPUA mod JAR in the client's `mods/` directory
2. A `tipua-client.toml` config file will be automatically generated in the `config/` directory:
   - Set `serverAddress` to the server address
   - Set `httpPort` to match the server port
3. Update check will not run on first launch
4. Auto-check for updates after entering the main menu
1. 将TIPUA模组JAR放入客户端的 `mods/` 目录
2. 在 `config/` 目录会自动生成 `tipua-client.toml` 配置文件：
   - 设置 `serverAddress` 为服务器地址
   - 设置 `httpPort` 与服务器端口匹配
3. 第一次启动不会检查更新
4. 进入主菜单后自动开始检查更新

## Configuration
## 配置说明

### Server Settings (`tipua-server.toml`)
### 服务端设置 (`tipua-server.toml`)

| Setting | Description | Default |
|---------|-------------|---------|
| `httpPort` | HTTP server port (for version query and file distribution) | 25566 |
| `serverVersion` | Current modpack version (e.g., 1.2.1) | 1.0.0 |
| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `httpPort` | HTTP服务器端口（用于版本查询和文件分发） | 25566 |
| `serverVersion` | 当前整合包版本号（如1.2.1） | 1.0.0 |

### Client Settings (`tipua-client.toml`)
### 客户端设置 (`tipua-client.toml`)

| Setting | Description | Default |
|---------|-------------|---------|
| `serverAddress` | Server HTTP address | localhost |
| `httpPort` | Server HTTP port | 25566 |
| `autoUpdate` | Auto-check for updates | true |
| `autoExtract` | Auto-extract files | true |
| `downloadTimeoutSeconds` | Download timeout (seconds) | 300 |
| `showUpdateNotification` | Show notifications | true |
| `maxRetryAttempts` | Maximum retry attempts on download failure | 3 |
| `retryDelaySeconds` | Delay between retries (seconds) | 5 |
| `autoRollback` | Auto-rollback to previous version on update failure | true |
| `scheduledUpdateTime` | Scheduled update time (HH:mm format, e.g., 14:30), leave empty to disable | (empty) |
| `autoUpdateOnSchedule` | Auto-update at scheduled time | true |
| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `serverAddress` | 服务器HTTP地址 | localhost |
| `httpPort` | 服务器HTTP端口 | 25566 |
| `autoUpdate` | 自动检查更新 | true |
| `autoExtract` | 自动解压文件 | true |
| `downloadTimeoutSeconds` | 下载超时（秒） | 300 |
| `showUpdateNotification` | 显示通知 | true |
| `maxRetryAttempts` | 下载失败时的最大重试次数 | 3 |
| `retryDelaySeconds` | 重试之间的延迟时间（秒） | 5 |
| `autoRollback` | 更新失败时自动回滚到之前版本 | true |
| `scheduledUpdateTime` | 定时更新时间（HH:mm格式，如 14:30），留空则禁用定时更新 | (空) |
| `autoUpdateOnSchedule` | 是否在定时更新时间自动更新 | true |

## Server Commands
## 服务端命令

Server OPs can use the following commands:
服务端OP可使用以下命令：

| Command | Description |
|---------|-------------|
| `/tipua` | Show TIPUA help information |
| `/tipua version` | Show current modpack version |
| `/tipua info` | Show complete server configuration |
| `/tipua reload` | Reload TIPUA configuration (version takes effect immediately) |
| `/tipua modinfo` | Show mod build information and version |
| 命令 | 描述 |
|-----|------|
| `/tipua` | 显示TIPUA帮助信息 |
| `/tipua version` | 显示当前整合包版本 |
| `/tipua info` | 显示完整的服务器配置信息 |
| `/tipua reload` | 重载TIPUA配置（版本号即时生效） |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## Client Commands
## 客户端命令

Clients can use the following commands:
客户端可使用以下命令：

| Command | Description |
|---------|-------------|
| `/tipua rollback` | One-click rollback to previous version |
| `/tipua modinfo` | Show mod build information and version |
| 命令 | 描述 |
|-----|------|
| `/tipua rollback` | 一键回滚到上一版本 |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## How It Works
## 工作原理

1. **Version Query**: Client queries version from server `/version` endpoint
2. **Index File Retrieval**: Client fetches index file from server `/modrinth.index.json`
3. **Version Comparison**: Client compares local version with server version
4. **File Status Check**:
   - If config file exists but mods folder doesn't → Download according to config
   - If config file doesn't exist but mods directory exists → Auto-delete mods directory
5. **Download**: If server version is newer, client downloads each file according to index
   - Multiple download URLs per file, auto-try next on failure
   - SHA-1 and SHA-512 verification after download
   - Smart network monitoring: dynamic thread adjustment based on network conditions
   - Auto retry: automatic retry on network timeout or connection failure (configurable count and delay)
6. **Progress Display**: Show download progress bar, speed, remaining time, and current file name
7. **data.zip Processing**: After all files downloaded, attempt to download `data.zip` from server
   - Extract to game directory in replace mode
8. **Version Update**: After all downloads and extractions complete, update client version identifier
9. **Error Recovery**:
   - Auto rollback: automatic rollback to previous version on update failure (configurable)
   - Manual rollback: manual rollback via `/tipua rollback` command
10. **Restart**: Prompt user to click "Quit Game" button to restart for changes to take effect
1. **版本查询**：客户端向服务端 `/version` 端点查询版本
2. **索引文件获取**：客户端向服务端 `/modrinth.index.json` 获取索引文件
3. **版本对比**：客户端对比本地版本与服务端版本
4. **文件状态检查**：
   - 如果配置文件存在但 mods 文件夹不存在 → 按照配置文件下载
   - 如果配置文件不存在但 mods 目录存在 → 自动删除 mods 目录
5. **下载**：如果服务端版本更新，客户端根据索引文件下载每个文件
   - 每个文件支持多个下载地址，自动尝试下一个
   - 下载完成后进行 SHA-1 和 SHA-512 校验
   - 智能网络监控：根据网络状况动态调整线程数
   - 自动重试：网络超时或连接失败时自动重试（可配置次数和延迟）
6. **进度显示**：显示下载进度条、速度和剩余时间，以及当前下载的文件名
7. **data.zip 处理**：所有文件下载完成后，尝试从服务器下载 `data.zip`
   - 使用替换模式解压到游戏目录
8. **版本更新**：所有下载和解压完成后，更新客户端版本标识
9. **错误恢复**：
   - 自动回滚：更新失败时自动回滚到上一版本（可配置）
   - 手动回滚：通过 `/tipua rollback` 命令手动回滚
10. **重启**：提示用户点击"退出游戏"按钮重启以使更改生效

## Config File Migration
## 配置文件迁移

When upgrading from an older version, the old config file `tipua-common.toml` will be automatically migrated:
- Server config migrated to `tipua-server.toml`
- Client config migrated to `tipua-client.toml`
- Old config file backed up as `tipua-common.toml.bak`
如果从旧版本升级，旧配置文件 `tipua-common.toml` 会自动迁移：
- 服务端配置迁移到 `tipua-server.toml`
- 客户端配置迁移到 `tipua-client.toml`
- 旧配置文件备份为 `tipua-common.toml.bak`

## Modrinth Integration
## Modrinth集成

TIPUA is deeply integrated with Modrinth:
- [Modrinth Page](https://modrinth.com/mod/tipua)
- Auto-check for TIPUA mod updates
- Display Modrinth changelogs in update preview UI
TIPUA与Modrinth深度集成：
- [Modrinth页面](https://modrinth.com/mod/tipua)
- 自动检查TIPUA模组自身更新
- 可在更新预览界面显示Modrinth更新日志

## License
## 许可证

[AGPL-3.0](LICENSE)

## Team
## 制作团队

[ByUsi Studio](https://about.cdifit.cn)
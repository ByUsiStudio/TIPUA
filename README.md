# TIPUA - 整合包自动更新工具

[Modrinth](https://modrinth.com/mod/tipua)

一个NeoForge模组，通过 `modrinth.index.json` 文件自动同步服务端和客户端的整合包内容。

## 功能特性

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

## 运行要求

- Minecraft 1.21.1
- NeoForge 21.1.234+

## modrinth.index.json 格式

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

## 安装方法

### 服务端安装

1. 将TIPUA模组JAR放入服务器的 `mods/` 目录
2. 在 `config/tipua/` 目录放置以下文件：
   - `modrinth.index.json` - 整合包索引文件（必须）
   - `data.zip`（可选）- 额外数据压缩包，最后解压到游戏目录
3. 在 `config/` 目录会自动生成 `tipua-server.toml` 配置文件：
   - 设置 `serverVersion` 为整合包版本号（如 "1.0.0"）
   - 设置 `httpPort`（默认：25566）用于版本查询端点
4. 启动服务器

### 客户端安装

1. 将TIPUA模组JAR放入客户端的 `mods/` 目录
2. 在 `config/` 目录会自动生成 `tipua-client.toml` 配置文件：
   - 设置 `serverAddress` 为服务器地址
   - 设置 `httpPort` 与服务器端口匹配
3. 第一次启动不会检查更新
4. 进入主菜单后自动开始检查更新

## 配置说明

### 服务端设置 (`tipua-server.toml`)

| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `httpPort` | HTTP服务器端口（用于版本查询和文件分发） | 25566 |
| `serverVersion` | 当前整合包版本号（如1.2.1） | 1.0.0 |

### 客户端设置 (`tipua-client.toml`)

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

## 服务端命令

服务端OP可使用以下命令：

| 命令 | 描述 |
|-----|------|
| `/tipua` | 显示TIPUA帮助信息 |
| `/tipua version` | 显示当前整合包版本 |
| `/tipua info` | 显示完整的服务器配置信息 |
| `/tipua reload` | 重载TIPUA配置（版本号即时生效） |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## 客户端命令

客户端可使用以下命令：

| 命令 | 描述 |
|-----|------|
| `/tipua rollback` | 一键回滚到上一版本 |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## 工作原理

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

## 配置文件迁移

如果从旧版本升级，旧配置文件 `tipua-common.toml` 会自动迁移：
- 服务端配置迁移到 `tipua-server.toml`
- 客户端配置迁移到 `tipua-client.toml`
- 旧配置文件备份为 `tipua-common.toml.bak`

## Modrinth集成

TIPUA与Modrinth深度集成：
- [Modrinth页面](https://modrinth.com/mod/tipua)
- 自动检查TIPUA模组自身更新
- 可在更新预览界面显示Modrinth更新日志

## 许可证

[AGPL-3.0](LICENSE)

## 制作团队

[ByUsi Studio](https://about.cdifit.cn)
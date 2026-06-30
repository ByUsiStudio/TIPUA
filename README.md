# TIPUA - 整合包自动更新工具

[Modrinth](https://modrinth.com/mod/tipua)

一个NeoForge模组，通过ZIP压缩包自动同步服务端和客户端的整合包内容。

## 功能特性

- **版本管理**：语义化版本系统（如1.2.1），精确控制版本
- **直链下载**：服务端提供直链下载地址，客户端直接下载
- **断点续传**：支持下载中断后继续下载，节省时间和带宽
- **下载进度显示**：实时显示下载进度、速度和剩余时间
- **解压进度显示**：显示当前解压的文件和总体进度
- **自动解压**：自动解压 `config` 和 `mods` 目录，其他文件默认替换
- **自动下载**：客户端加入时自动下载并解压更新
- **服务端命令**：游戏内命令管理和监控TIPUA
- **完全可配置**：服务端和客户端行为均可配置
- **更新预览**：下载前展示将要更新的文件列表
- **回滚功能**：一键回滚到上一版本
- **Modrinth集成**：自动从Modrinth获取模组信息和更新

## 运行要求

- Minecraft 1.21.1
- NeoForge 21.1.234+

## ZIP压缩包结构

ZIP压缩包必须包含以下结构：
```
modpack.zip
├── config/          # 必须：配置文件目录
│   └── ...
├── mods/            # 必须：模组JAR文件目录
│   └── ...
└── (其他文件)        # 可选：将被解压替换
```

## 安装方法

### 服务端安装

1. 将TIPUA模组JAR放入服务器的 `mods/` 目录
2. 在 `config/` 目录会自动生成 `tipua-server.toml` 配置文件：
   - 设置 `serverVersion` 为整合包版本号（如 "1.0.0"）
   - 设置 `modpackDownloadUrl` 为直链下载地址（如 "https://example.com/modpack.zip"）
   - 设置 `httpPort`（默认：25566）用于版本查询端点
3. 启动服务器

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
| `httpPort` | HTTP服务器端口（用于版本查询） | 25566 |
| `serverVersion` | 当前整合包版本号（如1.2.1） | 1.0.0 |
| `modpackDownloadUrl` | 直链下载地址（必须配置） | (空) |

### 客户端设置 (`tipua-client.toml`)

| 设置项 | 描述 | 默认值 |
|-------|------|--------|
| `serverAddress` | 服务器HTTP地址 | localhost |
| `httpPort` | 服务器HTTP端口 | 25566 |
| `autoUpdate` | 自动检查更新 | true |
| `autoExtract` | 自动解压ZIP | true |
| `downloadTimeoutSeconds` | 下载超时（秒） | 300 |
| `showUpdateNotification` | 显示通知 | true |
| `modpackDownloadUrl` | 覆盖下载地址（留空则从服务端获取） | (空) |

## 服务端命令

服务端OP可使用以下命令：

| 命令 | 描述 |
|-----|------|
| `/tipua` | 显示TIPUA帮助信息 |
| `/tipua version` | 显示当前整合包版本 |
| `/tipua url` | 显示配置的直链下载地址 |
| `/tipua info` | 显示完整的服务器配置信息 |
| `/tipua reload` | 重载TIPUA配置（版本号和下载地址即时生效） |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## 客户端命令

客户端可使用以下命令：

| 命令 | 描述 |
|-----|------|
| `/tipua rollback` | 一键回滚到上一版本 |
| `/tipua modinfo` | 显示模组编译信息和版本 |

## 工作原理

1. **版本查询**：客户端向服务端 `/version` 端点查询版本
2. **地址查询**：客户端向服务端 `/download-url` 端点获取直链
3. **版本对比**：客户端对比本地版本与服务端版本
4. **更新预览**：显示将要更新的文件列表（支持分类查看）
5. **下载**：如果服务端版本更新，客户端从直链下载ZIP（支持断点续传）
6. **进度显示**：显示下载进度条、速度和剩余时间
7. **解压**：ZIP被解压，显示解压进度和当前文件，`config` 和 `mods` 合并，其他文件替换
8. **回滚**：如需回滚，执行 `/tipua rollback` 命令恢复上一版本
9. **重启**：提示用户点击"退出游戏"按钮重启以使更改生效

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

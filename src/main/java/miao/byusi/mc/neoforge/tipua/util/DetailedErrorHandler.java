package miao.byusi.mc.neoforge.tipua.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 详细错误信息处理器
 * 提供错误原因分析和解决方案建议
 */
public class DetailedErrorHandler {
    
    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        NETWORK_TIMEOUT("网络超时", "Network Timeout"),
        NETWORK_UNREACHABLE("网络不可达", "Network Unreachable"),
        DNS_RESOLUTION_FAILED("DNS解析失败", "DNS Resolution Failed"),
        CONNECTION_REFUSED("连接被拒绝", "Connection Refused"),
        SSL_ERROR("SSL证书错误", "SSL Certificate Error"),
        INVALID_URL("无效的下载地址", "Invalid Download URL"),
        SERVER_ERROR("服务器错误", "Server Error"),
        NOT_FOUND("文件不存在", "File Not Found"),
        PERMISSION_DENIED("权限不足", "Permission Denied"),
        DISK_FULL("磁盘空间不足", "Disk Full"),
        CORRUPTED_DOWNLOAD("下载文件损坏", "Corrupted Download"),
        UNKNOWN_ERROR("未知错误", "Unknown Error"),
        ZIP_STRUCTURE_INVALID("ZIP结构无效", "Invalid ZIP Structure"),
        EXTRACTION_FAILED("解压失败", "Extraction Failed"),
        FILE_CONFLICT("文件冲突", "File Conflict"),
        CONFIG_ERROR("配置错误", "Configuration Error");
        
        private final String chineseName;
        private final String englishName;
        
        ErrorType(String chineseName, String englishName) {
            this.chineseName = chineseName;
            this.englishName = englishName;
        }
        
        public String getChineseName() {
            return chineseName;
        }
        
        public String getEnglishName() {
            return englishName;
        }
    }
    
    /**
     * 错误详细信息类
     */
    public static class ErrorDetail {
        private final ErrorType errorType;
        private final String errorMessage;
        private final String cause;
        private final String solution;
        private final boolean canRetry;
        private final boolean canRollback;
        
        public ErrorDetail(ErrorType errorType, String errorMessage, String cause, String solution, boolean canRetry, boolean canRollback) {
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.cause = cause;
            this.solution = solution;
            this.canRetry = canRetry;
            this.canRollback = canRollback;
        }
        
        public ErrorType getErrorType() {
            return errorType;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public String getCause() {
            return cause;
        }
        
        public String getSolution() {
            return solution;
        }
        
        public boolean canRetry() {
            return canRetry;
        }
        
        public boolean canRollback() {
            return canRollback;
        }
        
        /**
         * 获取格式化的错误信息（用于显示）
         */
        public String getFormattedError() {
            StringBuilder sb = new StringBuilder();
            sb.append("错误类型 / Error Type: ").append(errorType.getChineseName()).append(" (").append(errorType.getEnglishName()).append(")\n");
            sb.append("错误信息 / Error Message: ").append(errorMessage).append("\n");
            sb.append("可能原因 / Possible Cause: ").append(cause).append("\n");
            sb.append("解决方案 / Solution: ").append(solution).append("\n");
            if (canRetry) {
                sb.append("✓ 可以重试 / Can retry\n");
            }
            if (canRollback) {
                sb.append("✓ 可以回滚 / Can rollback\n");
            }
            return sb.toString();
        }
    }
    
    private static final Map<String, ErrorDetail> ERROR_SOLUTIONS = new HashMap<>();
    
    static {
        // 初始化错误解决方案数据库
        initErrorSolutions();
    }
    
    /**
     * 初始化错误解决方案数据库
     */
    private static void initErrorSolutions() {
        // 网络超时
        ERROR_SOLUTIONS.put("java.net.SocketTimeoutException", 
            new ErrorDetail(
                ErrorType.NETWORK_TIMEOUT,
                "连接超时，服务器响应时间过长",
                "网络连接缓慢或服务器负载过高",
                "1. 检查网络连接\n2. 尝试重新下载\n3. 联系服务器管理员检查服务器状态\n4. 检查防火墙设置",
                true, false
            )
        );
        
        // 网络不可达
        ERROR_SOLUTIONS.put("java.net.UnknownHostException",
            new ErrorDetail(
                ErrorType.DNS_RESOLUTION_FAILED,
                "无法解析服务器地址",
                "DNS服务器配置错误或域名不存在",
                "1. 检查服务器地址是否正确\n2. 尝试使用IP地址替代域名\n3. 检查DNS设置\n4. 尝试使用公共DNS (8.8.8.8)",
                true, false
            )
        );
        
        // 连接被拒绝
        ERROR_SOLUTIONS.put("java.net.ConnectException",
            new ErrorDetail(
                ErrorType.CONNECTION_REFUSED,
                "连接被服务器拒绝",
                "服务器未启动或端口配置错误",
                "1. 检查服务器是否运行中\n2. 确认服务器端口配置正确\n3. 检查防火墙设置\n4. 联系服务器管理员",
                true, false
            )
        );
        
        // SSL错误
        ERROR_SOLUTIONS.put("javax.net.ssl.SSLException",
            new ErrorDetail(
                ErrorType.SSL_ERROR,
                "SSL证书验证失败",
                "服务器SSL证书过期或配置错误",
                "1. 检查服务器时间设置\n2. 尝试使用HTTP而非HTTPS\n3. 联系服务器管理员更新证书",
                true, false
            )
        );
        
        // 文件不存在
        ERROR_SOLUTIONS.put("404",
            new ErrorDetail(
                ErrorType.NOT_FOUND,
                "下载的文件不存在",
                "整合包文件已被删除或移动",
                "1. 联系服务器管理员确认文件存在\n2. 检查下载地址是否正确\n3. 等待服务器管理员重新上传文件",
                true, false
            )
        );
        
        // 权限不足
        ERROR_SOLUTIONS.put("java.nio.file.AccessDeniedException",
            new ErrorDetail(
                ErrorType.PERMISSION_DENIED,
                "文件访问权限不足",
                "游戏目录没有写入权限或文件被占用",
                "1. 以管理员身份运行游戏\n2. 检查游戏目录的读写权限\n3. 关闭可能占用文件的其他程序\n4. 检查杀毒软件是否阻止访问",
                true, false
            )
        );
        
        // 磁盘空间不足
        ERROR_SOLUTIONS.put("java.nio.file.NoSuchFileException",
            new ErrorDetail(
                ErrorType.DISK_FULL,
                "磁盘空间不足",
                "硬盘没有足够的空间存储下载的文件",
                "1. 清理磁盘空间\n2. 选择其他安装目录\n3. 确保至少有2GB可用空间",
                true, false
            )
        );
        
        // 服务器错误
        ERROR_SOLUTIONS.put("500",
            new ErrorDetail(
                ErrorType.SERVER_ERROR,
                "服务器内部错误",
                "服务器端发生错误",
                "1. 稍后重试\n2. 联系服务器管理员\n3. 检查服务器日志",
                true, false
            )
        );
        
        // ZIP结构无效
        ERROR_SOLUTIONS.put("zip_structure_invalid",
            new ErrorDetail(
                ErrorType.ZIP_STRUCTURE_INVALID,
                "ZIP文件结构无效",
                "整合包ZIP文件缺少必需的config或mods目录",
                "1. 联系服务器管理员重新制作整合包\n2. 确保ZIP包含config和mods目录\n3. 验证ZIP文件完整性",
                true, true
            )
        );
        
        // 解压失败
        ERROR_SOLUTIONS.put("extraction_failed",
            new ErrorDetail(
                ErrorType.EXTRACTION_FAILED,
                "文件解压失败",
                "ZIP文件损坏或磁盘空间不足",
                "1. 重新下载整合包\n2. 清理磁盘空间\n3. 检查文件完整性\n4. 使用 /tipua rollback 回滚到之前版本",
                true, true
            )
        );
        
        // 文件冲突
        ERROR_SOLUTIONS.put("file_conflict",
            new ErrorDetail(
                ErrorType.FILE_CONFLICT,
                "文件冲突",
                "解压过程中遇到文件冲突",
                "1. 使用 /tipua rollback 回滚到之前版本\n2. 清理冲突文件后重试\n3. 联系服务器管理员",
                false, true
            )
        );
    }
    
    /**
     * 根据异常获取详细的错误信息
     */
    public static ErrorDetail getErrorDetail(Throwable throwable) {
        if (throwable == null) {
            return new ErrorDetail(
                ErrorType.UNKNOWN_ERROR,
                "未知错误",
                "错误信息不可用",
                "1. 查看日志文件获取详细信息\n2. 联系技术支持",
                true, false
            );
        }
        
        String exceptionMessage = throwable.getMessage();
        if (exceptionMessage == null) {
            exceptionMessage = throwable.getClass().getName();
        }
        
        // 检查HTTP状态码
        if (exceptionMessage.contains("HTTP 404") || exceptionMessage.contains("404")) {
            return ERROR_SOLUTIONS.get("404");
        }
        
        if (exceptionMessage.contains("HTTP 500") || exceptionMessage.contains("500")) {
            return ERROR_SOLUTIONS.get("500");
        }
        
        // 检查特定异常类型
        if (throwable instanceof java.net.SocketTimeoutException) {
            return ERROR_SOLUTIONS.get("java.net.SocketTimeoutException");
        }
        
        if (throwable instanceof java.net.UnknownHostException) {
            return ERROR_SOLUTIONS.get("java.net.UnknownHostException");
        }
        
        if (throwable instanceof java.net.ConnectException) {
            return ERROR_SOLUTIONS.get("java.net.ConnectException");
        }
        
        if (throwable instanceof javax.net.ssl.SSLException) {
            return ERROR_SOLUTIONS.get("javax.net.ssl.SSLException");
        }
        
        if (throwable instanceof java.nio.file.AccessDeniedException) {
            return ERROR_SOLUTIONS.get("java.nio.file.AccessDeniedException");
        }
        
        // 检查错误消息中的关键字
        if (exceptionMessage.contains("超时") || exceptionMessage.contains("timeout")) {
            return ERROR_SOLUTIONS.get("java.net.SocketTimeoutException");
        }
        
        if (exceptionMessage.contains("拒绝") || exceptionMessage.contains("refused")) {
            return ERROR_SOLUTIONS.get("java.net.ConnectException");
        }
        
        if (exceptionMessage.contains("权限") || exceptionMessage.contains("permission") || exceptionMessage.contains("access")) {
            return ERROR_SOLUTIONS.get("java.nio.file.AccessDeniedException");
        }
        
        if (exceptionMessage.contains("空间") || exceptionMessage.contains("space") || exceptionMessage.contains("disk")) {
            return ERROR_SOLUTIONS.get("java.nio.file.NoSuchFileException");
        }
        
        // 默认未知错误
        return new ErrorDetail(
            ErrorType.UNKNOWN_ERROR,
            exceptionMessage,
            "无法确定具体原因",
            "1. 查看完整错误日志\n2. 联系技术支持\n3. 尝试回滚到之前版本",
            true, false
        );
    }
    
    /**
     * 根据错误类型ID获取错误详情
     */
    public static ErrorDetail getErrorDetailById(String errorId) {
        return ERROR_SOLUTIONS.getOrDefault(errorId, 
            new ErrorDetail(
                ErrorType.UNKNOWN_ERROR,
                errorId,
                "无法确定具体原因",
                "1. 查看完整错误日志\n2. 联系技术支持",
                true, false
            )
        );
    }
    
    /**
     * 分析ZIP结构错误
     */
    public static ErrorDetail analyzeZipError(boolean hasConfig, boolean hasMods) {
        if (!hasConfig && !hasMods) {
            return new ErrorDetail(
                ErrorType.ZIP_STRUCTURE_INVALID,
                "ZIP文件缺少必需目录",
                "整合包ZIP必须包含config和mods目录",
                "1. 联系服务器管理员重新制作整合包\n2. 确保ZIP包含config和mods目录\n3. 验证ZIP文件结构",
                true, true
            );
        }
        
        if (!hasConfig) {
            return new ErrorDetail(
                ErrorType.ZIP_STRUCTURE_INVALID,
                "ZIP文件缺少config目录",
                "整合包ZIP必须包含config目录",
                "1. 联系服务器管理员重新制作整合包\n2. 在ZIP中添加config目录\n3. 验证ZIP文件结构",
                true, true
            );
        }
        
        if (!hasMods) {
            return new ErrorDetail(
                ErrorType.ZIP_STRUCTURE_INVALID,
                "ZIP文件缺少mods目录",
                "整合包ZIP必须包含mods目录",
                "1. 联系服务器管理员重新制作整合包\n2. 在ZIP中添加mods目录\n3. 验证ZIP文件结构",
                true, true
            );
        }
        
        return null;
    }
}
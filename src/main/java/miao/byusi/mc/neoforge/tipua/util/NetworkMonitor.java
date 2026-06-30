package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能网络监控器
 * 实时监控网络状况，提供自适应下载策略
 */
public class NetworkMonitor {
    
    private static final int SAMPLE_INTERVAL_MS = 1000; // 采样间隔（毫秒）
    private static final int HISTORY_SIZE = 10; // 历史记录大小
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 8;
    private static final double MIN_SPEED_THRESHOLD = 10.0; // KB/s 最小速度阈值
    private static final double STABILITY_THRESHOLD = 0.8; // 网络稳定性阈值
    
    /**
     * 网络状况等级
     */
    public enum NetworkCondition {
        EXCELLENT("优秀", "Excellent", 8, 10000),
        GOOD("良好", "Good", 6, 5000),
        FAIR("一般", "Fair", 4, 2000),
        POOR("较差", "Poor", 2, 500),
        VERY_POOR("很差", "Very Poor", 1, 0);
        
        private final String chineseName;
        private final String englishName;
        private final int recommendedThreads;
        private final double speedThreshold; // KB/s
        
        NetworkCondition(String chineseName, String englishName, int recommendedThreads, double speedThreshold) {
            this.chineseName = chineseName;
            this.englishName = englishName;
            this.recommendedThreads = recommendedThreads;
            this.speedThreshold = speedThreshold;
        }
        
        public String getChineseName() {
            return chineseName;
        }
        
        public String getEnglishName() {
            return englishName;
        }
        
        public int getRecommendedThreads() {
            return recommendedThreads;
        }
        
        public double getSpeedThreshold() {
            return speedThreshold;
        }
    }
    
    /**
     * 网络状态数据
     */
    public static class NetworkState {
        private final double currentSpeed; // KB/s
        private final double averageSpeed; // KB/s
        private final double stability; // 0.0 - 1.0
        private final NetworkCondition condition;
        private final int recommendedThreads;
        private final long totalBytesDownloaded;
        private final long totalTimeElapsed; // milliseconds
        private final double packetLossRate;
        
        public NetworkState(double currentSpeed, double averageSpeed, double stability, 
                          NetworkCondition condition, int recommendedThreads, 
                          long totalBytesDownloaded, long totalTimeElapsed, double packetLossRate) {
            this.currentSpeed = currentSpeed;
            this.averageSpeed = averageSpeed;
            this.stability = stability;
            this.condition = condition;
            this.recommendedThreads = recommendedThreads;
            this.totalBytesDownloaded = totalBytesDownloaded;
            this.totalTimeElapsed = totalTimeElapsed;
            this.packetLossRate = packetLossRate;
        }
        
        public double getCurrentSpeed() {
            return currentSpeed;
        }
        
        public double getAverageSpeed() {
            return averageSpeed;
        }
        
        public double getStability() {
            return stability;
        }
        
        public NetworkCondition getCondition() {
            return condition;
        }
        
        public int getRecommendedThreads() {
            return recommendedThreads;
        }
        
        public long getTotalBytesDownloaded() {
            return totalBytesDownloaded;
        }
        
        public long getTotalTimeElapsed() {
            return totalTimeElapsed;
        }
        
        public double getPacketLossRate() {
            return packetLossRate;
        }
        
        public String getFormattedCurrentSpeed() {
            return formatSpeed(currentSpeed);
        }
        
        public String getFormattedAverageSpeed() {
            return formatSpeed(averageSpeed);
        }
        
        private String formatSpeed(double speed) {
            if (speed < 1024) {
                return String.format("%.1f KB/s", speed);
            } else {
                return String.format("%.1f MB/s", speed / 1024);
            }
        }
    }
    
    private final AtomicLong totalBytesDownloaded = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastSampleTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastBytesSample = new AtomicLong(0);
    
    private final ConcurrentLinkedQueue<Double> speedHistory = new ConcurrentLinkedQueue<>();
    private final AtomicReference<NetworkCondition> currentCondition = new AtomicReference<>(NetworkCondition.GOOD);
    private final AtomicReference<NetworkState> latestState = new AtomicReference<>();
    
    private final ScheduledExecutorService scheduler;
    private volatile boolean isMonitoring = false;
    
    // 网络质量监控
    private int successfulSamples = 0;
    private int failedSamples = 0;
    private volatile long lastSpeedUpdate = 0;
    
    public NetworkMonitor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TIPUA-NetworkMonitor");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * 开始监控
     */
    public void startMonitoring() {
        if (isMonitoring) {
            return;
        }
        
        isMonitoring = true;
        totalBytesDownloaded.set(0);
        startTime.set(System.currentTimeMillis());
        lastSampleTime.set(System.currentTimeMillis());
        lastBytesSample.set(0);
        speedHistory.clear();
        successfulSamples = 0;
        failedSamples = 0;
        
        scheduler.scheduleAtFixedRate(this::collectSample, 0, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        TIPUAMod.LOGGER.info("网络监控已启动 / Network monitoring started");
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        TIPUAMod.LOGGER.info("网络监控已停止 / Network monitoring stopped");
    }
    
    /**
     * 记录下载的字节数
     */
    public void recordBytesDownloaded(long bytes) {
        if (isMonitoring) {
            totalBytesDownloaded.addAndGet(bytes);
            successfulSamples++;
        }
    }
    
    /**
     * 记录下载失败
     */
    public void recordDownloadFailure() {
        if (isMonitoring) {
            failedSamples++;
        }
    }
    
    /**
     * 收集网络状态样本
     */
    private void collectSample() {
        if (!isMonitoring) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long currentBytes = totalBytesDownloaded.get();
        
        // 计算当前速度
        long timeDiff = currentTime - lastSampleTime.get();
        long bytesDiff = currentBytes - lastBytesSample.get();
        
        double currentSpeed = 0;
        if (timeDiff > 0) {
            currentSpeed = (bytesDiff / 1024.0) / (timeDiff / 1000.0); // KB/s
        }
        
        // 更新采样点
        lastSampleTime.set(currentTime);
        lastBytesSample.set(currentBytes);
        
        // 添加到历史记录
        speedHistory.add(currentSpeed);
        while (speedHistory.size() > HISTORY_SIZE) {
            speedHistory.poll();
        }
        
        // 分析网络状况
        analyzeNetworkCondition(currentSpeed);
        
        // 更新状态
        updateNetworkState();
        
        lastSpeedUpdate = currentTime;
    }
    
    /**
     * 分析网络状况
     */
    private void analyzeNetworkCondition(double currentSpeed) {
        // 计算平均速度
        double averageSpeed = speedHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
        
        // 计算稳定性（速度方差）
        double variance = 0;
        if (speedHistory.size() > 1) {
            double mean = averageSpeed;
            variance = speedHistory.stream()
                .mapToDouble(speed -> Math.pow(speed - mean, 2))
                .average()
                .orElse(0);
        }
        
        double stability = 1.0 - Math.min(variance / (averageSpeed * averageSpeed + 1), 1.0);
        
        // 确定网络状况等级
        NetworkCondition newCondition;
        
        if (currentSpeed >= NetworkCondition.EXCELLENT.speedThreshold && stability > STABILITY_THRESHOLD) {
            newCondition = NetworkCondition.EXCELLENT;
        } else if (currentSpeed >= NetworkCondition.GOOD.speedThreshold && stability > STABILITY_THRESHOLD * 0.8) {
            newCondition = NetworkCondition.GOOD;
        } else if (currentSpeed >= NetworkCondition.FAIR.speedThreshold && stability > STABILITY_THRESHOLD * 0.6) {
            newCondition = NetworkCondition.FAIR;
        } else if (currentSpeed >= NetworkCondition.POOR.speedThreshold || stability > STABILITY_THRESHOLD * 0.4) {
            newCondition = NetworkCondition.POOR;
        } else {
            newCondition = NetworkCondition.VERY_POOR;
        }
        
        currentCondition.set(newCondition);
        
        TIPUAMod.LOGGER.debug("网络状况: {} / Network condition: {} - 速度: {:.1f} KB/s, 稳定性: {:.2f}", 
            newCondition.getChineseName(), newCondition.getEnglishName(), currentSpeed, stability);
    }
    
    /**
     * 更新网络状态
     */
    private void updateNetworkState() {
        double currentSpeed = speedHistory.isEmpty() ? 0 : speedHistory.peek();
        double averageSpeed = speedHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
        
        // 计算稳定性
        double stability = calculateStability();
        
        // 计算丢包率
        double packetLossRate = calculatePacketLossRate();
        
        // 获取推荐线程数
        int recommendedThreads = currentCondition.get().getRecommendedThreads();
        
        // 创建网络状态
        NetworkState state = new NetworkState(
            currentSpeed, averageSpeed, stability,
            currentCondition.get(), recommendedThreads,
            totalBytesDownloaded.get(),
            System.currentTimeMillis() - startTime.get(),
            packetLossRate
        );
        
        latestState.set(state);
    }
    
    /**
     * 计算网络稳定性
     */
    private double calculateStability() {
        if (speedHistory.size() < 2) {
            return 1.0;
        }
        
        double mean = speedHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
        
        if (mean == 0) {
            return 0.0;
        }
        
        double variance = speedHistory.stream()
            .mapToDouble(speed -> Math.pow(speed - mean, 2))
            .average()
            .orElse(0);
        
        return 1.0 - Math.min(variance / (mean * mean), 1.0);
    }
    
    /**
     * 计算丢包率
     */
    private double calculatePacketLossRate() {
        int totalSamples = successfulSamples + failedSamples;
        if (totalSamples == 0) {
            return 0.0;
        }
        
        return (double) failedSamples / totalSamples;
    }
    
    /**
     * 获取当前网络状态
     */
    public NetworkState getCurrentNetworkState() {
        return latestState.get();
    }
    
    /**
     * 获取推荐线程数
     */
    public int getRecommendedThreads() {
        return currentCondition.get().getRecommendedThreads();
    }
    
    /**
     * 根据网络状况调整线程数
     */
    public int adjustThreadsBasedOnNetwork(int currentThreads) {
        NetworkCondition condition = currentCondition.get();
        int recommendedThreads = condition.getRecommendedThreads();
        
        // 根据网络状况动态调整
        int adjustedThreads;
        
        switch (condition) {
            case EXCELLENT:
                // 优秀网络：增加线程数
                adjustedThreads = Math.min(currentThreads + 2, MAX_THREADS);
                break;
                
            case GOOD:
                // 良好网络：保持或微调线程数
                adjustedThreads = Math.min(Math.max(currentThreads, recommendedThreads), MAX_THREADS);
                break;
                
            case FAIR:
                // 一般网络：减少线程数
                adjustedThreads = Math.max(currentThreads - 1, MIN_THREADS);
                break;
                
            case POOR:
            case VERY_POOR:
                // 较差网络：使用最小线程数
                adjustedThreads = MIN_THREADS;
                break;
                
            default:
                adjustedThreads = currentThreads;
        }
        
        if (adjustedThreads != currentThreads) {
            TIPUAMod.LOGGER.info("根据网络状况调整线程数: {} -> {} / Adjusting thread count based on network condition: {} -> {}", 
                currentThreads, adjustedThreads, currentThreads, adjustedThreads);
        }
        
        return adjustedThreads;
    }
    
    /**
     * 是否应该回退到单线程下载
     */
    public boolean shouldFallbackToSingleThread() {
        NetworkState state = getCurrentNetworkState();
        return state.getCurrentSpeed() < MIN_SPEED_THRESHOLD || 
               state.getStability() < STABILITY_THRESHOLD * 0.5 ||
               state.getPacketLossRate() > 0.3;
    }
    
    /**
     * 获取下载状态描述
     */
    public String getStatusDescription() {
        NetworkState state = getCurrentNetworkState();
        if (state == null) {
            return "网络监控未启动 / Network monitoring not started";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("网络状况 / Network Condition: ").append(state.getCondition().getChineseName())
          .append(" (").append(state.getCondition().getEnglishName()).append(")\n");
        sb.append("当前速度 / Current Speed: ").append(state.getFormattedCurrentSpeed()).append("\n");
        sb.append("平均速度 / Average Speed: ").append(state.getFormattedAverageSpeed()).append("\n");
        sb.append("网络稳定性 / Stability: ").append(String.format("%.1f%%", state.getStability() * 100)).append("\n");
        sb.append("推荐线程数 / Recommended Threads: ").append(state.getRecommendedThreads()).append("\n");
        sb.append("已下载 / Downloaded: ").append(formatBytes(state.getTotalBytesDownloaded())).append("\n");
        sb.append("总用时 / Total Time: ").append(formatTime(state.getTotalTimeElapsed())).append("\n");
        sb.append("丢包率 / Packet Loss: ").append(String.format("%.1f%%", state.getPacketLossRate() * 100));
        
        return sb.toString();
    }
    
    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * 格式化时间
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) return seconds + "s";
        else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%dm %ds", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return String.format("%dh %dm", hours, remainingMinutes);
        }
    }
    
    /**
     * 重置监控数据
     */
    public void reset() {
        totalBytesDownloaded.set(0);
        startTime.set(System.currentTimeMillis());
        lastSampleTime.set(System.currentTimeMillis());
        lastBytesSample.set(0);
        speedHistory.clear();
        successfulSamples = 0;
        failedSamples = 0;
        
        TIPUAMod.LOGGER.info("网络监控数据已重置 / Network monitoring data has been reset");
    }
    
    /**
     * 检查是否正在监控
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
}
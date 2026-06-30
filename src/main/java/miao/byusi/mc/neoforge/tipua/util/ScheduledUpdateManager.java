package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.ClientConfig;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时更新管理器
 * 管理定时更新任务的调度和执行
 */
public class ScheduledUpdateManager {
    
    private static ScheduledExecutorService scheduler;
    private static volatile boolean isRunning = false;
    private static volatile LocalDateTime lastUpdateTime = null;
    private static volatile LocalDateTime nextScheduledTime = null;
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 启动定时更新调度器
     */
    public static void startScheduler() {
        if (isRunning) {
            TIPUAMod.LOGGER.warn("定时更新调度器已在运行 / Scheduled update scheduler is already running");
            return;
        }
        
        if (!ClientConfig.isScheduledUpdateEnabled()) {
            TIPUAMod.LOGGER.info("定时更新未启用 / Scheduled update is disabled");
            return;
        }
        
        String scheduledTime = ClientConfig.getScheduledUpdateTime();
        if (scheduledTime == null || scheduledTime.trim().isEmpty()) {
            TIPUAMod.LOGGER.info("定时更新时间未配置 / Scheduled update time is not configured");
            return;
        }
        
        try {
            LocalTime targetTime = LocalTime.parse(scheduledTime.trim(), TIME_FORMATTER);
            calculateNextScheduledTime(targetTime);
            
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "TIPUA-ScheduledUpdate");
                thread.setDaemon(true);
                return thread;
            });
            
            isRunning = true;
            scheduleNextUpdate();
            
            TIPUAMod.LOGGER.info("定时更新调度器已启动，下次更新时间: {} / Scheduled update scheduler started, next update time: {}", 
                nextScheduledTime.format(DATETIME_FORMATTER), nextScheduledTime.format(DATETIME_FORMATTER));
            
        } catch (DateTimeParseException e) {
            TIPUAMod.LOGGER.error("定时更新时间格式无效: {}，请使用HH:mm格式 / Invalid scheduled update time format: {}, please use HH:mm format", 
                scheduledTime, scheduledTime, e);
        }
    }
    
    /**
     * 停止定时更新调度器
     */
    public static void stopScheduler() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        
        TIPUAMod.LOGGER.info("定时更新调度器已停止 / Scheduled update scheduler stopped");
    }
    
    /**
     * 计算下次计划更新时间
     */
    private static void calculateNextScheduledTime(LocalTime targetTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledDateTime = LocalDateTime.of(now.toLocalDate(), targetTime);
        
        // 如果今天的时间已经过了，设置为明天
        if (now.isAfter(scheduledDateTime)) {
            scheduledDateTime = scheduledDateTime.plusDays(1);
        }
        
        nextScheduledTime = scheduledDateTime;
    }
    
    /**
     * 调度下次更新
     */
    private static void scheduleNextUpdate() {
        if (!isRunning || scheduler == null || nextScheduledTime == null) {
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        long delaySeconds = java.time.Duration.between(now, nextScheduledTime).getSeconds();
        
        if (delaySeconds <= 0) {
            // 时间已过，重新计算
            String scheduledTime = ClientConfig.getScheduledUpdateTime();
            try {
                LocalTime targetTime = LocalTime.parse(scheduledTime.trim(), TIME_FORMATTER);
                calculateNextScheduledTime(targetTime);
                delaySeconds = java.time.Duration.between(now, nextScheduledTime).getSeconds();
            } catch (DateTimeParseException e) {
                TIPUAMod.LOGGER.error("定时更新时间格式无效 / Invalid scheduled update time format", e);
                stopScheduler();
                return;
            }
        }
        
        TIPUAMod.LOGGER.info("下次更新将在 {} 秒后执行 / Next update will be executed in {} seconds", delaySeconds, delaySeconds);
        
        scheduler.schedule(() -> {
            executeScheduledUpdate();
            // 重新调度下次更新
            calculateNextScheduledTime(LocalTime.parse(ClientConfig.getScheduledUpdateTime().trim(), TIME_FORMATTER));
            scheduleNextUpdate();
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 执行定时更新
     */
    private static void executeScheduledUpdate() {
        TIPUAMod.LOGGER.info("执行定时更新 / Executing scheduled update at {}", LocalDateTime.now().format(DATETIME_FORMATTER));
        
        try {
            // 这里触发实际的更新检查逻辑
            // 需要通过回调或其他方式通知ClientUpdateManager执行更新
            triggerUpdateCheck();
            
            lastUpdateTime = LocalDateTime.now();
            TIPUAMod.LOGGER.info("定时更新执行完成 / Scheduled update executed successfully at {}", 
                lastUpdateTime.format(DATETIME_FORMATTER));
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("定时更新执行失败 / Scheduled update execution failed", e);
        }
    }
    
    /**
     * 触发更新检查（需要与ClientUpdateManager集成）
     */
    private static void triggerUpdateCheck() {
        // 这里需要与ClientUpdateManager集成
        // 可以通过事件系统或直接调用
        TIPUAMod.LOGGER.info("触发定时更新检查 / Triggering scheduled update check");
        
        // 这个方法需要在ClientUpdateManager中实现对应的逻辑
        // 暂时先记录日志
    }
    
    /**
     * 重新加载配置并重启调度器
     */
    public static void reloadAndRestart() {
        stopScheduler();
        
        if (ClientConfig.isScheduledUpdateEnabled()) {
            startScheduler();
        }
    }
    
    /**
     * 检查是否到了定时更新时间
     */
    public static boolean isScheduledUpdateTime() {
        if (!ClientConfig.isScheduledUpdateEnabled() || nextScheduledTime == null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(nextScheduledTime) && 
               now.isBefore(nextScheduledTime.plusMinutes(1)); // 1分钟窗口期
    }
    
    /**
     * 获取下次计划更新时间
     */
    public static LocalDateTime getNextScheduledTime() {
        return nextScheduledTime;
    }
    
    /**
     * 获取上次更新时间
     */
    public static LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    /**
     * 获取距离下次更新的剩余时间（秒）
     */
    public static long getSecondsUntilNextUpdate() {
        if (nextScheduledTime == null) {
            return -1;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return java.time.Duration.between(now, nextScheduledTime).getSeconds();
    }
    
    /**
     * 检查调度器是否正在运行
     */
    public static boolean isRunning() {
        return isRunning && scheduler != null && !scheduler.isShutdown();
    }
    
    /**
     * 手动设置下次更新时间（用于测试）
     */
    public static void setNextScheduledTime(LocalDateTime time) {
        nextScheduledTime = time;
        TIPUAMod.LOGGER.info("手动设置下次更新时间为: {} / Manually set next update time to: {}", 
            time.format(DATETIME_FORMATTER), time.format(DATETIME_FORMATTER));
    }
    
    /**
     * 验证时间格式是否正确
     */
    public static boolean isValidTimeFormat(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        
        try {
            LocalTime.parse(time.trim(), TIME_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    /**
     * 获取定时更新状态信息
     */
    public static String getStatusInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("定时更新状态 / Scheduled Update Status:\n");
        sb.append("启用状态 / Enabled: ").append(isRunning() ? "是 / Yes" : "否 / No").append("\n");
        
        if (ClientConfig.isScheduledUpdateEnabled()) {
            sb.append("计划时间 / Scheduled Time: ").append(ClientConfig.getScheduledUpdateTime()).append("\n");
            
            if (nextScheduledTime != null) {
                sb.append("下次更新 / Next Update: ").append(nextScheduledTime.format(DATETIME_FORMATTER)).append("\n");
                
                long secondsRemaining = getSecondsUntilNextUpdate();
                if (secondsRemaining > 0) {
                    long hours = secondsRemaining / 3600;
                    long minutes = (secondsRemaining % 3600) / 60;
                    long seconds = secondsRemaining % 60;
                    sb.append(String.format("剩余时间 / Time Remaining: %02d:%02d:%02d\n", hours, minutes, seconds));
                }
            }
            
            if (lastUpdateTime != null) {
                sb.append("上次更新 / Last Update: ").append(lastUpdateTime.format(DATETIME_FORMATTER)).append("\n");
            }
            
            sb.append("自动执行 / Auto Execute: ").append(ClientConfig.isAutoUpdateOnSchedule() ? "是 / Yes" : "否 / No").append("\n");
        }
        
        return sb.toString();
    }
}
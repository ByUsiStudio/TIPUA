package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class EnhancedMultiThreadDownloader {
    
    private static final int DEFAULT_THREADS = 4;
    private static final int MIN_CHUNK_SIZE = 1024 * 1024;
    
    private final URL url;
    private final Path outputPath;
    private final int threadCount;
    private final int timeoutSeconds;
    private final DownloadProgressCallback callback;
    private final Consumer<DownloadStatus> statusCallback;
    
    private volatile boolean isCancelled = false;
    private ExecutorService executorService;
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private long totalSize = 0;
    private volatile Throwable error = null;
    
    private int currentAttempt = 0;
    private final int maxRetryAttempts;
    private final int retryDelaySeconds;
    
    private final List<DownloadStatusListener> statusListeners = new ArrayList<>();
    
    @FunctionalInterface
    public interface DownloadProgressCallback {
        void onProgress(long downloaded, long total, double speed);
    }
    
    @FunctionalInterface
    public interface DownloadStatusListener {
        void onStatusChange(DownloadStatus status);
    }
    
    public enum DownloadStatus {
        STARTING("开始下载", "Starting download"),
        CONNECTING("连接服务器", "Connecting to server"),
        DOWNLOADING("下载中", "Downloading"),
        PAUSED("已暂停", "Paused"),
        COMPLETED("下载完成", "Download completed"),
        FAILED("下载失败", "Download failed"),
        RETRYING("重试中", "Retrying"),
        CANCELLED("已取消", "Cancelled");
        
        private final String chineseStatus;
        private final String englishStatus;
        
        DownloadStatus(String chineseStatus, String englishStatus) {
            this.chineseStatus = chineseStatus;
            this.englishStatus = englishStatus;
        }
        
        public String getChineseStatus() {
            return chineseStatus;
        }
        
        public String getEnglishStatus() {
            return englishStatus;
        }
    }
    
    public EnhancedMultiThreadDownloader(URL url, int timeoutSeconds, int maxRetryAttempts, 
                                        int retryDelaySeconds, DownloadProgressCallback callback, 
                                        Consumer<DownloadStatus> statusCallback) {
        this(url, FabricLoader.getInstance().getGameDir().resolve(".tipua_temp_download.zip"), timeoutSeconds, 
             DEFAULT_THREADS, maxRetryAttempts, retryDelaySeconds, callback, statusCallback);
    }
    
    public EnhancedMultiThreadDownloader(URL url, Path outputPath, int timeoutSeconds, int threadCount,
                                        int maxRetryAttempts, int retryDelaySeconds, 
                                        DownloadProgressCallback callback, Consumer<DownloadStatus> statusCallback) {
        this.url = url;
        this.outputPath = outputPath;
        this.timeoutSeconds = timeoutSeconds;
        this.threadCount = Math.max(1, threadCount);
        this.callback = callback;
        this.statusCallback = statusCallback;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
    }
    
    public void addStatusListener(DownloadStatusListener listener) {
        statusListeners.add(listener);
    }
    
    private void notifyStatusChange(DownloadStatus status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
        for (DownloadStatusListener listener : statusListeners) {
            listener.onStatusChange(status);
        }
    }
    
    public Path downloadWithRetry() throws IOException {
        currentAttempt = 0;
        IOException lastException = null;
        
        while (currentAttempt <= maxRetryAttempts) {
            currentAttempt++;
            
            if (currentAttempt > 1) {
                notifyStatusChange(DownloadStatus.RETRYING);
                TIPUAMod.LOGGER.info("Retry attempt {}...", currentAttempt - 1);
                
                try {
                    Thread.sleep(retryDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", e);
                }
            } else {
                notifyStatusChange(DownloadStatus.STARTING);
            }
            
            try {
                return download();
            } catch (IOException e) {
                lastException = e;
                
                if (!shouldRetry(e) || currentAttempt > maxRetryAttempts) {
                    throw e;
                }
                
                DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetail(e);
                TIPUAMod.LOGGER.warn("Download failed, reason: {}", errorDetail.getErrorMessage());
                
                cleanupTempFiles();
            }
        }
        
        throw lastException;
    }
    
    private boolean shouldRetry(IOException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("timeout") || 
               message.contains("Timeout") ||
               message.contains("Connection") ||
               message.contains("Network") ||
               message.contains("500") ||
               message.contains("502") ||
               message.contains("503") ||
               message.contains("504");
    }
    
    private void cleanupTempFiles() {
        try {
            if (Files.exists(outputPath)) {
                Files.delete(outputPath);
            }
            
            for (int i = 0; i < threadCount; i++) {
                Path partFile = FabricLoader.getInstance().getGameDir().resolve(".tipua_part_" + i);
                if (Files.exists(partFile)) {
                    Files.delete(partFile);
                }
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("Failed to cleanup temp files", e);
        }
    }
    
    public Path download() throws IOException {
        if (isCancelled) {
            throw new IOException("Download cancelled");
        }
        
        if (Files.exists(outputPath)) {
            Files.delete(outputPath);
        }
        
        notifyStatusChange(DownloadStatus.CONNECTING);
        totalSize = getContentLength();
        
        TIPUAMod.LOGGER.info("Starting multi-thread download: {} bytes, {} threads", totalSize, threadCount);
        
        if (totalSize <= 0) {
            return downloadSingleThread();
        }
        
        boolean supportsRange = checkRangeSupport();
        if (!supportsRange) {
            TIPUAMod.LOGGER.warn("Server doesn't support Range requests, falling back to single thread");
            return downloadSingleThread();
        }
        
        if (totalSize < MIN_CHUNK_SIZE * threadCount) {
            return downloadSingleThread();
        }
        
        return downloadMultiThread();
    }
    
    private long getContentLength() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestMethod("HEAD");
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return connection.getContentLengthLong();
            } else {
                throw new IOException("Server error: HTTP " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    private boolean checkRangeSupport() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestMethod("HEAD");
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        connection.setRequestProperty("Range", "bytes=0-0");
        
        try {
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_PARTIAL;
        } finally {
            connection.disconnect();
        }
    }
    
    private Path downloadSingleThread() throws IOException {
        TIPUAMod.LOGGER.info("Using single thread download");
        notifyStatusChange(DownloadStatus.DOWNLOADING);
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Download failed: HTTP " + responseCode);
        }
        
        long contentLength = connection.getContentLengthLong();
        if (contentLength > 0) {
            totalSize = contentLength;
        }
        
        long startTime = System.currentTimeMillis();
        long lastProgressUpdate = startTime;
        
        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(outputPath)) {
            
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            
            while ((read = is.read(buffer)) != -1) {
                if (isCancelled) {
                    throw new IOException("Download cancelled");
                }
                os.write(buffer, 0, read);
                totalRead += read;
                totalDownloaded.addAndGet(read);
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastProgressUpdate >= 500) {
                    double speed = calculateSpeed(startTime, totalDownloaded.get());
                    if (callback != null) {
                        callback.onProgress(totalDownloaded.get(), totalSize, speed);
                    }
                    lastProgressUpdate = currentTime;
                }
            }
        } finally {
            connection.disconnect();
        }
        
        notifyStatusChange(DownloadStatus.COMPLETED);
        return outputPath;
    }
    
    private double calculateSpeed(long startTime, long downloadedBytes) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (elapsedTime <= 0) {
            return 0.0;
        }
        return (downloadedBytes / 1024.0) / (elapsedTime / 1000.0);
    }
    
    private Path downloadMultiThread() throws IOException {
        notifyStatusChange(DownloadStatus.DOWNLOADING);
        
        List<Path> partFiles = new ArrayList<>();
        long chunkSize = totalSize / threadCount;
        long startTime = System.currentTimeMillis();
        
        try {
            executorService = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                long start = i * chunkSize;
                long end = (i == threadCount - 1) ? totalSize - 1 : (i + 1) * chunkSize - 1;
                
                Path partFile = FabricLoader.getInstance().getGameDir().resolve(".tipua_part_" + i);
                partFiles.add(partFile);
                
                int threadIndex = i;
                futures.add(executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downloadChunk(start, end, partFile, threadIndex, startTime);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }));
            }
            
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new IOException(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", e);
                }
            }
            
            mergeParts(partFiles);
            
            notifyStatusChange(DownloadStatus.COMPLETED);
            TIPUAMod.LOGGER.info("Multi-thread download completed");
            return outputPath;
            
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
            for (Path partFile : partFiles) {
                Files.deleteIfExists(partFile);
            }
        }
    }
    
    private void downloadChunk(long start, long end, Path partFile, int threadIndex, long startTime) throws IOException {
        if (isCancelled) {
            throw new IOException("Download cancelled");
        }
        
        TIPUAMod.LOGGER.debug("Thread {} downloading chunk: {} - {}", threadIndex, start, end);
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw new IOException("Thread " + threadIndex + " download failed: HTTP " + responseCode);
        }
        
        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(partFile)) {
            
            byte[] buffer = new byte[8192];
            int read;
            
            while ((read = is.read(buffer)) != -1) {
                if (isCancelled) {
                    throw new IOException("Download cancelled");
                }
                os.write(buffer, 0, read);
                totalDownloaded.addAndGet(read);
                
                double speed = calculateSpeed(startTime, totalDownloaded.get());
                if (callback != null) {
                    callback.onProgress(totalDownloaded.get(), totalSize, speed);
                }
            }
        } finally {
            connection.disconnect();
        }
        
        TIPUAMod.LOGGER.debug("Thread {} download completed", threadIndex);
    }
    
    private void mergeParts(List<Path> partFiles) throws IOException {
        TIPUAMod.LOGGER.info("Merging part files");
        
        try (OutputStream os = Files.newOutputStream(outputPath)) {
            for (Path partFile : partFiles) {
                if (!Files.exists(partFile)) {
                    throw new IOException("Part file does not exist: " + partFile);
                }
                try (InputStream is = Files.newInputStream(partFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
            }
        }
        
        TIPUAMod.LOGGER.info("Part files merged");
    }
    
    public void cancel() {
        isCancelled = true;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        notifyStatusChange(DownloadStatus.CANCELLED);
    }
    
    public int getCurrentAttempt() {
        return currentAttempt;
    }
    
    public DownloadStatus getCurrentStatus() {
        if (isCancelled) {
            return DownloadStatus.CANCELLED;
        }
        return DownloadStatus.DOWNLOADING;
    }
}
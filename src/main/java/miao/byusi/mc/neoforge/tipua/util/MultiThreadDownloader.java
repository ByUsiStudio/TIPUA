package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.fml.loading.FMLPaths;

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

public class MultiThreadDownloader {
    
    private static final int DEFAULT_THREADS = 4;
    private static final int MIN_CHUNK_SIZE = 1024 * 1024;
    
    private final URL url;
    private final Path outputPath;
    private final int threadCount;
    private final int timeoutSeconds;
    private final DownloadProgressCallback callback;
    private final int maxRetryAttempts;
    private final int retryDelaySeconds;
    
    private volatile boolean isCancelled = false;
    private ExecutorService executorService;
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private long totalSize = 0;
    private volatile Throwable error = null;
    
    private int currentAttempt = 0;
    
    @FunctionalInterface
    public interface DownloadProgressCallback {
        void onProgress(long downloaded, long total);
    }
    
    public MultiThreadDownloader(URL url, int timeoutSeconds, DownloadProgressCallback callback) {
        this(url, FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip"), timeoutSeconds, DEFAULT_THREADS, 0, 5, callback);
    }
    
    public MultiThreadDownloader(URL url, Path outputPath, int timeoutSeconds, int threadCount, 
                                int maxRetryAttempts, int retryDelaySeconds, DownloadProgressCallback callback) {
        this.url = url;
        this.outputPath = outputPath;
        this.timeoutSeconds = timeoutSeconds;
        this.threadCount = Math.max(1, threadCount);
        this.callback = callback;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
    }
    
    public Path downloadWithRetry() throws IOException {
        currentAttempt = 0;
        IOException lastException = null;
        
        while (currentAttempt <= maxRetryAttempts) {
            currentAttempt++;
            
            if (currentAttempt > 1) {
                TIPUAMod.LOGGER.info("第 {} 次重试下载... / Retry attempt {}...", currentAttempt - 1, currentAttempt - 1);
                
                try {
                    Thread.sleep(retryDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("下载被中断 / Download interrupted", e);
                }
            }
            
            try {
                return download();
            } catch (IOException e) {
                lastException = e;
                
                // 检查是否应该重试
                if (!shouldRetry(e) || currentAttempt > maxRetryAttempts) {
                    throw e;
                }
                
                DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetail(e);
                TIPUAMod.LOGGER.warn("下载失败，原因: {} / Download failed, reason: {}", 
                    errorDetail.getErrorMessage(), errorDetail.getErrorMessage());
                
                // 清理临时文件
                cleanupTempFiles();
            }
        }
        
        throw lastException;
    }
    
    private boolean shouldRetry(IOException exception) {
        if (maxRetryAttempts <= 0) {
            return false;
        }
        
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("timeout") || 
               message.contains("Timeout") ||
               message.contains("连接") || 
               message.contains("Connection") ||
               message.contains("网络") ||
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
                Path partFile = FMLPaths.GAMEDIR.get().resolve(".tipua_part_" + i);
                if (Files.exists(partFile)) {
                    Files.delete(partFile);
                }
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理临时文件失败 / Failed to cleanup temp files", e);
        }
    }
    
    public Path download() throws IOException {
        if (Files.exists(outputPath)) {
            Files.delete(outputPath);
        }
        
        totalSize = getContentLength();
        TIPUAMod.LOGGER.info("开始多线程下载: {} bytes, {} 线程 / Starting multi-thread download: {} bytes, {} threads", 
                totalSize, threadCount, totalSize, threadCount);
        
        if (totalSize <= 0) {
            return downloadSingleThread();
        }
        
        boolean supportsRange = checkRangeSupport();
        if (!supportsRange) {
            TIPUAMod.LOGGER.warn("服务器不支持Range请求，回退到单线程下载 / Server doesn't support Range requests, falling back to single thread");
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
            }
        } finally {
            connection.disconnect();
        }
        return -1;
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
        TIPUAMod.LOGGER.info("使用单线程下载 / Using single thread download");
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("下载失败: HTTP " + responseCode);
        }
        
        long contentLength = connection.getContentLengthLong();
        if (contentLength > 0) {
            totalSize = contentLength;
        }
        
        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(outputPath)) {
            
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            
            while ((read = is.read(buffer)) != -1) {
                if (isCancelled) {
                    throw new IOException("下载已取消");
                }
                os.write(buffer, 0, read);
                totalRead += read;
                totalDownloaded.addAndGet(read);
                
                if (callback != null) {
                    callback.onProgress(totalDownloaded.get(), totalSize);
                }
            }
        } finally {
            connection.disconnect();
        }
        
        return outputPath;
    }
    
    private Path downloadMultiThread() throws IOException {
        List<Path> partFiles = new ArrayList<>();
        long chunkSize = totalSize / threadCount;
        
        try {
            executorService = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            
            for (int i = 0; i < threadCount; i++) {
                long start = i * chunkSize;
                long end = (i == threadCount - 1) ? totalSize - 1 : (i + 1) * chunkSize - 1;
                
                Path partFile = FMLPaths.GAMEDIR.get().resolve(".tipua_part_" + i);
                partFiles.add(partFile);
                
                int threadIndex = i;
                futures.add(executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downloadChunk(start, end, partFile, threadIndex);
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
                    throw new IOException("下载被中断");
                }
            }
            
            mergeParts(partFiles);
            
            TIPUAMod.LOGGER.info("多线程下载完成 / Multi-thread download completed");
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
    
    private void downloadChunk(long start, long end, Path partFile, int threadIndex) throws IOException {
        if (isCancelled) {
            throw new IOException("下载已取消");
        }
        
        TIPUAMod.LOGGER.debug("线程 {} 下载块: {} - {} / Thread {} downloading chunk: {} - {}", 
                threadIndex, start, end, threadIndex, start, end);
        
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeoutSeconds * 1000);
        connection.setReadTimeout(timeoutSeconds * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
        connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_PARTIAL) {
            throw new IOException("线程 " + threadIndex + " 下载失败: HTTP " + responseCode);
        }
        
        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(partFile)) {
            
            byte[] buffer = new byte[8192];
            int read;
            
            while ((read = is.read(buffer)) != -1) {
                if (isCancelled) {
                    throw new IOException("下载已取消");
                }
                os.write(buffer, 0, read);
                totalDownloaded.addAndGet(read);
                
                if (callback != null) {
                    callback.onProgress(totalDownloaded.get(), totalSize);
                }
            }
        } finally {
            connection.disconnect();
        }
        
        TIPUAMod.LOGGER.debug("线程 {} 下载完成 / Thread {} download completed", threadIndex, threadIndex);
    }
    
    private void mergeParts(List<Path> partFiles) throws IOException {
        TIPUAMod.LOGGER.info("合并分块文件 / Merging part files");
        
        try (OutputStream os = Files.newOutputStream(outputPath)) {
            for (Path partFile : partFiles) {
                if (!Files.exists(partFile)) {
                    throw new IOException("分块文件不存在: " + partFile);
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
        
        TIPUAMod.LOGGER.info("分块文件合并完成 / Part files merged");
    }
    
    public void cancel() {
        isCancelled = true;
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}

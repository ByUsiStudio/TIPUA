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
        this(url, FabricLoader.getInstance().getGameDir().resolve(".tipua_temp_download.zip"), timeoutSeconds, DEFAULT_THREADS, 0, 5, callback);
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
                TIPUAMod.LOGGER.info("Retry attempt {}...", currentAttempt - 1);
                
                try {
                    Thread.sleep(retryDelaySeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Download interrupted", e);
                }
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
        if (maxRetryAttempts <= 0) {
            return false;
        }
        
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
        if (Files.exists(outputPath)) {
            Files.delete(outputPath);
        }
        
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
        TIPUAMod.LOGGER.info("Using single thread download");
        
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
                
                Path partFile = FabricLoader.getInstance().getGameDir().resolve(".tipua_part_" + i);
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
                    throw new IOException("Download interrupted");
                }
            }
            
            mergeParts(partFiles);
            
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
    
    private void downloadChunk(long start, long end, Path partFile, int threadIndex) throws IOException {
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
                
                if (callback != null) {
                    callback.onProgress(totalDownloaded.get(), totalSize);
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
    }
}
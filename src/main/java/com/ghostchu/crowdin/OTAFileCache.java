package com.ghostchu.crowdin;

import com.ghostchu.crowdin.exception.OTAException;
import com.ghostchu.crowdin.util.DigestUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OTAFileCache {
    private static final Gson gson = new Gson();
    private final Logger LOG;
    private final File rootCacheFolder;
    private final File memoryDBFile;
    /**
     * CacheMemory
     * CrowdinSyntaxCode - CacheRecord
     * Encoding by Gson to JSON format.
     */
    private Map<String, CacheRecord> cacheMemory;

    /**
     * Create a OTAFileCache instance to manage the files on the filesystem and caches.
     *
     * @param rootCacheFolder The root folder to store the cache files.
     * @throws OTAException Throws a OTAException while failed to create the cache folder.
     */
    public OTAFileCache(@NotNull File rootCacheFolder) throws OTAException {
        this.LOG = Logger.getLogger("OTAFileCache - " + rootCacheFolder.getName());
        this.rootCacheFolder = rootCacheFolder;
        initCacheFolder();
        this.memoryDBFile = new File(rootCacheFolder, "memory.json");
        loadMemory();
    }

    /**
     * Read the cache from the disk.
     *
     * @param crowdinSyntaxCode The crowdin syntax code.
     * @param timestamp         The timestamp of the manifest.
     * @param allowExpired      Whether to allow expired cache. False will return null for outdated cache.
     * @return The cache content
     */
    @Nullable
    public String readCache(@NotNull String crowdinSyntaxCode, long timestamp, boolean allowExpired) {
        CacheStatus status = getCacheStatus(crowdinSyntaxCode, timestamp);
        // EXPIRED
        if (!allowExpired) {
            if (status == CacheStatus.CACHE_EXPIRED) return null;
        }
        // INVALID
        if (status == CacheStatus.CACHE_INVALID) return null;
        // NOT_CACHED
        if (status == CacheStatus.NOT_CACHED) return null;
        // VALID
        return _getContent(crowdinSyntaxCode);
    }

    /**
     * Write the cache into disk and update memory DB.
     *
     * @param crowdinSyntaxCode The crowdin syntax code.
     * @param fileContent       The file content.
     * @param timestamp         The timestamp of the manifest.
     */
    public void writeCache(@NotNull String crowdinSyntaxCode, @NotNull String fileContent, long timestamp) {
        _writeContent(crowdinSyntaxCode, fileContent);
        // Generate SHA-1 hash
        String sha1 = DigestUtil.sha1(fileContent);
        // Write to memory
        cacheMemory.put(crowdinSyntaxCode, new CacheRecord(timestamp, sha1));
        // Save the memory to disk.
        saveMemory();
    }

    /**
     * Gets the specific cache status
     *
     * @param crowdinSyntaxCode The Crowdin syntax language code
     * @param timestamp         The timestamp of the manifest.
     * @return The cache status
     */
    @NotNull
    public CacheStatus getCacheStatus(@NotNull String crowdinSyntaxCode, long timestamp) {
        // Lookup cache memory
        CacheRecord record = cacheMemory.get(crowdinSyntaxCode);
        if (record == null) return CacheStatus.NOT_CACHED;
        // Check if the file exists
        String fileContent = _getContent(crowdinSyntaxCode);
        if (fileContent == null) return CacheStatus.CACHE_INVALID;
        // Verify SHA1 hash
        String sha1 = DigestUtil.sha1(fileContent);
        if (!sha1.equals(record.sha1)) return CacheStatus.CACHE_INVALID;
        // Verify expired
        if (record.manifestTimestamp < timestamp) return CacheStatus.CACHE_EXPIRED;
        // All good!
        return CacheStatus.WORKING;
    }

    /**
     * Load the content from file
     *
     * @param crowdinSyntaxCode The crowdin syntax code
     * @return The content of the file, null if IOException or not exists.
     */
    @Nullable
    private String _getContent(@NotNull String crowdinSyntaxCode) {
        File file = new File(this.rootCacheFolder, crowdinSyntaxCode);
        if (!file.exists()) return null;
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.FINE, "File exists but not readable.", e);
            return null;
        }
    }

    /**
     * Write the content info file
     *
     * @param crowdinSyntaxCode The crowdin syntax code
     */
    private void _writeContent(@NotNull String crowdinSyntaxCode, @NotNull String content) {
        File file = new File(this.rootCacheFolder, crowdinSyntaxCode);
        try {
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    LOG.fine("Failed to create file: " + file.getAbsolutePath() + ", giving up...");
                }
            }
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.FINE, "IOException while write the cache", e);
        }
    }

    /**
     * Save the memory DB from memory to disk.
     */
    private void saveMemory() {
        try {
            Files.writeString(memoryDBFile.toPath(), gson.toJson(cacheMemory));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save memory, all new cache entries and changes will be dropped.", e);
        }
    }

    /**
     * Load the memory DB from memory.json file.
     */
    private void loadMemory() {
        // First, load it as a default
        this.cacheMemory = new HashMap<>();
        if (!memoryDBFile.exists()) return; // Give up loading, because it not exists at all.
        try {
            // Load from JSON file.
            Map<String, CacheRecord> memoryDB = gson.fromJson(Files.readString(new File(rootCacheFolder, "memory.json").toPath()), new TypeToken<Map<String, CacheRecord>>() {
            }.getType());
            // Use thread-safe map for memory DB, we need parallel download files.
            this.cacheMemory = new ConcurrentHashMap<>(memoryDB);
        } catch (IOException e) {
            // For any error
            LOG.log(Level.WARNING, "Failed to load cache memory from file: " + memoryDBFile.getName(), e);
            // Then safely ignore with default empty cache.
        }
    }

    private void initCacheFolder() throws OTAException {
        if (!this.rootCacheFolder.exists()) {
            if (!this.rootCacheFolder.getParentFile().exists()) {
                if (!this.rootCacheFolder.getParentFile().mkdirs()) {
                    throw new OTAException("Failed to create cache folder: " + this.rootCacheFolder.getParentFile().getAbsolutePath());
                }
            }
            if (!this.rootCacheFolder.mkdir()) {
                throw new OTAException("Failed to create cache folder: " + this.rootCacheFolder.getAbsolutePath());
            }
        }
        if (!this.rootCacheFolder.isDirectory()) {
            throw new OTAException("Cache folder is not a directory: " + this.rootCacheFolder.getAbsolutePath());
        }
    }

    /**
     * The cache status
     */
    enum CacheStatus {
        /**
         * The cache file not exists at all or not logged into memory.
         */
        NOT_CACHED,
        /**
         * The cache file has been modified.
         */
        CACHE_INVALID,
        /**
         * Expired but still working.
         */
        CACHE_EXPIRED,
        /**
         * The cache file is valid and ready to go.
         */
        WORKING
    }

    /**
     * The cache record
     */
    static class CacheRecord {
        private final long manifestTimestamp;
        // UTF-8 SHA1
        private final String sha1;
        public CacheRecord(long manifestTimestamp, String sha1) {
            this.manifestTimestamp = manifestTimestamp;
            this.sha1 = sha1;
        }

        public long getManifestTimestamp() {
            return manifestTimestamp;
        }

        public String getSha1() {
            return sha1;
        }
    }
}

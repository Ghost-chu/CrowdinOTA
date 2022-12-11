package com.ghostchu.crowdin;

import com.ghostchu.crowdin.exception.OTAException;
import com.ghostchu.crowdin.util.DigestUtil;
import com.ghostchu.crowdin.util.ForkJoinPoolUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class OTAFileInstance {
    private final Logger LOG;
    private final String fileName;
    private final CrowdinOTA parent;
    private final int fileIndex;
    private final UnirestInstance unirest;
    private final ReentrantLock LOCK = new ReentrantLock();
    /**
     * URL Mapping
     * Key: CrowdinSyntax Language Code
     * Value: Host appended URl
     */
    private Map<String, String> urlMapping;
    private final OTAFileCache fileCache;

    /**
     * Creates a OTAFileInstance instance.
     *
     * @param parent    The parent CrowdinOTA instance.
     * @param fileName  The file name.
     * @param fileIndex The file position in `files` array.
     * @throws OTAException Throws a OTAException while failed during requesting or processing.
     */
    public OTAFileInstance(@NotNull CrowdinOTA parent, @NotNull String fileName, int fileIndex, @NotNull UnirestInstance unirest) throws OTAException {
        this.parent = parent;
        this.fileName = fileName;
        this.fileIndex = fileIndex;
        this.unirest = unirest;
        this.LOG = Logger.getLogger("OTAFileInstance-" + fileName + "-" + fileIndex);
        this.fileCache = new OTAFileCache(initCacheFolder());
        initUrlMapping();
        downloadFiles(true, 16);
    }

    /**
     * Get the translation content
     *
     * @param customSyntax     The syntax name in language_mapping
     * @param customLocaleCode The language code in your custom syntax in language_mapping
     * @return The translation content.
     */
    @Nullable
    public String getLocaleContentByCustomCode(@NotNull String customSyntax, @NotNull String customLocaleCode) {
        return getLocaleContentByCrowdinCode(parent.mapLanguageCustom(customLocaleCode, customSyntax));
    }

    /**
     * Get the translation content
     *
     * @param crowdinSyntaxLanguageCode The crowdin syntax language code.
     * @return The translation content.
     */
    @Nullable
    public String getLocaleContentByCrowdinCode(@NotNull String crowdinSyntaxLanguageCode) {
        LOCK.lock();
        try {
            return this.fileCache.readCache(crowdinSyntaxLanguageCode, parent.getTimestamp(), true);
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * Download translation files from Crowdin with Multi-Threaded (I/O Blocking)
     *
     * @param includeExpired Whether to include files which expired, False will only download invalid/not cached files
     * @param threads        The maximum threads for fork-join-pool.
     */
    public synchronized void downloadFiles(boolean includeExpired, int threads) {
        LOCK.lock();
        try {
            Set<String> localesNeedDownload = new HashSet<>(checkLocalesUnavailable());
            if (includeExpired) localesNeedDownload.addAll(checkLocalesExpired());
            LOG.info("Downloading translations for " + localesNeedDownload.size() + " locales...");
            List<Future<?>> futures = new ArrayList<>();
            // Create thread pool
            ExecutorService service = ForkJoinPoolUtil.createExecutorService(threads);
            // Parallel download files
            localesNeedDownload.forEach(locale -> futures.add(service.submit(() -> downloadFile(locale))));
            // Wait for all tasks to complete
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception ignored) {
                }
            });
        } finally {
            LOCK.unlock();
        }
    }

    private void downloadFile(@NotNull String crowdinSyntaxLanguageCode) {
        LOG.info("Downloading translation for " + crowdinSyntaxLanguageCode + "...");
        String url = urlMapping.get(crowdinSyntaxLanguageCode);
        if (url == null)
            throw new IllegalArgumentException("Invalid crowdinSyntaxLanguageCode: " + crowdinSyntaxLanguageCode);
        HttpResponse<String> response = unirest.get(url).asString();
        if (response.isSuccess()) {
            // write into cache
            this.fileCache.writeCache(crowdinSyntaxLanguageCode, response.getBody(), this.parent.getTimestamp());
            LOG.info("Downloaded translation for " + crowdinSyntaxLanguageCode + ".");
        } else {
            LOG.warning("Failed to download translation for " + crowdinSyntaxLanguageCode + ": " + response.getStatus());
        }
    }

    /**
     * Check all locales that need to update.
     *
     * @return Lists of locales that cache expired.
     */
    @NotNull
    private Set<String> checkLocalesExpired() {
        Set<String> expired = new HashSet<>();
        Set<String> locales = urlMapping.keySet();
        for (String locale : locales) {
            OTAFileCache.CacheStatus status = this.fileCache.getCacheStatus(locale, this.parent.getTimestamp());
            if (status == OTAFileCache.CacheStatus.CACHE_EXPIRED) {
                expired.add(locale);
            }
        }
        return expired;
    }

    /**
     * Check all locales translation availability.
     *
     * @return Lists of locales that cache invalid or not cached.
     */
    @NotNull
    private Set<String> checkLocalesUnavailable() {
        Set<String> unavailable = new HashSet<>();
        Set<String> locales = urlMapping.keySet();
        for (String locale : locales) {
            OTAFileCache.CacheStatus status = this.fileCache.getCacheStatus(locale, this.parent.getTimestamp());
            if (status == OTAFileCache.CacheStatus.NOT_CACHED || status == OTAFileCache.CacheStatus.CACHE_INVALID) {
                unavailable.add(locale);
            }
        }
        return unavailable;
    }

    /**
     * Creates the url for every locale and put into urlMapping
     */
    private void initUrlMapping() throws OTAException {
        JsonElement contentElement = this.parent.manifest.get("content");
        if (contentElement == null || !contentElement.isJsonObject()) {
            throw new OTAException("Either content field not found or not a object.");
        }
        Map<String, String> stageMapping = new LinkedHashMap<>();
        JsonObject content = contentElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> urlEntry : content.entrySet()) {
            String crowdinSyntaxCode = urlEntry.getKey();
            JsonElement pathElement = urlEntry.getValue();
            if (!pathElement.isJsonArray()) {
                throw new OTAException("The `content.<locale>` object not a array.");
            }
            JsonArray fileArray = pathElement.getAsJsonArray();
            if (fileArray.size() <= this.fileIndex) {
                LOG.warning("The `content.<locale>` array size is less than the file index, skipping...");
                continue;
            }
            String path = fileArray.get(this.fileIndex).getAsString();
            String url = this.parent.distributionUrl + path + "?timestamp=" + parent.getTimestamp();
            stageMapping.put(crowdinSyntaxCode, url);
        }
        this.urlMapping = stageMapping;
    }

    /**
     * Creates the cache folder if it doesn't exist.
     * Folder inside the parent cache folder, name is sha1(fileName)
     *
     * @return The cache folder.
     * @throws OTAException Throws a OTAException while failed to create the cache folder.
     */
    @NotNull
    private File initCacheFolder() throws OTAException {
        File parentFolder = parent.cacheFolder;
        File newFolder = new File(parentFolder, DigestUtil.sha1(this.fileName));
        if (!newFolder.exists()) {
            if (!newFolder.mkdirs()) {
                throw new OTAException("Failed to create cache folder for " + this.fileName);
            }
        }
        return newFolder;
    }
}

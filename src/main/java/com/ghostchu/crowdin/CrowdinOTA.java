package com.ghostchu.crowdin;

import com.ghostchu.crowdin.exception.OTAException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class CrowdinOTA {
    protected final String distributionUrl;
    protected final UnirestInstance unirest;
    protected final File cacheFolder;
    protected JsonObject manifest;
    protected Map<String, Map<String, String>> languageMapping;

    private OTAInstance otaInstance;

    /**
     * Create CrowdinOTA instance from a crowdin distribution URL.
     *
     * @param distributionUrl The distribution URL.
     *                        Example: <a href="https://distributions.crowdin.net/91b97508fdf19626f2977b7xrm4/">...</a>
     * @param cacheFolder     The folder to put cache files
     * @throws OTAException Throws a OTAException while failed during requesting or processing the manifest.
     */
    public CrowdinOTA(@NotNull String distributionUrl, @NotNull File cacheFolder) throws OTAException {
        this.distributionUrl = distributionUrl;
        this.cacheFolder = cacheFolder;
        this.unirest = Unirest.primaryInstance();
        initCacheFolder();
        fetchMetadata();
        loadLanguageMapping();
        createOTAInstance();
    }


    /**
     * Create CrowdinOTA instance from a crowdin distribution URL.
     *
     * @param distributionUrl The distribution URL.
     *                        Example: <a href="https://distributions.crowdin.net/91b97508fdf19626f2977b7xrm4/">...</a>
     * @param cacheFolder     The folder to put cache files
     * @param unirest         The unirest instance for requesting
     * @throws OTAException Throws a OTAException while failed during requesting or processing manifest.
     */
    public CrowdinOTA(@NotNull String distributionUrl, @NotNull File cacheFolder, @NotNull UnirestInstance unirest) throws OTAException {
        this.distributionUrl = distributionUrl;
        this.unirest = unirest;
        this.cacheFolder = cacheFolder;
        initCacheFolder();
        fetchMetadata();
        loadLanguageMapping();
        createOTAInstance();
    }

    /**
     * Gets the OTAInstance which you can locate to load or processing any file instance you want.
     *
     * @return The OTAInstance instance.
     */
    public @NotNull OTAInstance getOtaInstance() {
        return this.otaInstance;
    }

    /**
     * Creates the OTAInstance.
     *
     * @throws OTAException Throws a OTAException while failed during requesting or processing manifest.
     */
    private void createOTAInstance() throws OTAException {
        this.otaInstance = new OTAInstance(this, unirest);
    }

    /**
     * Gets the timestamp of this manifest
     *
     * @return The timestamp of this manifest.
     */
    public long getTimestamp() {
        return manifest.get("timestamp").getAsLong();
    }

    /**
     * Gets the mapped language code for the given locale.
     * For example:
     * "tr": {
     * "locale": "tr-TR"
     * }
     * In this case, give "tr" for crowdinSyntaxCode and "locale" for customSyntaxName, "tr-TR" will be returns.
     * If nothing matches, return crowdinSyntaxCode as-is.
     *
     * @param crowdinSyntaxCode The language code (crowdin name)
     * @param customSyntaxName  The mapping name (custom name)
     * @return The mapped language code
     */
    @NotNull
    public String mapLanguageCode(@NotNull String crowdinSyntaxCode, @NotNull String customSyntaxName) {
        Map<?, ?> mapping = this.languageMapping.get(crowdinSyntaxCode);
        if (mapping == null) return crowdinSyntaxCode; // Return as-is because no mapping found
        @SuppressWarnings("unchecked")
        Map<String, String> customSyntax = (Map<String, String>) mapping.get(customSyntaxName);
        if (customSyntax == null) return crowdinSyntaxCode; // Return as-is because no custom mapping found
        return customSyntax.get(crowdinSyntaxCode);
    }

    /**
     * Load the language mapping
     *
     * @throws OTAException When manifest invalid
     */
    private void loadLanguageMapping() throws OTAException {
        JsonObject obj = this.manifest.getAsJsonObject("language_mapping");
        if (obj == null) return;
        this.languageMapping = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            // load the mapping
            String crowdinSyntaxCode = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                throw new OTAException("One of element in language_mapping's value not a json object.");
            }
            Map<String, String> mappingSet = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> mappingEntry : entry.getValue().getAsJsonObject().entrySet()) {
                String customSyntaxName = mappingEntry.getKey();
                String customSyntaxCode = mappingEntry.getValue().getAsString();
                mappingSet.put(customSyntaxName, customSyntaxCode);
            }
            this.languageMapping.put(crowdinSyntaxCode, mappingSet);
        }

    }


    /**
     * Fetch metadata from Crowdin and cache in local.
     */
    private void fetchMetadata() throws OTAException {
        HttpResponse<String> response = unirest.get(this.distributionUrl).asString();
        if (!response.isSuccess()) {
            throw new OTAException("Failed to get Crowdin distribution manifest: " + response.getStatus());
        }
        String manifestJson = response.getBody();
        try {
            JsonElement element = JsonParser.parseString(manifestJson);
            if (!element.isJsonObject())
                throw new OTAException("Failed to parse Crowdin distribution manifest: root path must is a json object.");
            this.manifest = element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new OTAException("Failed to parse Crowdin distribution manifest: " + e.getMessage(), e);
        }
    }

    private void initCacheFolder() {
        // check parent folder if exists
        if (!cacheFolder.exists()) {
            if (!cacheFolder.mkdirs())
                throw new IllegalStateException(new IOException("Cannot create cache folder."));
        } else {
            if (cacheFolder.isDirectory())
                throw new IllegalStateException(new IOException("Cannot create cache folder, file exists but not a directory."));
        }
    }
}

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
import java.util.Objects;
import java.util.logging.Logger;

public class CrowdinOTA {
    private static final Logger LOG = Logger.getLogger("CrowdinOTA");
    protected final String distributionUrl;
    protected final UnirestInstance unirest;
    protected final File cacheFolder;
    protected JsonObject manifest;
    /**
     * The language mapping
     * CrowdinSyntaxName, Map(Syntax, CustomName)
     */
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
        if (this.distributionUrl.endsWith("/"))
            throw new IllegalArgumentException("Distribution URL should not end with a slash.");
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
     * @param customSyntax      The mapping name (custom name)
     * @return The mapped language code
     */
    @NotNull
    public String mapLanguageCode(@NotNull String crowdinSyntaxCode, @NotNull String customSyntax) {
        Map<String, String> mapping = this.languageMapping.get(crowdinSyntaxCode);
        if (mapping == null) return crowdinSyntaxCode; // Return as-is because no mapping found
        String customSyntaxMapped = mapping.get(customSyntax);
        // Return as-is because no custom mapping found
        return Objects.requireNonNullElse(customSyntaxMapped, crowdinSyntaxCode);
    }

    /**
     * Gets the mapped language code for the given locale.
     * For example:
     * "tr": {
     * "locale": "tr-TR"
     * }
     * In this case, give "tr-TR" for customCode and "locale" for customSyntaxName, "tr" will be returns.
     * If nothing matches, return customCode as-is.
     * It is the mapLanguage's reserve
     *
     * @param customCode   The language code (custom name)
     * @param customSyntax The custom syntax name
     * @return The mapped language code
     */
    @NotNull
    public String mapLanguageCustom(@NotNull String customCode, @NotNull String customSyntax) {
        for (Map.Entry<String, Map<String, String>> entry : this.languageMapping.entrySet()) {
            String customCodeStored = entry.getValue().get(customSyntax);
            if (customCodeStored.equals(customCode)) return entry.getKey();
        }
        return customCode;
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
        LOG.info("Downloading Crowdin distribution manifest from remote server...");
        HttpResponse<String> response = unirest.get(this.distributionUrl + "/manifest.json").asString();
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
            if (!cacheFolder.isDirectory())
                throw new IllegalStateException(new IOException("Cannot create cache folder, file exists but not a directory."));
        }
    }
}

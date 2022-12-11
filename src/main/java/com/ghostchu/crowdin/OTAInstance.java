package com.ghostchu.crowdin;

import com.ghostchu.crowdin.exception.OTAException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import kong.unirest.UnirestInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class OTAInstance {

    private final CrowdinOTA parent;
    private final UnirestInstance unirest;
    private Map<String, OTAFileInstance> fileMapping;

    /**
     * Create a OTAInstance instance from a CrowdinOTA instance.
     *
     * @param parent The parent CrowdinOTA instance.
     * @throws OTAException Throws a OTAException while failed during requesting or processing manifest.
     */
    protected OTAInstance(@NotNull CrowdinOTA parent, @NotNull UnirestInstance unirest) throws OTAException {
        this.parent = parent;
        this.unirest = unirest;
        initFileInstances();
    }

    /**
     * List the available files in this OTA instance.
     *
     * @return The available files in this OTA instance.
     */
    @NotNull
    public Collection<String> listFiles() {
        return new ArrayList<>(fileMapping.keySet());
    }

    /**
     * Gets the parent CrowdinOTA instance.
     *
     * @param fileName The file name.
     * @return The parent CrowdinOTA instance. Null for no instance found.
     */
    @Nullable
    public OTAFileInstance getFileInstance(@NotNull String fileName) {
        return fileMapping.get(fileName);
    }

    /**
     * Creates the OTAFileInstance instances.
     *
     * @throws OTAException Throws a OTAException while failed during requesting or processing manifest.
     */
    private void initFileInstances() throws OTAException {
        JsonElement filesElement = parent.manifest.get("files");
        if (!filesElement.isJsonArray()) {
            throw new OTAException("The manifest field `files` not a array.");
        }
        JsonArray files = filesElement.getAsJsonArray();
        Map<String, OTAFileInstance> stageMap = new LinkedHashMap<>();
        int index = 0;
        for (JsonElement file : files) {
            String fileName = file.getAsString();
            OTAFileInstance fileInstance = new OTAFileInstance(parent, fileName, index, this.unirest);
            stageMap.put(fileName, fileInstance);
            index++;
        }
        this.fileMapping = stageMap;
    }
}

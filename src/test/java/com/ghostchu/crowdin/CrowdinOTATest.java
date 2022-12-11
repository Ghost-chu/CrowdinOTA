package com.ghostchu.crowdin;

import com.ghostchu.crowdin.exception.OTAException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

class CrowdinOTATest {

    static CrowdinOTA instance;

    @BeforeAll
    static void setUp() throws OTAException {
        File file = new File("testCache");
        file.deleteOnExit();
        file.delete();
        instance = new CrowdinOTA("https://distributions.crowdin.net/847569d13d22ee803f1cfa7xrm4",
                file);
    }

    @AfterAll
    static void cleanUp() {
        File file = new File("testCache");
        file.delete();
    }

    @Test
    void getTimestamp() {
        Assertions.assertTrue(instance.getTimestamp() > 0);
    }

    @Test
    void mapLanguageCode() {
        Assertions.assertEquals("aaa", instance.mapLanguageCode("aaa", "locale"));
        Assertions.assertEquals("he-IL", instance.mapLanguageCode("he", "locale"));
        Assertions.assertEquals("zh-CN", instance.mapLanguageCode("zh-CN", "locale"));
        Assertions.assertEquals("uk-UA", instance.mapLanguageCode("uk", "locale"));
    }

    @Test
    void mapLanguageCustom() {
        Assertions.assertEquals("aaa", instance.mapLanguageCustom("aaa", "locale"));
        Assertions.assertEquals("he", instance.mapLanguageCustom("he-IL", "locale"));
        Assertions.assertEquals("zh-CN", instance.mapLanguageCustom("zh-CN", "locale"));
        Assertions.assertEquals("uk", instance.mapLanguageCustom("uk-UA", "locale"));
    }

    @Test
    void translationGetting() {
        String fileContent = """
                example1:
                  example2:
                    test: "This is a test file for testing the REST response of Crowdin distribution feature, please ignore this file!"
                """;
        @SuppressWarnings("DataFlowIssue") String actualContent = instance.getOtaInstance().getFileInstance("/hikari/quickshop-bukkit/src/main/resources/lang/example.yml")
                .getLocaleContentByCustomCode("locale", "zh-CN");
        //System.out.println(actualContent);
        Assertions.assertEquals(actualContent, fileContent);
    }
}
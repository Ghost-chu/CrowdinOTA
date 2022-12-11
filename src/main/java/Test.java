import com.ghostchu.crowdin.CrowdinOTA;
import com.ghostchu.crowdin.OTAFileInstance;
import com.ghostchu.crowdin.exception.OTAException;

import java.io.File;

public class Test {
    public static void main(String[] args) throws OTAException {
        File myCacheFolder = new File("my-cache");
        String distributionUrl = "https://distributions.crowdin.net/847569d13d22ee803f1cfa7xrm4";
        String crowdinFilePath = "/hikari/quickshop-bukkit/src/main/resources/lang/example.yml";
        CrowdinOTA crowdinOTA = new CrowdinOTA(distributionUrl, myCacheFolder);
        // I/O blocking operation, when you create a OTAFileInstance, it will download all translations
        OTAFileInstance fileInstance = crowdinOTA.getOtaInstance().getFileInstance(crowdinFilePath);
        System.out.println(fileInstance.getLocaleContentByCrowdinCode("zh-CN"));
        System.out.println(fileInstance.getLocaleContentByCustomCode("locale", "uk-UA"));
    }
}

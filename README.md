# CrowdinOTA

A vanilla Java library that adapt the Crowdin Over-The-Air Content Delivery with cache and custom language mapping
support.

## Features

* Translation cache management.
* Multiple files support.
* Parallel files downloading.

## Usage

```java
public class Test {
    public static void main(String[] args) throws OTAException {
        File myCacheFolder = new File("my-cache");
        String distributionUrl = "https://distributions.crowdin.net/847569d13d22ee803f1cfa7xrm4";
        String crowdinFilePath = "/hikari/quickshop-bukkit/src/main/resources/lang/example.yml";
        CrowdinOTA crowdinOTA = new CrowdinOTA(distributionUrl, myCacheFolder);
        // I/O blocking operation, when you create a OTAFileInstance, it will download all translations
        OTAFileInstance fileInstance = crowdinOTA.getOtaInstance().getFileInstance(crowdinFilePath, autoDownload);
        // Now get your translations!
        System.out.println(fileInstance.getLocaleContentByCrowdinCode("zh-CN"));
        System.out.println(fileInstance.getLocaleContentByCustomCode("locale", "uk-UA"));
    }
}
```

## Caching

CrowdinOTA will cache all translations in your cache folder, and it will check the cache timestamp, if the cache file is
expired, it will download the new translation file from Crowdin.

CrowdinOTA also will store the cache file's SHA-1 for validating.

## Maven

We're on Maven Central.

```xml

<dependency>
    <groupId>com.ghostchu.crowdin</groupId>
    <artifactId>CrowdinOTA</artifactId>
    <version>1.0</version>
</dependency>
```
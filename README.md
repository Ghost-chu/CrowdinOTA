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
        OTAFileInstance fileInstance = crowdinOTA.getOtaInstance().getFileInstance(crowdinFilePath);
        // Now get your translations!
        System.out.println(fileInstance.getLocaleContentByCrowdinCode("zh-CN"));
        System.out.println(fileInstance.getLocaleContentByCustomCode("locale", "uk-UA"));
        // Or get all available translations!
        fileInstance.getAvailableLocales();
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
    <artifactId>crowdinota</artifactId>
    <version>1.0.3</version>
</dependency>
```

## Example Project

[QuickShop-Hikari](https://github.com/Ghost-chu/QuickShop-Hikari/blob/5690651eecfd877e442a7a8f958a6ef896e8edb1/quickshop-bukkit/src/main/java/com/ghostchu/quickshop/localization/text/SimpleTextManager.java#L109) using CrowdinOTA to process the Crowdin distribution.

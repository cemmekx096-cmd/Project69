Struktur Folder/File
Dicetak pada: 2026-01-29 20:03:42
Direktori: /data/data/com.termux/files/home/project69
```
============================================================

├── buildSrc/
│   ├── gradle/
│   │   └── wrapper/
│   │       └── gradle-wrapper.properties
│   ├── src/
│   │   └── main/
│   │       └── kotlin/
│   │           ├── AndroidConfig.kt
│   │           ├── Extensions.kt
│   │           ├── lib-android.gradle.kts
│   │           ├── lib-kotlin.gradle.kts
│   │           └── lib-multisrc.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── core/
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/
│   │       │   └── extensions/
│   │       │       └── utils/
│   │       │           ├── Collections.kt
│   │       │           ├── Date.kt
│   │       │           ├── Json.kt
│   │       │           ├── Network.kt
│   │       │           ├── Preferences.kt
│   │       │           ├── Source.kt
│   │       │           └── Url.kt
│   │       └── res/
│   │           ├── mipmap-hdpi/
│   │           │   └── ic_launcher.png
│   │           ├── mipmap-mdpi/
│   │           │   └── ic_launcher.png
│   │           ├── mipmap-xhdpi/
│   │           │   └── ic_launcher.png
│   │           ├── mipmap-xxhdpi/
│   │           │   └── ic_launcher.png
│   │           └── mipmap-xxxhdpi/
│   │               └── ic_launcher.png
│   ├── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── lib/
│   ├── amazon-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── amazonextractor/
│   │   │                               └── AmazonExtractor.kt
│   │   └── build.gradle.kts
│   ├── anichin-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── anichinextractor/
│   │   │                               └── AnichinExtractor.kt
│   │   └── build.gradle.kts
│   ├── bangumi-scraper/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── bangumiscraper/
│   │   │                               ├── BangumiDTO.kt
│   │   │                               ├── BangumiScraper.kt
│   │   │                               └── BangumiScraperException.kt
│   │   └── build.gradle.kts
│   ├── blogger-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── bloggerextractor/
│   │   │                               └── BloggerExtractor.kt
│   │   └── build.gradle.kts
│   ├── burstcloud-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── burstcloudextractor/
│   │   │                               ├── BurstCloudExtractor.kt
│   │   │                               └── BurstCloudExtractorDto.kt
│   │   └── build.gradle.kts
│   ├── buzzheavier-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── buzzheavierextractor/
│   │   │                               └── BuzzheavierExtractor.kt
│   │   └── build.gradle.kts
│   ├── cda-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── cdaextractor/
│   │   │                               └── CdaExtractor.kt
│   │   └── build.gradle.kts
│   ├── chillx-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── chillxextractor/
│   │   │                               ├── ChillxExtractor.kt
│   │   │                               └── WebViewResolver.kt
│   │   └── build.gradle.kts
│   ├── cloudflare-interceptor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── cloudflareinterceptor/
│   │   │                               └── CloudflareInterceptor.kt
│   │   └── build.gradle.kts
│   ├── cryptoaes/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── cryptoaes/
│   │   │                               ├── CryptoAES.kt
│   │   │                               └── Deobfuscator.kt
│   │   └── build.gradle.kts
│   ├── dailymotion-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── dailymotionextractor/
│   │   │                               ├── DailymotionDto.kt
│   │   │                               └── DailymotionExtractor.kt
│   │   └── build.gradle.kts
│   ├── dataimage/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── dataimage/
│   │   │                               └── DataImageInterceptor.kt
│   │   └── build.gradle.kts
│   ├── dood-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── doodextractor/
│   │   │                               └── DoodExtractor.kt
│   │   └── build.gradle.kts
│   ├── doodstream-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── doodstreamextractor/
│   │   │                               └── DoodstreamExtractor.kt
│   │   └── build.gradle.kts
│   ├── dopeflix-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── dopeflixextractor/
│   │   │                               └── DopeFlixExtractor.kt
│   │   └── build.gradle.kts
│   ├── fastream-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── fastreamextractor/
│   │   │                               └── FastreamExtractor.kt
│   │   └── build.gradle.kts
│   ├── filemoon-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── filemoonextractor/
│   │   │                               └── FilemoonExtractor.kt
│   │   └── build.gradle.kts
│   ├── fireplayer-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── fireplayerextractor/
│   │   │                               └── FireplayerExtractor.kt
│   │   └── build.gradle.kts
│   ├── fusevideo-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── fusevideoextractor/
│   │   │                               └── FusevideoExtractor.kt
│   │   └── build.gradle.kts
│   ├── gdriveplayer-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── gdriveplayerextractor/
│   │   │                               └── GdrivePlayerExtractor.kt
│   │   └── build.gradle.kts
│   ├── gogostream-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── gogostreamextractor/
│   │   │                               ├── GogoStreamExtractor.kt
│   │   │                               └── GogoStreamExtractorDto.kt
│   │   └── build.gradle.kts
│   ├── goodstream-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── goodstramextractor/
│   │   │                               └── GoodStreamExtractor.kt
│   │   └── build.gradle.kts
│   ├── googledrive-episodes/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── googledriveepisodes/
│   │   │                               └── GoogleDriveEpisodes.kt
│   │   └── build.gradle.kts
│   ├── googledrive-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── googledriveextractor/
│   │   │                               └── GoogleDriveExtractor.kt
│   │   └── build.gradle.kts
│   ├── i18n/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── i18n/
│   │   │                               └── Intl.kt
│   │   └── build.gradle.kts
│   ├── javcoverfetcher/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── javcoverfetcher/
│   │   │                               └── JavCoverFetcher.kt
│   │   └── build.gradle.kts
│   ├── lulu-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── luluextractor/
│   │   │                               └── LuluExtractor.kt
│   │   └── build.gradle.kts
│   ├── lycoris-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── lycorisextractor/
│   │   │                               └── LycorisExtractor.kt
│   │   └── build.gradle.kts
│   ├── megacloud-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── megacloudextractor/
│   │   │                               └── MegaCloudExtractor.kt
│   │   └── build.gradle.kts
│   ├── megamax-multiserver/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── megamaxmultiserver/
│   │   │                               ├── dto/
│   │   │                               │   ├── IframeDto.kt
│   │   │                               │   └── LeechDto.kt
│   │   │                               └── MegaMaxMultiServer.kt
│   │   └── build.gradle.kts
│   ├── mixdrop-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── mixdropextractor/
│   │   │                               └── MixDropExtractor.kt
│   │   └── build.gradle.kts
│   ├── mp4upload-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── mp4uploadextractor/
│   │   │                               └── Mp4uploadExtractor.kt
│   │   └── build.gradle.kts
│   ├── okru-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── okruextractor/
│   │   │                               └── OkruExtractor.kt
│   │   └── build.gradle.kts
│   ├── playlist-utils/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── playlistutils/
│   │   │                               └── PlaylistUtils.kt
│   │   └── build.gradle.kts
│   ├── rapidcloud-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── assets/
│   │   │       │   ├── crypto-js.js
│   │   │       │   ├── megacloud.decodedpng.js
│   │   │       │   └── megacloud.getsrcs.js
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── rapidcloudextractor/
│   │   │                               ├── RapidCloudExtractor.kt
│   │   │                               └── WebViewResolver.kt
│   │   └── build.gradle.kts
│   ├── rubyvidhub-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── rubyvidhubextractor/
│   │   │                               ├── JsUnpacker.kt
│   │   │                               ├── RubyVidHubDto.kt
│   │   │                               └── RubyVidHubExtractor.kt
│   │   └── build.gradle.kts
│   ├── rumble-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── rumbleextractor/
│   │   │                               └── RumbleExtractor.kt
│   │   └── build.gradle.kts
│   ├── savefile-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── savefileextractor/
│   │   │                               └── SavefileExtractor.kt
│   │   └── build.gradle.kts
│   ├── sendvid-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── sendvidextractor/
│   │   │                               └── SendvidExtractor.kt
│   │   └── build.gradle.kts
│   ├── sibnet-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── sibnetextractor/
│   │   │                               └── SibnetExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamdav-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamdavextractor/
│   │   │                               └── StreamDavExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamhidevid-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamhidevidextractor/
│   │   │                               └── StreamHideVidExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamhub-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamhubextractor/
│   │   │                               └── StreamHubExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamlare-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamlareextractor/
│   │   │                               └── StreamlareExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamsilk-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamsilkextractor/
│   │   │                               ├── JsHunter.kt
│   │   │                               └── StreamSilkExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamtape-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamtapeextractor/
│   │   │                               └── StreamTapeExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamvid-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamvidextractor/
│   │   │                               └── StreamVidExtractor.kt
│   │   └── build.gradle.kts
│   ├── streamwish-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── streamwishextractor/
│   │   │                               └── StreamWishExtractor.kt
│   │   └── build.gradle.kts
│   ├── synchrony/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── assets/
│   │   │       │   └── synchrony-v2.4.5.1.js
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── synchrony/
│   │   │                               └── Deobfuscator.kt
│   │   └── build.gradle.kts
│   ├── universal-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── universalextractor/
│   │   │                               └── UniversalExtractor.kt
│   │   └── build.gradle.kts
│   ├── unpacker/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── unpacker/
│   │   │                               ├── SubstringExtractor.kt
│   │   │                               └── Unpacker.kt
│   │   └── build.gradle.kts
│   ├── upstream-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── upstreamextractor/
│   │   │                               └── UpstreamExtractor.kt
│   │   └── build.gradle.kts
│   ├── uqload-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── uqloadextractor/
│   │   │                               └── UqloadExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidbom-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidbomextractor/
│   │   │                               └── VidBomExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidguard-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidguardextractor/
│   │   │                               └── VidGuardExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidhide-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidhideextractor/
│   │   │                               ├── JsUnpacker.kt
│   │   │                               └── VidHideExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidland-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidlandextractor/
│   │   │                               └── VidLandExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidmoly-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidmolyextractor/
│   │   │                               └── VidMolyExtractor.kt
│   │   └── build.gradle.kts
│   ├── vido-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidoextractor/
│   │   │                               └── VidoExtractor.kt
│   │   └── build.gradle.kts
│   ├── vidsrc-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vidsrcextractor/
│   │   │                               └── VidSrcExtractor.kt
│   │   └── build.gradle.kts
│   ├── vk-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vkextractor/
│   │   │                               └── VkExtractor.kt
│   │   └── build.gradle.kts
│   ├── voe-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── voeextractor/
│   │   │                               ├── DdosGuardInterceptor.kt
│   │   │                               └── VoeExtractor.kt
│   │   └── build.gradle.kts
│   ├── vudeo-extractor/
│   │   ├── src/
│   │   │   └── main/
│   │   │       └── java/
│   │   │           └── eu/
│   │   │               └── kanade/
│   │   │                   └── tachiyomi/
│   │   │                       └── lib/
│   │   │                           └── vudeoextractor/
│   │   │                               └── VudeoExtractor.kt
│   │   └── build.gradle.kts
│   └── yourupload-extractor/
│       ├── src/
│       │   └── main/
│       │       └── java/
│       │           └── eu/
│       │               └── kanade/
│       │                   └── tachiyomi/
│       │                       └── lib/
│       │                           └── youruploadextractor/
│       │                               └── YourUploadExtractor.kt
│       └── build.gradle.kts
├── lib-multisrc/
│   ├── anilist/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── anilist/
│   │   │                       ├── AniListAnimeHttpSource.kt
│   │   │                       ├── AniListFilters.kt
│   │   │                       ├── AniListRequest.kt
│   │   │                       └── AniListResponse.kt
│   │   └── build.gradle.kts
│   ├── animestream/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── animestream/
│   │   │                       ├── AnimeStream.kt
│   │   │                       ├── AnimeStreamFilters.kt
│   │   │                       └── AnimeStreamUrlActivity.kt
│   │   ├── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── datalifeengine/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── datalifeengine/
│   │   │                       └── DataLifeEngine.kt
│   │   └── build.gradle.kts
│   ├── dooplay/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── dooplay/
│   │   │                       ├── DooPlay.kt
│   │   │                       └── DooPlayUrlActivity.kt
│   │   ├── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── dopeflix/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── dopeflix/
│   │   │                       ├── dto/
│   │   │                       │   └── DopeFlixDto.kt
│   │   │                       ├── DopeFlix.kt
│   │   │                       └── DopeFlixFilters.kt
│   │   └── build.gradle.kts
│   ├── sudatchi/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── sudatchi/
│   │   │                       ├── dto/
│   │   │                       │   └── SudatchiDto.kt
│   │   │                       ├── Sudatchi.kt
│   │   │                       ├── SudatchiFilters.kt
│   │   │                       └── SudatchiUrlActivity.kt
│   │   └── build.gradle.kts
│   ├── wcotheme/
│   │   ├── src/
│   │   │   └── eu/
│   │   │       └── kanade/
│   │   │           └── tachiyomi/
│   │   │               └── multisrc/
│   │   │                   └── wcotheme/
│   │   │                       ├── Filters.kt
│   │   │                       └── WcoTheme.kt
│   │   └── build.gradle.kts
│   └── zorotheme/
│       ├── src/
│       │   └── eu/
│       │       └── kanade/
│       │           └── tachiyomi/
│       │               └── multisrc/
│       │                   └── zorotheme/
│       │                       ├── dto/
│       │                       │   └── ZoroThemeDto.kt
│       │                       ├── ZoroTheme.kt
│       │                       └── ZoroThemeFilters.kt
│       └── build.gradle.kts
├── src/
│   ├── all/
│   │   ├── animeonsen/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── animeonsen/
│   │   │   │                           ├── dto/
│   │   │   │                           │   └── AnimeOnsenDto.kt
│   │   │   │                           ├── AnimeOnsen.kt
│   │   │   │                           └── AOAPIInterceptor.kt
│   │   │   └── build.gradle
│   │   ├── animeworldindia/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── animeworldindia/
│   │   │   │                           ├── AnimeWorldIndia.kt
│   │   │   │                           ├── AnimeWorldIndiaFactory.kt
│   │   │   │                           ├── AnimeWorldIndiaFilters.kt
│   │   │   │                           └── MyStreamExtractor.kt
│   │   │   └── build.gradle
│   │   ├── animexin/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── animexin/
│   │   │   │                           ├── extractors/
│   │   │   │                           │   ├── VidstreamingExtractor.kt
│   │   │   │                           │   └── YouTubeExtractor.kt
│   │   │   │                           └── AnimeXin.kt
│   │   │   └── build.gradle
│   │   ├── anizone/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── anizone/
│   │   │   │                           ├── AniZone.kt
│   │   │   │                           └── LivewireDto.kt
│   │   │   └── build.gradle
│   │   ├── chineseanime/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── chineseanime/
│   │   │   │                           ├── extractors/
│   │   │   │                           │   └── VatchusExtractor.kt
│   │   │   │                           └── ChineseAnime.kt
│   │   │   └── build.gradle
│   │   ├── debridindex/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── debridindex/
│   │   │   │                           ├── dto/
│   │   │   │                           │   └── DebridIndexDto.kt
│   │   │   │                           ├── DebirdIndexUrlActivity.kt
│   │   │   │                           └── DebridIndex.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── googledrive/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── googledrive/
│   │   │   │                           ├── GoogleDrive.kt
│   │   │   │                           ├── GoogleDriveDto.kt
│   │   │   │                           └── GoogleDriveMultiFormReqs.kt
│   │   │   ├── build.gradle
│   │   │   └── README.md
│   │   ├── googledriveindex/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── googledriveindex/
│   │   │   │                           ├── GoogleDriveIndex.kt
│   │   │   │                           └── GoogleDriveIndexDto.kt
│   │   │   └── build.gradle
│   │   ├── hentaitorrent/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── hentaitorrent/
│   │   │   │                           ├── HentaiTorrent.kt
│   │   │   │                           └── HentaiTorrentUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── jable/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── jable/
│   │   │   │                           ├── Jable.kt
│   │   │   │                           ├── JableFactory.kt
│   │   │   │                           ├── JableFilters.kt
│   │   │   │                           └── JableIntl.kt
│   │   │   └── build.gradle
│   │   ├── javgg/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── javgg/
│   │   │   │                           └── Javgg.kt
│   │   │   └── build.gradle
│   │   ├── javguru/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── javguru/
│   │   │   │                           ├── extractors/
│   │   │   │                           │   ├── EmTurboExtractor.kt
│   │   │   │                           │   └── MaxStreamExtractor.kt
│   │   │   │                           ├── JavGuru.kt
│   │   │   │                           ├── JavGuruFilters.kt
│   │   │   │                           └── JavGuruUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── jellyfin/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── jellyfin/
│   │   │   │                           ├── dto/
│   │   │   │                           │   ├── ItemDto.kt
│   │   │   │                           │   ├── ItemType.kt
│   │   │   │                           │   ├── LoginDto.kt
│   │   │   │                           │   ├── MediaLibraryDto.kt
│   │   │   │                           │   └── PlaybackInfoDto.kt
│   │   │   │                           ├── Jellyfin.kt
│   │   │   │                           ├── JellyfinFactory.kt
│   │   │   │                           └── Utils.kt
│   │   │   └── build.gradle
│   │   ├── lmanime/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── lmanime/
│   │   │   │                           └── LMAnime.kt
│   │   │   └── build.gradle
│   │   ├── missav/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── missav/
│   │   │   │                           ├── MissAV.kt
│   │   │   │                           ├── MissAvApi.kt
│   │   │   │                           ├── MissAvDtoModels.kt
│   │   │   │                           └── MissAVFilters.kt
│   │   │   └── build.gradle
│   │   ├── myreadingmanga/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── myreadingmanga/
│   │   │   │                           ├── MyReadingManga.kt
│   │   │   │                           └── MyReadingMangaFactory.kt
│   │   │   └── build.gradle
│   │   ├── newgrounds/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── newgrounds/
│   │   │   │                           ├── DateUtils.kt
│   │   │   │                           ├── NewGrounds.kt
│   │   │   │                           └── NewGroundsFilters.kt
│   │   │   └── build.gradle
│   │   ├── nyaatorrent/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── nyaatorrent/
│   │   │   │                           ├── NyaaFactory.kt
│   │   │   │                           ├── NyaaTorrent.kt
│   │   │   │                           └── NyaaTorrentUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── ptorrent/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── ptorrent/
│   │   │   │                           ├── PTorrent.kt
│   │   │   │                           └── PTorrentUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── rouvideo/
│   │   │   ├── assets/
│   │   │   │   └── i18n/
│   │   │   │       ├── messages_en.properties
│   │   │   │       ├── messages_vi.properties
│   │   │   │       └── messages_zh.properties
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── rouvideo/
│   │   │   │                           ├── RouVideo.kt
│   │   │   │                           ├── RouVideoDto.kt
│   │   │   │                           ├── RouVideoFactory.kt
│   │   │   │                           ├── RouVideoFilter.kt
│   │   │   │                           └── RouVideoUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── shabakatycinemana/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── shabakatycinemana/
│   │   │   │                           └── ShabakatyCinemana.kt
│   │   │   └── build.gradle
│   │   ├── streamingcommunity/
│   │   │   ├── assets/
│   │   │   │   └── i18n/
│   │   │   │       ├── messages_en.properties
│   │   │   │       └── messages_it.properties
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── streamingcommunity/
│   │   │   │                           ├── Dto.kt
│   │   │   │                           ├── Filters.kt
│   │   │   │                           ├── StreamingCommunity.kt
│   │   │   │                           └── StreamingCommunityFactory.kt
│   │   │   └── build.gradle
│   │   ├── stremio/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── stremio/
│   │   │   │                           ├── addon/
│   │   │   │                           │   ├── dto/
│   │   │   │                           │   │   ├── AddonDto.kt
│   │   │   │                           │   │   ├── CatalogDto.kt
│   │   │   │                           │   │   └── ResourceDto.kt
│   │   │   │                           │   └── AddonManager.kt
│   │   │   │                           ├── Dto.kt
│   │   │   │                           ├── Stremio.kt
│   │   │   │                           └── Utils.kt
│   │   │   ├── build.gradle
│   │   │   └── README.md
│   │   ├── subsplease/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── subsplease/
│   │   │   │                           └── Subsplease.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── sudatchi/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── sudatchi/
│   │   │   │                           ├── Sudatchi.kt
│   │   │   │                           └── SudatchiUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── sudatchinsfw/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── sudatchinsfw/
│   │   │   │                           ├── Sudatchi.kt
│   │   │   │                           └── SudatchiUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── supjav/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── supjav/
│   │   │   │                           ├── SupJav.kt
│   │   │   │                           ├── SupJavFactory.kt
│   │   │   │                           └── SupJavUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── torrentio/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── torrentio/
│   │   │   │                           ├── dto/
│   │   │   │                           │   └── TorrentioDto.kt
│   │   │   │                           ├── Torrentio.kt
│   │   │   │                           └── TorrentioUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── torrentioanime/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── torrentioanime/
│   │   │   │                           ├── dto/
│   │   │   │                           │   ├── AniZipDto.kt
│   │   │   │                           │   └── TorrentioDto.kt
│   │   │   │                           ├── AniListFilters.kt
│   │   │   │                           ├── AniListQueries.kt
│   │   │   │                           ├── Torrentio.kt
│   │   │   │                           └── TorrentioUrlActivity.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   ├── xnxx/
│   │   │   ├── res/
│   │   │   │   ├── mipmap-hdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-mdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   ├── mipmap-xxhdpi/
│   │   │   │   │   └── ic_launcher.png
│   │   │   │   └── mipmap-xxxhdpi/
│   │   │   │       └── ic_launcher.png
│   │   │   ├── src/
│   │   │   │   └── eu/
│   │   │   │       └── kanade/
│   │   │   │           └── tachiyomi/
│   │   │   │               └── animeextension/
│   │   │   │                   └── all/
│   │   │   │                       └── xnxx/
│   │   │   │                           └── Xnxx.kt
│   │   │   ├── AndroidManifest.xml
│   │   │   └── build.gradle
│   │   └── xvideos/
│   │       ├── res/
│   │       │   ├── mipmap-hdpi/
│   │       │   │   └── ic_launcher.jpg
│   │       │   ├── mipmap-mdpi/
│   │       │   │   └── ic_launcher.jpg
│   │       │   ├── mipmap-xhdpi/
│   │       │   │   └── ic_launcher.jpg
│   │       │   ├── mipmap-xxhdpi/
│   │       │   │   └── ic_launcher.jpg
│   │       │   └── mipmap-xxxhdpi/
│   │       │       └── ic_launcher.jpg
│   │       ├── src/
│   │       │   └── eu/
│   │       │       └── kanade/
│   │       │           └── tachiyomi/
│   │       │               └── animeextension/
│   │       │                   └── all/
│   │       │                       └── xvideos/
│   │       │                           └── Xvideos.kt
│   │       ├── AndroidManifest.xml
│   │       └── build.gradle
│   └── id/
│       ├── anichin/
│       │   ├── res/
│       │   │   ├── mipmap-hdpi/
│       │   │   │   └── ic_launcher.png
│       │   │   ├── mipmap-mdpi/
│       │   │   │   └── ic_launcher.png
│       │   │   ├── mipmap-xhdpi/
│       │   │   │   └── ic_launcher.png
│       │   │   ├── mipmap-xxhdpi/
│       │   │   │   └── ic_launcher.png
│       │   │   └── mipmap-xxxhdpi/
│       │   │       └── ic_launcher.png
│       │   ├── src/
│       │   │   └── eu/
│       │   │       └── kanade/
│       │   │           └── tachiyomi/
│       │   │               └── animeextension/
│       │   │                   └── id/
│       │   │                       └── anichin/
│       │   │                           ├── extractors/
│       │   │                           │   └── UniversalBase64Extractor.kt
│       │   │                           ├── Anichin.kt
│       │   │                           ├── AnichinFactory.kt
│       │   │                           └── AnichinFilters.kt
│       │   └── build.gradle
│       └── otakudesu/
│           ├── res/
│           │   ├── mipmap-hdpi/
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-mdpi/
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-xhdpi/
│           │   │   └── ic_launcher.png
│           │   ├── mipmap-xxhdpi/
│           │   │   └── ic_launcher.png
│           │   └── mipmap-xxxhdpi/
│           │       └── ic_launcher.png
│           ├── src/
│           │   └── eu/
│           │       └── kanade/
│           │           └── tachiyomi/
│           │               └── animeextension/
│           │                   └── id/
│           │                       └── otakudesu/
│           │                           └── OtakuDesu.kt
│           └── build.gradle
├── struktur/
├── 2.py
├── build.gradle.kts
├── common.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── ktlintCodeStyle.xml
├── lib_structure.md
├── README.md
├── settings.gradle.kts
└── tree_structure.txt

============================================================
```
Total file yang di-scan: 533
File output: /data/data/com.termux/files/home/project69/tree_structure.txt

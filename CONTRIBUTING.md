# Contributing to Apk-project69

Panduan untuk berkontribusi pada repository **Apk-project69** - Aniyomi Video Extensions (Sub Indonesia).

## 📋 Prerequisites (Diperlukan)

Sebelum mulai, pastikan kamu memiliki pengetahuan dasar tentang:

- [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)
- Web scraping:
  - [HTML](https://developer.mozilla.org/en-US/docs/Web/HTML)
  - [CSS selectors](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Selectors)
  - [OkHttp](https://square.github.io/okhttp/)
  - [JSoup](https://jsoup.org/)

## 🛠️ Tools yang Diperlukan

- [Android Studio](https://developer.android.com/studio) (latest)
- Emulator atau HP dengan developer mode enabled + Aniyomi terbaru
- [Icon Generator](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html)
- Git (latest version)

## 🚀 Setup Repository

```bash
# Clone repository
git clone https://github.com/cemmekx096-cmd/Apk-project69.git
cd Apk-project69

# (Optional) Setup sparse checkout untuk clone lebih cepat
git sparse-checkout set --cone --sparse-index
git sparse-checkout add buildSrc core gradle lib src
```

---

## 📝 Membuat Extension Baru

### 1️⃣ Folder Structure

Buat folder extension baru di `src/id/[extension-name]/`:

```
src/id/myextension/
├── build.gradle                                    # Config file
├── res/                                           # Resources (icons)
│   ├── mipmap-hdpi/ic_launcher.png
│   ├── mipmap-mdpi/ic_launcher.png
│   ├── mipmap-xhdpi/ic_launcher.png
│   ├── mipmap-xxhdpi/ic_launcher.png
│   └── mipmap-xxxhdpi/ic_launcher.png
└── src/main/kotlin/eu/kanade/tachiyomi/animeextension/id/myextension/
    ├── MyExtension.kt                            # Main class (required)
    ├── MyExtensionFactory.kt                     # Factory (if needed)
    ├── MyExtensionFilters.kt                     # Filters (optional)
    └── extractors/                               # Helper classes (optional)
        └── CustomExtractor.kt
```

**Naming Convention:**
- Folder: lowercase, no spaces → `myextension`
- Package: `eu.kanade.tachiyomi.animeextension.id.myextension`
- Class: CapitalizedCamelCase → `MyExtension`

### 2️⃣ build.gradle

```gradle
ext {
    extName = 'My Extension Name'           // Display name
    extClass = '.MyExtension'               // Main class (relative path)
    extVersionCode = 1                      // Version code (increment on changes)
    isNsfw = false                          // Set true if adult content
}

apply from: "$rootDir/common.gradle"

dependencies {
    // Add required extractors (lihat list di struktur.md)
    compileOnly project(':lib:rapidcloud-extractor')
    compileOnly project(':lib:streamtape-extractor')
}
```

### 3️⃣ Main Extension Class

**MyExtension.kt:**
```kotlin
package eu.kanade.tachiyomi.animeextension.id.myextension

import eu.kanade.tachiyomi.animesource.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

class MyExtension : ParsedAnimeHttpSource() {
    override val name = "My Extension"
    override val baseUrl = "https://example.com"
    override val lang = "id"
    
    // CSS selectors untuk parsing HTML
    override fun popularAnimeSelector(): String = "div.anime-item"
    override fun searchAnimeSelector(): String = "div.anime-result"
    override fun episodeListSelector(): String = "div.episode"
    override fun videoListSelector(): String = "source"
    
    // URL untuk fetch popular anime
    override fun popularAnimeRequest(page: Int) = GET(baseUrl, headers)
    override fun popularAnimeParse(response: Response): AnimesPage {
        val animes = response.use { res ->
            res.document.select(popularAnimeSelector()).map { element ->
                SAnime.create().apply {
                    setUrlWithoutDomain(element.selectFirst("a")?.attr("href") ?: return@apply)
                    title = element.selectFirst("h2")?.text() ?: ""
                    thumbnail_url = element.selectFirst("img")?.attr("src")
                }
            }
        }
        return AnimesPage(animes, hasNextPage = false)
    }
    
    // Implement other required methods...
    override fun searchAnimeRequest(page: Int, query: String, filters: FilterList) = GET(baseUrl, headers)
    override fun searchAnimeParse(response: Response) = AnimesPage(emptyList(), false)
    
    override fun animeDetailsParse(document: Document) = SAnime.create()
    override fun episodeListParse(response: Response) = emptyList<SEpisode>()
    override fun videoListParse(response: Response) = emptyList<Video>()
}
```

---

## 🎞️ Available Extractors

Repository ini memiliki **70+ video extractors** di folder `lib/`. Gunakan yang sesuai dengan kebutuhan:

**Video Hosting yang Umum:**
```gradle
compileOnly project(':lib:rapidcloud-extractor')      // Megacloud/Rapid Cloud
compileOnly project(':lib:streamtape-extractor')      // StreamTape
compileOnly project(':lib:dood-extractor')            // Dood
compileOnly project(':lib:streamlare-extractor')      // Streamlare
compileOnly project(':lib:voe-extractor')             // VoE
compileOnly project(':lib:vidsrc-extractor')          // VidSrc
```

**Special Extractors:**
```gradle
compileOnly project(':lib:cloudflare-interceptor')    // Bypass Cloudflare
compileOnly project(':lib:cryptoaes')                 // AES decryption
compileOnly project(':lib:unpacker')                  // JS unpacking
```

Lihat `struktur.md` untuk list lengkap (70+ extractors).

---

## 🏗️ Build & Test

### Build Extension:
```bash
# Build semua extension
./gradlew build

# Build extension spesifik
./gradlew :src:id:myextension:build

# Clean build
./gradlew :src:id:myextension:clean :src:id:myextension:build
```

### Output APK:
```
src/id/myextension/build/outputs/apk/release/myextension-release.apk
```

### Test di Aniyomi:
1. Build APK
2. Install di emulator/phone: `adb install myextension-release.apk`
3. Buka Aniyomi → Browse → Pilih extension
4. Test browse, search, episode, video playback

---

## 🐛 Debugging

### Method 1: Android Studio Debugger
1. Set breakpoints di code
2. Build & run dengan Debug mode
3. Attach debugger ke Aniyomi process
4. Step through code

### Method 2: Logcat
```bash
# View all logs
adb logcat

# Filter by tag
adb logcat | grep OkHttpClient

# Check errors
adb logcat | grep "ERROR\|Exception"
```

### Method 3: Network Inspection
Aniyomi preview memiliki verbose logging. Buka:
`More → Settings → Advanced → Verbose logging`

---

## 📤 Submit Changes

### Pre-Submission Checklist:

- [ ] Icons sudah di-set (5 ukuran: hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi)
- [ ] Extension compile tanpa error: `./gradlew build`
- [ ] Test di emulator/phone dengan Aniyomi
- [ ] Extension menampilkan anime dengan benar
- [ ] Video playback berfungsi
- [ ] Update `extVersionCode` di `build.gradle`
- [ ] No hardcoded URLs atau sensitive data
- [ ] Update `list.md` dengan extension baru

### Submit Pull Request:

1. **Create branch baru:**
   ```bash
   git checkout -b add/my-extension
   ```

2. **Commit changes:**
   ```bash
   git add src/id/myextension/
   git commit -m "Add MyExtension sub Indonesia"
   ```

3. **Push ke fork:**
   ```bash
   git push origin add/my-extension
   ```

4. **Open Pull Request** di GitHub:
   - Title: `Add MyExtension (Sub Indonesia)`
   - Description: Jelaskan apa yang extension lakukan
   - Reference issues jika ada

5. **Wait for review** - Maintainer akan review dan merge jika oke

---

## 📚 Resources & Referensi

- **Struktur lengkap:** Lihat `struktur.md`
- **Daftar extension:** Lihat `list.md`
- **Original repository:** [yuzono/aniyomi-extensions](https://github.com/yuzono/aniyomi-extensions)
- **Aniyomi docs:** [github.com/aniyomiorg/aniyomi](https://github.com/aniyomiorg/aniyomi)
- **Code examples:** Check existing extensions di `src/id/`

---

## ⚠️ Common Issues

**Extension tidak muncul:**
- Check `extClass` di `build.gradle` (harus sesuai dengan class name)
- Check package name (harus `eu.kanade.tachiyomi.animeextension.id.xxx`)
- Rebuild project

**Video tidak bisa diplay:**
- Check extractor yang digunakan sudah di-add di `build.gradle`
- Verify `videoListParse()` return list dengan `videoUrl`
- Check network di logcat

**Compile error:**
- Update Android Studio ke versi latest
- Clean build: `./gradlew clean build`
- Invalidate cache: `File → Invalidate Caches`

---

## 📞 Getting Help

- Read existing extensions di `src/id/` untuk contoh
- Check `struktur.md` untuk penjelasan folder structure
- Ask in GitHub issues (describe problema dengan detail)
- Reference existing sources untuk best practices

---

**Happy coding! 🚀**

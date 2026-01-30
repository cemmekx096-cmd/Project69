# 📁 Struktur Repository Apk-project69

Dokumentasi lengkap struktur folder dan file dalam repository **Apk-project69** untuk Aniyomi Video Extensions.

---

## 🏗️ Overview Struktur

```
project69/
├── buildSrc/                    # Gradle build scripts & extensions
│   ├── gradle/wrapper/
│   ├── src/main/kotlin/
│   │   ├── AndroidConfig.kt
│   │   ├── Extensions.kt
│   │   ├── lib-android.gradle.kts
│   │   ├── lib-kotlin.gradle.kts
│   │   └── lib-multisrc.gradle.kts
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── core/                        # Core utilities & shared code
│   ├── src/main/kotlin/extensions/utils/
│   │   ├── Collections.kt
│   │   ├── Date.kt
│   │   ├── Json.kt
│   │   ├── Network.kt
│   │   ├── Preferences.kt
│   │   ├── Source.kt
│   │   └── Url.kt
│   ├── src/main/res/           # Resources (icons, etc)
│   ├── AndroidManifest.xml
│   └── build.gradle.kts
│
├── gradle/                      # Gradle wrapper & dependencies
│   ├── wrapper/
│   └── libs.versions.toml       # Dependency versions
│
├── lib/                         # Library extractors (70+)
│   ├── amazon-extractor/
│   ├── anichin-extractor/
│   ├── bangumi-scraper/
│   ├── blogger-extractor/
│   ├── cloudflare-interceptor/
│   ├── cryptoaes/
│   ├── dood-extractor/
│   ├── rapidcloud-extractor/
│   ├── streamtape-extractor/
│   ├── voe-extractor/
│   └── ... (50+ extractors lainnya)
│
├── lib-multisrc/                # Multi-source shared implementations
│   ├── anilist/
│   ├── animestream/
│   ├── dooplay/
│   ├── dopeflix/
│   ├── sudatchi/
│   ├── wcotheme/
│   └── zorotheme/
│
├── src/                         # Source extensions
│   ├── all/                     # General/international extensions
│   │   ├── jable/               # JAV content
│   │   ├── javgg/               # JAV content
│   │   ├── javguru/             # JAV content
│   │   ├── missav/              # JAV content
│   │   ├── rouvideo/            # Multi-language streaming
│   │   ├── xnxx/                # Adult content
│   │   └── xvideos/             # Adult content
│   │
│   └── id/                      # Indonesian extensions (Sub Indonesia)
│       ├── anichin/             # Anime streaming (ID)
│       └── otakudesu/           # Anime streaming (ID)
│
├── struktur/
│   └── README.md                # Dokumentasi struktur internal
│
├── .gitignore
├── README.md                    # Dokumentasi utama
├── list.md                      # Daftar lengkap extension
├── build.gradle.kts
├── gradle.properties
├── gradlew & gradlew.bat        # Gradle wrapper scripts
├── ktlintCodeStyle.xml
├── settings.gradle.kts
└── structure.txt
```

---

## 📂 Penjelasan Detail Setiap Folder

### **buildSrc/**
Build configuration dan gradle plugins.
- **gradle/wrapper/** - Gradle version management
- **src/main/kotlin/** - Shared gradle task definitions
  - `lib-android.gradle.kts` - Android library config
  - `lib-kotlin.gradle.kts` - Kotlin library config
  - `lib-multisrc.gradle.kts` - Multi-source config

### **core/**
Core utilities dan helper functions yang digunakan semua extension.
```
core/src/main/kotlin/extensions/utils/
├── Collections.kt     - List/Map utilities
├── Date.kt           - Date handling
├── Json.kt           - JSON parsing
├── Network.kt        - HTTP requests
├── Preferences.kt    - SharedPreferences wrapper
├── Source.kt         - Base source utilities
└── Url.kt            - URL manipulation
```

### **gradle/**
Gradle dependency management.
- **wrapper/** - Gradle version manager
- **libs.versions.toml** - Centralized dependency versions

### **lib/** (70+ Video Extractors)
Library untuk extract video links dari berbagai platform hosting.

**Extractor populer:**
```
lib/
├── cloudflare-interceptor/     # Bypass Cloudflare
├── cryptoaes/                  # Encryption/Decryption
├── dood-extractor/             # Dood video host
├── rapidcloud-extractor/       # Megacloud/Rapid Cloud
├── streamtape-extractor/       # StreamTape host
├── voe-extractor/              # VoE hosting
├── megacloud-extractor/        # MegaCloud
└── ... (60+ extractors lainnya)
```

**Struktur tiap extractor:**
```
[extractor-name]/
├── src/main/java/eu/kanade/tachiyomi/lib/[extractor-name]/
│   └── [Extractor].kt          # Main logic
├── build.gradle.kts
└── (optional assets/)          # JS files, configs
```

### **lib-multisrc/**
Reusable implementations untuk multiple extensions yang share base code.
```
lib-multisrc/
├── anilist/                    # AniList API integration
├── animestream/                # AnimeStream base
├── dooplay/                    # DooPlay CMS
├── dopeflix/                   # DopeFlix platform
└── ... (lebih banyak templates)
```

Tiap module di `lib-multisrc/` bisa di-reuse oleh multiple extensions di `src/`.

### **src/ - Extension Sources**

#### **src/all/** (International Extensions - 7 sources)
```
src/all/
├── jable/                      # JAV streaming
├── javgg/                      # JAV streaming
├── javguru/                    # JAV streaming with custom extractors
├── missav/                     # JAV streaming
├── rouvideo/                   # Multi-language video
├── xnxx/                       # Adult content
└── xvideos/                    # Adult content
```

#### **src/id/** (Indonesian Extensions)
```
src/id/
├── anichin/                    # Anime Sub Indonesia
│   ├── src/.../anichin/
│   │   ├── Anichin.kt         # Main source
│   │   ├── AnichinFactory.kt  # Factory pattern
│   │   └── AnichinFilters.kt  # Search filters
│   ├── res/mipmap-*/          # Icons (5 sizes)
│   └── build.gradle
│
└── otakudesu/                  # Anime Sub Indonesia
    ├── src/.../otakudesu/
    │   └── OtakuDesu.kt
    ├── res/mipmap-*/          # Icons
    └── build.gradle
```

**Struktur tiap extension:**
```
extension-name/
├── build.gradle                # Extension build config
├── res/
│   ├── mipmap-hdpi/
│   ├── mipmap-mdpi/
│   ├── mipmap-xhdpi/
│   ├── mipmap-xxhdpi/
│   └── mipmap-xxxhdpi/
│       └── ic_launcher.png     # Icon (5 ukuran)
├── src/main/
│   ├── kotlin/eu/kanade/tachiyomi/animeextension/[region]/[source-name]/
│   │   ├── [SourceName].kt        # Main source class
│   │   ├── [SourceName]Factory.kt # Factory (if needed)
│   │   ├── [SourceName]Filters.kt # Search filters
│   │   └── ... (helper classes)
│   └── AndroidManifest.xml (optional)
└── (optional) AndroidManifest.xml
```

---

## 📊 Extension Development Guide

### Menambah Extension Baru (Sub Indonesia)

**1. Create folder structure:**
```bash
src/id/new-extension/
├── build.gradle
├── res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
│   └── ic_launcher.png
└── src/main/kotlin/eu/kanade/tachiyomi/animeextension/id/newextension/
    ├── NewExtension.kt
    ├── NewExtensionFactory.kt (optional)
    └── NewExtensionFilters.kt (optional)
```

**2. NewExtension.kt template:**
```kotlin
package eu.kanade.tachiyomi.animeextension.id.newextension

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.*

class NewExtension : AnimeCatalogueSource() {
    override val name = "New Extension"
    override val baseUrl = "https://example.com"
    override val lang = "id"
    
    // Implement required methods
    override suspend fun getPopularAnime(page: Int) = ...
    override suspend fun searchAnime(page: Int, query: String, filters: FilterList) = ...
    override suspend fun getAnimeDetails(anime: SAnime) = ...
    // ... etc
}
```

**3. Update build.gradle:**
```gradle
plugins {
    id 'lib-android'
}

dependencies {
    // Add needed extractors
    compileOnly project(':lib:rapidcloud-extractor')
}
```

**4. Update list.md** dengan extension baru
**5. Create PR** untuk di-review

---

## 🔄 Build & Compilation

### Setup Development:
```bash
# Clone repository
git clone https://github.com/cemmekx096-cmd/Apk-project69.git
cd Apk-project69

# Build extension
./gradlew build

# Build specific extension
./gradlew :src:id:anichin:build
```

### Output:
- APK files: `src/[region]/[extension]/build/outputs/apk/release/`
- Akan di-push ke `repo` branch untuk distribution

---

## 📝 Nama Convention

- **Package:** `eu.kanade.tachiyomi.animeextension.[region].[sourcename]`
- **Folder:** `src/[region]/[source-name-lowercase]/`
- **Class:** `SourceNameCapitalized`
- **Extension name:** "Source Name" (display)

**Contoh:**
- Folder: `src/id/otakudesu/`
- Package: `eu.kanade.tachiyomi.animeextension.id.otakudesu`
- Class: `OtakuDesu`

---

## 🔐 Branch Strategy

### **master** (Development)
- Source code extensions
- Library extractors
- Build configurations
- Dokumentasi

### **repo** (Release/Hosting)
- Compiled APK files
- `index.min.json` (metadata)
- **Auto-generated** by CI/CD
- **JANGAN edit manual**

---

## 📦 Extractors Yang Tersedia (70+)

**Video Hosting:**
- Dood, DoodStream, Rapidcloud, Megacloud, StreamTape
- FileMoon, MixDrop, VoE, Streamlare, mp4upload
- VidStream, VidMoly, VidHide, GogoStream
- Dan 50+ lainnya...

**Special:**
- Cloudflare Interceptor (bypass CF)
- CryptoAES (decryption)
- Google Drive (untuk files)

---

## 🚀 Development Checklist

Sebelum push extension baru:

- [ ] Extension implements `AnimeCatalogueSource` interface
- [ ] Minimal support: popular + search + details
- [ ] Add extractors needed via `compileOnly project()`
- [ ] Test locally dulu
- [ ] Update `list.md`
- [ ] Icon (5 sizes) di `res/mipmap-*/`
- [ ] Following package naming convention
- [ ] No hardcoded credentials
- [ ] Handle errors gracefully

---

## 📚 File Penting untuk Di-Track

**Harus di-commit:**
- Source code (`.kt`, `.xml`)
- Build configs (`build.gradle.kts`)
- Icons & resources
- Documentation (`.md`)

**Jangan di-commit:**
```gitignore
/build/
.gradle/
.idea/
*.apk
*.aab
local.properties
```

---

## 📞 Related Files

- `README.md` - Main documentation
- `list.md` - Extension list & features
- `CONTRIBUTING.md` - Development guidelines
- `CODE_OF_CONDUCT.md` - Community rules
- `gradle.properties` - Build properties
- `ktlintCodeStyle.xml` - Code style rules

---

## 🎯 Quick Links

- **Install URL:** `https://raw.githubusercontent.com/cemmekx096-cmd/Apk-project69/refs/heads/repo/index.min.json`
- **Original:** `https://github.com/yuzono/aniyomi-extensions`
- **Aniyomi:** `https://github.com/aniyomiorg/aniyomi`


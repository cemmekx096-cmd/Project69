# Rencana: Hydrax/Abyss Extractor Library
> Disimpan: 2026-02-14 | Status: PENDING - belum diimplementasikan

## Referensi
- Repo: https://github.com/abdlhay/AbyssVideoDownloader
- Provider: `short.icu/{id}` → redirect ke `abysscdn.com/?v={id}`

## Sistem Kerja Hydrax

### Step 1 - Ambil Video ID
```
short.icu/JhW_Ut5Cb → ambil slug setelah "/"
abysscdn.com/?v=JhW_Ut5Cb → ambil parameter "v"
```

### Step 2 - Fetch Halaman Abyss
```
GET https://abysscdn.com/?v={id}
→ Response: HTML dengan JS yang di-obfuscate
```

### Step 3 - Extract JS (AbyssJsCodeExtractor)
- Parse JS obfuscated dari HTML
- Extract encrypted video metadata (sourcesEncrypted)
- Butuh jalankan potongan JS → hasilnya JSON metadata video
- Masalah: perlu eval JS → di Android butuh WebView atau Rhino engine

### Step 4 - Generate Key (CryptoHelper.getKey)
```kotlin
fun getKey(value: Any?): String {
    // Convert value ke bytes (handle Number & String)
    // MD5 hash bytes → return hex string
}
```

### Step 5 - Decrypt Metadata (CryptoHelper.decryptAESCTR)
```
AES-CTR mode
Key: MD5 dari value tertentu (16 bytes)
IV: 16 bytes pertama dari key
→ Hasil: JSON dengan list segment URLs
```

### Step 6 - Download Segments
```
Base URL: https://p5bxlr4lz51.sssrr.org/sora/{videoId}/{token}
Token: di-generate per segment (Base64 encoded)
1027 segments untuk 1 video (1.92 GB)
```

## File Kunci di AbyssVideoDownloader
| File | Fungsi |
|------|--------|
| `AbyssToProvider.kt` | Extract video ID dari berbagai URL format |
| `CryptoHelper.kt` | AES-CTR encrypt/decrypt + MD5 key generation |
| `AbyssJsCodeExtractor.kt` | Parse & extract dari JS obfuscated |
| `services/VideoDownloader.kt` | Flow download lengkap (belum dibaca) |
| `services/HttpClientManager.kt` | HTTP client management (belum dibaca) |
| `services/ProviderDispatcher.kt` | Routing ke provider yang tepat (belum dibaca) |

## Tantangan Implementasi
1. **JS Eval** - AbyssJsCodeExtractor butuh jalankan JS → perlu Rhino engine atau WebView
2. **1000+ segments** - bukan HLS biasa, tidak bisa langsung diputar di player
3. **Token per segment** - setiap request butuh token baru

## Rencana Library: `lib/abyss-extractor`
```
lib/
  abyss-extractor/
    src/
      AbyssExtractor.kt      ← Main entry point
      AbyssProvider.kt       ← Port dari AbyssToProvider.kt  
      AbyssCrypto.kt         ← Port dari CryptoHelper.kt
      AbyssJsExtractor.kt    ← Port dari AbyssJsCodeExtractor.kt
      AbyssDownloader.kt     ← Stream segments ke player
    build.gradle.kts
```

## Alternatif Solusi
- Hydrax → langsung iframe fallback (user buka di WebView)
- Fokus Cast + TurboVIP + P2P yang lebih mudah dulu

## Services Analysis

### HttpClientManager.kt
- Windows → pakai Unirest
- Linux/Mac → pakai OkHttp + ImpersonatorFactory (curl-impersonate)
- Di Android → cukup OkHttp biasa, tidak perlu impersonator

### ProviderDispatcher.kt
- Router URL → Provider yang sesuai
- Default provider untuk Hydrax → `AbyssToProvider`
- Ada `JavaScriptExecutor` → butuh JS engine, tapi HANYA untuk provider lain (bukan Abyss)

### VideoDownloader.kt — PALING PENTING!

**Step 1: getVideoMetaData(url)**
```
GET abysscdn.com/?v={id}
→ Cari script yang mengandung "datas"
→ Regex: const datas = "{base64_string}"
→ Base64 decode → JSON Datas {user_id, slug, md5_id, media(encrypted)}
```

**Step 2: Decrypt metadata**
```
mediaKey = "{user_id}:{slug}:{md5_id}"
decryptionKey = MD5(mediaKey).toByteArray()
mediaSources = AES-CTR decrypt(media, decryptionKey)
→ JSON Video {mp4: {url, size, resId, md5_id}}
```

**Step 3: generateSegmentTokens**
```
encryptionKey = MD5(simpleVideo.size)
ranges = chunks of 2MB from total size
for each index i:
  path = "/mp4/{md5_id}/{resId}/{size}/2097152/{index}"
  encryptedBody = AES-CTR encrypt(path, encryptionKey)
  token = Base64(Base64(encryptedBody)).replace("=", "")
```

**Step 4: Download**
```
URL = "{simpleVideo.url}/sora/{simpleVideo.size}/{token}"
GET dengan Referer: https://abysscdn.com/
→ Binary 2MB per segment → merge → MP4
```

**KABAR BAIK: TIDAK butuh JS eval!**
Hanya butuh: Base64 + AES-CTR + MD5 → semua ada di Android standard library

## Rencana Library: lib/abyss-extractor (Updated)
```
AbyssExtractor.kt   ← getVideoId + getMetadata + generateTokens + return Video URLs
AbyssCrypto.kt      ← Port CryptoHelper (AES-CTR + MD5, tanpa Koin)
AbyssModels.kt      ← Data classes: Datas, Video, Mp4, SimpleVideo
```

## Status
- [x] Baca AbyssToProvider.kt
- [x] Baca CryptoHelper.kt
- [x] Baca AbyssJsCodeExtractor.kt
- [x] Baca HttpClientManager.kt
- [x] Baca ProviderDispatcher.kt
- [x] Baca VideoDownloader.kt
- [x] Konfirmasi: TIDAK perlu JS eval untuk Abyss default
- [x] Konfirmasi: Cukup javax.crypto (sudah ada di Android)
- [ ] Baca model files: Datas.kt, Video.kt, Mp4.kt, SimpleVideo.kt
- [ ] Implementasi lib/abyss-extractor di repo aniyomi-extensions

# Kirimly

Transfer file & teks lokal Android ↔ iOS lewat Wi-Fi/Hotspot — pengguna iOS cukup memakai Safari.

## Build

- **GitHub Actions:** push ke `main`/`master` → workflow `Build Kirimly APK` menghasilkan artefak `kirimly-debug-apk`.
- **Lokal:** JDK 17 + Android SDK 35, lalu `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

Stack: AGP 8.7.3 · Gradle 8.11.1 · Kotlin 2.0.21 · Compose BOM 2024.12.01 · Ktor 2.3.12 (server + client CIO) · Room 2.6.1 (KSP) · DataStore 1.1.1 · ZXing 3.5.3. `minSdk 26`, `targetSdk/compileSdk 35`.

## Cara pakai

1. **Terima dari iPhone:** tab *Terima* → Mulai Server → iPhone memindai **QR URL Safari** → masukkan PIN 4 digit → pilih file → setujui di Android. File tersimpan di `Downloads/Kirimly`.
2. **Tanpa Wi-Fi:** di kartu QR pilih *Wi-Fi Hotspot* → Aktifkan Hotspot Lokal (atau hotspot sistem + isi SSID manual) → iPhone memindai QR Wi-Fi, lalu QR URL.
3. **Kirim ke iPhone:** tab *Kirim* → tambah file/teks ke antrean → nyalakan *Berbagi cepat ke Safari* → iPhone membuka URL dan mengunduh.
4. **Android ↔ Android:** kedua perangkat membuka Kirimly; penerima menyalakan server; pengirim mengetuk perangkat di radar.

## API server (port 8080, naik otomatis bila terpakai)

| Method | Path | Keterangan |
|---|---|---|
| GET | `/` | Portal web iOS (HTML/CSS/JS tertanam) |
| GET | `/api/info` | Nama perangkat, status PIN |
| POST | `/api/verify-pin` | Cek PIN (`X-Kirimly-Pin`) |
| POST | `/api/request-upload` | Minta izin kirim; ditahan hingga Terima/Tolak (maks 55 dtk) → token |
| POST | `/api/upload?token&index&name` | Multipart streaming, buffer 8 KB ke MediaStore |
| POST | `/api/text` | Teks → clipboard + riwayat |
| GET | `/api/shared-files` | Antrean yang dibagikan Android |
| GET | `/api/download/{id}` | Unduh item antrean |
| WS | `/ws` | Pembaruan langsung (antrean, PIN berubah) |

## Struktur

```
data/      model, Room (riwayat), DataStore (tema, palet, nama, PIN)
network/   KirimlyServerManager (Ktor), PeerTransferClient, NsdDiscoveryManager,
           HotspotManager, IosWebPortalHtml, QrCodeGenerator, NetworkUtils/MediaStoreWriter, TransferTracker
service/   TransferForegroundService (dataSync, notifikasi status + aksi Terima/Tolak)
ui/        Theme (5 palet × terang/gelap), komponen, MainViewModel, layar Kirim/Terima/Riwayat
```

## Catatan

- Belum pernah dikompilasi di lingkungan pembuatnya (tanpa Android SDK); build pertama CI adalah verifikasinya.
- Release build memakai R8 tetapi belum dikonfigurasi signing — tambahkan `signingConfigs` sendiri.
- Portal web memakai HTTP biasa di jaringan lokal; `navigator.clipboard` di Safari memakai fallback `execCommand`.

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
| POST | `/api/upload/init` | Mulai upload resumable (token, index, ukuran, folder relatif, kunci ECDH opsional) |
| POST | `/api/upload/chunk?id&offset` | Chunk 4 MB + header `X-Chunk-Sha256`; 409 mengembalikan offset server |
| GET | `/api/upload/status?id` | Offset terakhir untuk melanjutkan |
| POST | `/api/upload/complete?id` | Verifikasi SHA-256 penuh, publikasikan ke Downloads/Kirimly |
| POST | `/api/upload?token&index&name` | Upload multipart lama (kompatibilitas) |
| GET | `/api/download-all` | Semua antrean sebagai ZIP streaming |
| POST | `/api/text` | Teks → clipboard + riwayat |
| GET | `/api/shared-files` | Antrean yang dibagikan Android |
| GET | `/api/download/{id}` | Unduh item antrean |
| WS | `/ws` | Pembaruan langsung (antrean, PIN berubah, alamat berubah) |

Android ↔ Android: chunk terenkripsi ECDH P-256 + AES-256-GCM, 3 file paralel. Perangkat tepercaya (`X-Kirimly-Device`) melewati PIN dan persetujuan. Apple membuka `http://kirimly.local:PORT` lewat responder mDNS bawaan.

## Struktur

```
data/      model, Room (riwayat), DataStore (tema, palet, nama, PIN)
network/   KirimlyServerManager (Ktor), PeerTransferClient, NsdDiscoveryManager,
           HotspotManager, IosWebPortalHtml, QrCodeGenerator, NetworkUtils/MediaStoreWriter, TransferTracker
service/   TransferForegroundService (dataSync, notifikasi status + aksi Terima/Tolak)
ui/        Theme (5 palet × terang/gelap), komponen, MainViewModel, layar Kirim/Terima/Riwayat
```

## Catatan

- CI GitHub Actions lulus (`assembleDebug`); APK debug tersedia sebagai artefak `kirimly-debug-apk`.
- Release build memakai R8 tetapi belum dikonfigurasi signing — tambahkan `signingConfigs` sendiri.
- Portal web memakai HTTP biasa di jaringan lokal; `navigator.clipboard` di Safari memakai fallback `execCommand`.

## Tambahan v1.2

- **Nearby Connections**: Android ↔ Android tanpa jaringan bersama (Bluetooth LE → Wi-Fi Direct), kode verifikasi di kedua layar.
- **iPhone**: hotspot lokal otomatis saat tanpa Wi-Fi, alur dua pindaian (QR Wi-Fi lalu QR Safari), atau Android bergabung ke Personal Hotspot iPhone.
- **Tahan aplikasi tertutup**: sesi upload & token disimpan di Room (`upload_sessions`, `approved_tokens`), digest dibangun ulang dari file saat server hidup lagi.
- **Kirim ke Safari**: tab Safari yang terhubung muncul di tab Kirim; Android bisa mendorong antrean, Safari menampilkan prompt Unduh.
- **Lainnya**: thumbnail (`/api/thumbnail/{id}`), auto-stop saat menganggur, log crash lokal, versionCode dari CI.
- **Bahasa**: English (default) + Indonesia (`values-in`), pemilih bahasa per aplikasi Android 13+, portal mengikuti bahasa browser.
- **Play Store**: lihat `docs/` (kebijakan privasi, listing, data safety, izin, checklist).

package com.kirimly.app.network

/**
 * Self-contained, mobile-first web client served at `GET /` for iOS Safari (and any browser).
 * Pure HTML5 + CSS3 + vanilla JS – no external requests, works fully offline on a hotspot.
 *
 * NOTE: this raw string deliberately contains no dollar signs so Kotlin performs no templating.
 */
object IosWebPortalHtml {

    val PAGE: String get() = HEAD + SCRIPT

    /** The page in the app's current language ("id" or "en"), so Safari matches the app. */
    fun render(lang: String): String = PAGE.replaceFirst("<html lang=\"id\">", "<html lang=\"" + lang + "\">")

    /** Markup and styles. */
    private const val HEAD: String = """<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black">
<meta name="theme-color" content="#000000">
<meta name="format-detection" content="telephone=no">
<title>Kirimly</title>
<style>
@font-face {
  font-family: "Host Grotesk";
  src: url("/assets/host-grotesk.woff2") format("woff2");
  font-weight: 300 800;
  font-style: normal;
  font-display: swap;
}
:root {
  --ground: #000000;
  --surface: #0b0b0b;
  --raise: #141414;
  --hairline: #1d1d1d;
  --text: #f9f8f9;
  --text-2: #e5e5e4;
  --muted: #d6d6d5;
  --action: #ededed;
  --on-action: #040404;
  --status: #a3cfff;
  --danger: #ff8a80;
  --success: #a3cfff;
  color-scheme: dark;
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html { background: var(--ground); }
html, body { margin: 0; padding: 0; }
body {
  font-family: "Host Grotesk", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  background: var(--ground);
  color: var(--text);
  font-size: 16px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  padding-bottom: env(safe-area-inset-bottom);
}
::selection { background: var(--action); color: var(--on-action); }
:focus-visible { outline: 2px solid var(--text); outline-offset: 3px; }
.wrap { max-width: 640px; margin: 0 auto; padding: 0 24px 48px; }
header.hero { padding: calc(env(safe-area-inset-top) + 12px) 12px 0; }
.hero-inner {
  max-width: 640px; margin: 0 auto; background: var(--surface); border: 1px solid var(--hairline);
  display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 12px 12px 16px;
}
.brand { display: flex; align-items: center; gap: 10px; min-width: 0; }
.logo { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; flex: none; }
.logo svg { width: 24px; height: 24px; }
.brand h1 { font-size: 18px; font-weight: 500; margin: 0; letter-spacing: -0.2px; }
.brand p { display: none; }
.peer-pill {
  display: flex; align-items: center; gap: 8px; min-width: 0;
  border: 1px solid var(--hairline); border-radius: 9999px; padding: 6px 12px; font-size: 13px; color: var(--muted);
}
.peer-pill span:last-child { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.peer-pill b { color: var(--text); font-weight: 500; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #5c5c5c; flex: none; }
.dot.on { background: var(--status); }
.intro { padding: 56px 0 8px; }
.intro h2 { font-size: 44px; line-height: 1.05; font-weight: 500; letter-spacing: -1.2px; margin: 0 0 14px; }
.intro p { color: var(--muted); margin: 0; max-width: 34ch; }
.card { background: var(--surface); border: 1px solid var(--hairline); padding: 20px; margin-top: 16px; }
.card h2 { font-size: 20px; font-weight: 500; margin: 0 0 4px; letter-spacing: -0.2px; }
.card .sub { color: var(--muted); font-size: 14px; margin: 0 0 16px; }
.drop {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  border: 1px dashed #3a3a3a; padding: 28px 12px; text-align: center;
  color: var(--muted); cursor: pointer; transition: border-color .2s, background .2s;
}
.drop.drag { border-color: var(--text); background: var(--raise); }
.drop strong { color: var(--text); font-size: 16px; font-weight: 500; }
.drop svg { width: 32px; height: 32px; color: var(--text); }
input[type=file] { display: none; }
.list { list-style: none; margin: 12px 0 0; padding: 0; }
.row { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-top: 1px solid var(--hairline); }
.row:first-child { border-top: none; }
.ico {
  width: 40px; height: 40px; background: var(--raise); border: 1px solid var(--hairline); color: var(--text-2);
  display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 500; flex: none;
  text-transform: uppercase; letter-spacing: 0.4px;
}
.meta { flex: 1; min-width: 0; }
.name { font-size: 15px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.size { font-size: 13px; color: var(--muted); font-variant-numeric: tabular-nums; }
.text-preview { font-size: 14px; color: var(--text-2); white-space: pre-wrap; word-break: break-word; max-height: 88px; overflow: hidden; }
.btn {
  appearance: none; border: 1px solid var(--action); border-radius: 9999px; padding: 13px 20px; font: inherit;
  font-size: 15px; font-weight: 500; background: var(--action); color: var(--on-action); cursor: pointer; width: 100%;
  transition: transform .08s ease, opacity .2s, background .2s;
}
.btn:hover { background: #ffffff; }
.btn:active { transform: scale(0.98); }
.btn[disabled] { opacity: 0.4; cursor: default; }
.btn.ghost { background: transparent; color: var(--text); border-color: #3a3a3a; }
.btn.ghost:hover { background: var(--raise); }
.btn.small { width: auto; padding: 8px 16px; font-size: 14px; text-decoration: none; display: inline-block; white-space: nowrap; flex: none; }
.icon-btn {
  appearance: none; border: none; background: transparent; color: var(--muted); font-size: 20px;
  width: 36px; height: 36px; border-radius: 9999px; cursor: pointer; flex: none;
}
.icon-btn:hover { background: var(--raise); color: var(--text); }
.actions { display: flex; gap: 10px; margin-top: 16px; }
.summary { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--hairline); font-variant-numeric: tabular-nums; }
textarea, input.field {
  width: 100%; border: 1px solid #3a3a3a; padding: 12px 14px; font-size: 16px; border-radius: 0;
  background: var(--ground); color: var(--text); font-family: inherit; outline: none; resize: vertical; caret-color: var(--text);
}
textarea::placeholder, input.field::placeholder { color: #8c8c8b; }
textarea { min-height: 104px; }
textarea:focus, input.field:focus { border-color: var(--text); }
.bar { height: 4px; background: var(--hairline); overflow: hidden; margin-top: 8px; }
.bar > span { display: block; height: 100%; width: 0; background: var(--text); transition: width .15s linear; }
.bar.done > span { background: var(--success); }
.bar.fail > span { background: var(--danger); }
.prog-meta { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); margin-top: 4px; font-variant-numeric: tabular-nums; }
.status-box {
  margin-top: 14px; padding: 12px 14px; border: 1px solid var(--hairline); background: var(--raise);
  color: var(--text-2); font-size: 14px; display: none; align-items: center; gap: 10px;
}
.status-box.show { display: flex; }
.spinner { width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--status); border-right-color: transparent; animation: spin .8s linear infinite; flex: none; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spinner { animation: none; } .bar > span { transition: none; } }
.empty { text-align: center; color: var(--muted); font-size: 14px; padding: 16px 0 4px; }
.toast {
  position: fixed; left: 50%; bottom: calc(env(safe-area-inset-bottom) + 24px); transform: translateX(-50%) translateY(30px);
  background: var(--action); color: var(--on-action); padding: 12px 20px; border-radius: 9999px; font-size: 14px; font-weight: 500; opacity: 0;
  transition: all .25s ease; pointer-events: none; max-width: 88vw; text-align: center; z-index: 50;
}
.toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
.toast.err { background: var(--danger); color: #040404; }
.toast.ok { background: var(--success); color: #040404; }
.modal-back { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.8); display: none; align-items: center; justify-content: center; z-index: 40; padding: 24px; }
.modal-back.show { display: flex; }
.modal { background: var(--surface); border: 1px solid var(--hairline); padding: 28px 22px; width: 100%; max-width: 360px; text-align: center; }
.modal h3 { margin: 0 0 6px; font-size: 22px; font-weight: 500; }
.modal p { margin: 0 0 20px; color: var(--muted); font-size: 14px; }
.pin-inputs { display: flex; gap: 10px; justify-content: center; margin-bottom: 14px; }
.pin-inputs input {
  width: 56px; height: 64px; text-align: center; font-size: 28px; font-weight: 500; border-radius: 0; font-family: inherit;
  border: 1px solid #3a3a3a; background: var(--ground); color: var(--text); outline: none; caret-color: var(--text);
}
.pin-inputs input:focus { border-color: var(--text); }
.shake { animation: shake .4s; }
@keyframes shake { 20%, 60% { transform: translateX(-8px); } 40%, 80% { transform: translateX(8px); } }
.err-text { color: var(--danger); font-size: 13px; min-height: 18px; margin-bottom: 8px; }
.label { font-size: 13px; color: var(--muted); margin: 0 0 8px; display: block; }
.footer { text-align: center; color: var(--muted); font-size: 13px; margin-top: 32px; padding-top: 20px; border-top: 1px solid var(--hairline); }
#folderInput { display: none; }
.tabs { display: flex; gap: 4px; padding: 4px; margin-top: 8px; border: 1px solid var(--hairline); border-radius: 9999px; background: var(--surface); position: sticky; top: 8px; z-index: 5; }
.tab { flex: 1; appearance: none; border: none; border-radius: 9999px; padding: 12px 6px; font: inherit; font-size: 14px; font-weight: 500; white-space: nowrap; display: flex; align-items: center; justify-content: center; background: transparent; color: var(--muted); cursor: pointer; }
.tab.active { background: var(--action); color: var(--on-action); }
.badge { display: inline-block; min-width: 20px; padding: 0 6px; margin-left: 6px; flex: none; border-radius: 9999px; background: var(--status); color: #040404; font-size: 12px; line-height: 20px; }
[hidden] { display: none !important; }
.ico img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ico { overflow: hidden; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.notice { font-size: 13px; color: var(--muted); margin: 12px 0 0; }
#keepAwake { display: none; }
label.btn { display: block; text-align: center; }
.actions .btn { flex: 1; }
a.btn { text-align: center; text-decoration: none; display: block; }

/* ---------- Clipboard sync (Kirimly Pro) ---------- */
.clipbar { position: fixed; left: 12px; right: 12px; bottom: calc(env(safe-area-inset-bottom) + 12px); z-index: 70; background: var(--raise); border: 1px solid #3a3a3a; padding: 14px 16px; display: none; max-width: 560px; margin: 0 auto; }
.clipbar.show { display: block; animation: spFade .25s ease; }
.clipbar .clip-from { font-size: 12px; color: var(--muted); margin: 0 0 6px; }
.clipbar .clip-text { margin: 0 0 12px; max-height: 96px; overflow: auto; white-space: pre-wrap; word-break: break-word; font-size: 15px; }
.clipbar .clip-actions { display: flex; gap: 8px; justify-content: flex-end; }
/* ---------- Intro animation (about 6 s) ---------- */
.splash { position: fixed; inset: 0; z-index: 100; background: #000000; display: flex; align-items: center; justify-content: center; transition: opacity .6s ease, visibility .6s ease; }
.splash.hide { opacity: 0; visibility: hidden; }
.splash-inner { text-align: center; padding: 24px; }
.splash-mark { width: 132px; height: 132px; overflow: visible; display: block; margin: 0 auto; }
.sp-plane { transform-box: fill-box; transform-origin: 50% 50%; animation: spGlide 1.2s cubic-bezier(.2, 1.4, .4, 1) both, spFloat 2.4s ease-in-out 1.4s infinite alternate; }
.sp-trail { fill: none; stroke: #5c5c5c; stroke-width: 2.5; stroke-linecap: round; stroke-dasharray: 20; stroke-dashoffset: 20; animation: spDraw .8s ease .9s forwards; }
.splash-word { display: flex; justify-content: center; font-size: 44px; font-weight: 500; letter-spacing: -1px; margin-top: 6px; color: var(--text); }
.splash-word span { display: inline-block; opacity: 0; transform: translateY(14px); animation: spRise .6s cubic-bezier(.2, .8, .2, 1) forwards; animation-delay: calc(1.4s + var(--i) * 70ms); }
.splash-tag { color: var(--muted); margin: 8px 0 0; opacity: 0; animation: spFade .6s ease 2.2s forwards; }
.splash-bar { width: 160px; height: 2px; background: var(--hairline); margin: 30px auto 0; overflow: hidden; opacity: 0; animation: spFade .3s ease 2.8s forwards; }
.splash-bar span { display: block; height: 100%; width: 0; background: var(--text); animation: spFill 2.7s ease-in-out 3s forwards; }
.splash-bar.ok span { background: var(--status); }
.splash-status { font-size: 13px; color: var(--muted); margin: 12px 0 0; min-height: 18px; opacity: 0; animation: spFade .4s ease 3s forwards; }
@keyframes spGlide { from { transform: translate(-30px, 26px) scale(.5) rotate(-14deg); opacity: 0; } 30% { opacity: 1; } to { transform: none; opacity: 1; } }
@keyframes spFloat { from { transform: translateY(0); } to { transform: translateY(-5px); } }
@keyframes spDraw { to { stroke-dashoffset: 0; } }
@keyframes spRise { to { opacity: 1; transform: none; } }
@keyframes spFade { to { opacity: 1; } }
@keyframes spFill { to { width: 100%; } }
@media (prefers-reduced-motion: reduce) { .splash * { animation-duration: .01s !important; animation-delay: 0s !important; animation-iteration-count: 1 !important; } }
</style>
</head>
<body>
<div id="splash" class="splash" role="presentation">
  <div class="splash-inner">
    <svg class="splash-mark" viewBox="0 0 108 108" aria-hidden="true">
      <path class="sp-trail" d="M30,66 C35,70 41,71 46,69"/>
      <g class="sp-plane"><path d="M34,52.5 L74,36 L60.5,76 L52.5,59.5 Z" fill="#F9F8F9"/><path d="M52.5,59.5 L74,36 L56.5,63.5 Z" fill="#8C8C8B"/></g>
    </svg>
    <div class="splash-word" aria-label="Kirimly"><span style="--i:0">K</span><span style="--i:1">i</span><span style="--i:2">r</span><span style="--i:3">i</span><span style="--i:4">m</span><span style="--i:5">l</span><span style="--i:6">y</span></div>
    <p class="splash-tag">Kirim file tanpa kabel.</p>
    <div class="splash-bar" id="splashBar"><span></span></div>
    <p id="splashStatus" class="splash-status" aria-live="polite">Menghubungkan ke Android…</p>
  </div>
</div>
<header class="hero">
  <div class="hero-inner">
    <div class="brand">
      <div class="logo" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none"><path d="M2.5 11.2L21.5 3.5L15.2 21.5L11.6 13.9Z" fill="#F9F8F9"/><path d="M11.6 13.9L21.5 3.5L12.8 15.6Z" fill="#8C8C8B"/></svg>
      </div>
      <h1>Kirimly</h1>
    </div>
    <div class="peer-pill"><span id="wsDot" class="dot"></span><span><b id="deviceName">Android</b></span></div>
  </div>
</header>

<main class="wrap">
  <section class="intro">
    <h2 id="introTitle">Kirim file ke Android, langsung dari Safari.</h2>
    <p id="introSub">Tanpa aplikasi, tanpa internet. Cukup satu jaringan Wi-Fi atau hotspot.</p>
  </section>

  <div class="tabs" role="tablist" aria-label="Mode">
    <button id="tabSend" class="tab active" type="button" role="tab" aria-selected="true" aria-controls="paneSend">Kirim ke Android</button>
    <button id="tabReceive" class="tab" type="button" role="tab" aria-selected="false" aria-controls="paneReceive">Terima dari Android <span id="sharedBadge" class="badge" style="display:none"></span></button>
  </div>

  <div id="paneSend" role="tabpanel" aria-labelledby="tabSend">
  <section class="card">
    <h2>Kirim file ke Android</h2>
    <p class="sub">Foto, video, dokumen, arsip, atau satu folder utuh. Di Mac kamu juga bisa menempel gambar dengan Cmd+V.</p>
    <label id="drop" class="drop" for="fileInput">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4"/><path d="M7 9l5-5 5 5"/><path d="M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/></svg>
      <strong>Pilih file</strong>
      <span>atau seret &amp; lepas di sini</span>
    </label>
    <input id="fileInput" type="file" multiple>
    <input id="folderInput" type="file" webkitdirectory multiple>
    <div class="actions"><label for="folderInput" class="btn ghost" role="button" tabindex="0" id="folderBtn">Pilih folder</label></div>
    <ul id="selectedList" class="list"></ul>
    <div id="selectedSummary" class="summary" style="display:none"><span id="selCount"></span><span id="selSize"></span></div>
    <div id="approvalBox" class="status-box"><span class="spinner"></span><span id="approvalText">Menunggu persetujuan di Android…</span></div>
    <div id="overall" style="display:none; margin-top:14px">
      <div class="prog-meta"><span id="overallLabel">Mengunggah…</span><span id="overallSpeed"></span></div>
      <div class="bar" id="overallBar"><span></span></div>
      <p id="keepAwake" class="notice">Biarkan layar menyala dan Safari tetap terbuka. Jika koneksi terputus atau layar terkunci, Kirimly melanjutkan dari posisi terakhir saat kamu kembali.</p>
    </div>
    <div class="actions">
      <button id="clearBtn" class="btn ghost" type="button" style="display:none">Bersihkan</button>
      <button id="sendBtn" class="btn" type="button" disabled>Kirim</button>
    </div>
  </section>

  <section class="card">
    <h2>Kirim teks atau tautan</h2>
    <p class="sub">Langsung masuk ke clipboard &amp; riwayat Android.</p>
    <label class="label" for="textInput">Teks atau URL</label>
    <textarea id="textInput" placeholder="Ketik atau tempel di sini"></textarea>
    <div class="actions"><button id="sendTextBtn" class="btn" type="button">Kirim Teks</button></div>
  </section>

  <section class="card">
    <label class="label" for="senderName">Nama perangkatmu (opsional)</label>
    <input id="senderName" class="field" type="text" maxlength="40" placeholder="mis. iPhone Rina">
    <p class="notice">Live Photo terkirim sebagai foto. Foto HEIC dikirim apa adanya; Android bisa mengubahnya ke JPG jika diaktifkan.</p>
  </section>

  </div>

  <div id="paneReceive" role="tabpanel" aria-labelledby="tabReceive" hidden>
  <section class="card">
    <div class="card-head">
      <div>
        <h2>Dibagikan dari Android</h2>
        <p class="sub" style="margin-bottom:0">Ketuk untuk mengunduh.</p>
      </div>
      <button id="refreshBtn" class="btn small ghost" type="button">Muat ulang</button>
    </div>
    <ul id="sharedList" class="list"></ul>
    <div id="sharedEmpty" class="empty">Belum ada file yang dibagikan.</div>
    <div class="actions" id="zipRow" style="display:none"><a id="zipLink" class="btn" href="#">Unduh semua (ZIP)</a></div>
  </section>

  </div>

  <p class="footer"><span class="dot" style="display:inline-block;vertical-align:middle;margin-right:6px"></span>Koneksi lokal tanpa enkripsi (HTTP). Gunakan di jaringan yang kamu percayai.</p>
</main>

<div id="pinModal" class="modal-back" role="dialog" aria-modal="true" aria-labelledby="pinTitle">
  <div class="modal" id="pinCard">
    <h3 id="pinTitle">Masukkan kode</h3>
    <p>Lihat kode 4 digit di tab Terima pada aplikasi Kirimly di Android.</p>
    <div class="pin-inputs">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1" autocomplete="one-time-code" aria-label="Digit kode 1">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1" aria-label="Digit kode 2">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1" aria-label="Digit kode 3">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1" aria-label="Digit kode 4">
    </div>
    <div id="pinError" class="err-text" role="alert"></div>
    <button id="pinSubmit" class="btn" type="button">Hubungkan</button>
  </div>
</div>

<div id="offerModal" class="modal-back" role="dialog" aria-modal="true" aria-labelledby="offerTitle">
  <div class="modal">
    <h3 id="offerTitle">Android ingin mengirim</h3>
    <p id="offerSub"></p>
    <ul id="offerList" class="list" style="text-align:left;margin-bottom:16px"></ul>
    <div class="actions">
      <button id="offerDecline" class="btn ghost" type="button">Tolak</button>
      <button id="offerAccept" class="btn" type="button">Unduh</button>
    </div>
  </div>
</div>

<div id="clipbar" class="clipbar" role="status" aria-live="polite">
  <p id="clipFrom" class="clip-from"></p>
  <p id="clipText" class="clip-text"></p>
  <div class="clip-actions"><button id="clipClose" class="btn ghost small" type="button">Tutup</button><button id="clipCopy" class="btn small" type="button">Salin</button></div>
</div>
<div id="toast" class="toast" role="status" aria-live="polite"></div>

"""

    /** Behaviour; kept separate so neither constant exceeds the class-file limit. */
    private const val SCRIPT: String = """<script>
(function () {
  'use strict';
  /* The Android app writes its own language into <html lang>, so the page always matches the app. */
  var LANG = (document.documentElement.getAttribute('lang') || 'id').toLowerCase().indexOf('en') === 0 ? 'en' : 'id';
  var EN = {
    'Clipboard dari ': 'Clipboard from ',
    'Disalin ke clipboard': 'Copied to clipboard',
    'Tidak bisa menyalin. Tekan lama teksnya untuk menyalin.': 'Couldn\'t copy. Press and hold the text to copy it.',
    'Tutup': 'Close',
    'Kirim file tanpa kabel.': 'Send files without cables.',
    'Menghubungkan ke Android…': 'Connecting to Android…',
    'Terhubung ke ': 'Connected to ',
    'Mencari Android…': 'Looking for Android…',
    'Kirim ke Android': 'Send to Android',
    'Terima dari Android': 'Receive from Android',
    'Ambil file yang dibagikan dari Android.': 'Get files shared from Android.',
    'Pilih file di Android, lalu ketuk iPhone ini di daftar tujuan. Atau unduh yang sudah dibagikan di bawah.': 'Choose files on Android, then tap this iPhone in its list of destinations. Or download what\'s already shared below.',
    'PIN salah atau belum diisi': 'Wrong or missing code',
    'Terlalu banyak percobaan. Tunggu 30 detik.': 'Too many attempts. Wait 30 seconds.',
    'Daftar file kosong': 'No files selected',
    'Permintaan ditolak': 'Request declined',
    'Waktu persetujuan habis': 'Approval timed out',
    'Teks kosong': 'The text is empty',
    'Teks terlalu panjang': 'The text is too long',
    'Sesi upload tidak valid': 'Upload session is invalid',
    'Android ingin mengirim': 'Android wants to send',
    'Belum ada file yang dibagikan.': 'Nothing shared yet.',
    'Bersihkan': 'Clear',
    'Biarkan layar menyala dan Safari tetap terbuka. Jika koneksi terputus atau layar terkunci, Kirimly melanjutkan dari posisi terakhir saat kamu kembali.': 'Keep the screen on and Safari open. If the connection drops or the screen locks, Kirimly resumes where it stopped when you come back.',
    'Dibagikan dari Android': 'Shared from Android',
    'Digit kode 1': 'Code digit 1',
    'Digit kode 2': 'Code digit 2',
    'Digit kode 3': 'Code digit 3',
    'Digit kode 4': 'Code digit 4',
    'Foto, video, dokumen, arsip, atau satu folder utuh. Di Mac kamu juga bisa menempel gambar dengan Cmd+V.': 'Photos, videos, documents, archives or a whole folder. On a Mac you can also paste images with Cmd+V.',
    'Hubungkan': 'Connect',
    'Ketik atau tempel di sini': 'Type or paste here',
    'Ketuk untuk mengunduh.': 'Tap to download.',
    'Kirim': 'Send',
    'Kirim Teks': 'Send text',
    'Kirim file ke Android': 'Send files to Android',
    'Kirim file ke Android, langsung dari Safari.': 'Send files to Android, straight from Safari.',
    'Kirim teks atau tautan': 'Send text or a link',
    'Koneksi lokal tanpa enkripsi (HTTP). Gunakan di jaringan yang kamu percayai.': 'Local connection without encryption (HTTP). Use it on a network you trust.',
    'Langsung masuk ke clipboard & riwayat Android.': 'Goes straight to the Android clipboard & history.',
    'Lihat kode 4 digit di tab Terima pada aplikasi Kirimly di Android.': 'Find the 4-digit code on the Receive tab of Kirimly on Android.',
    'Live Photo terkirim sebagai foto. Foto HEIC dikirim apa adanya; Android bisa mengubahnya ke JPG jika diaktifkan.': 'Live Photos are sent as photos. HEIC photos are sent as they are; Android can convert them to JPG if enabled.',
    'Masukkan kode': 'Enter code',
    'Mengunggah…': 'Uploading…',
    'Menunggu persetujuan di Android…': 'Waiting for approval on Android…',
    'Muat ulang': 'Reload',
    'Nama perangkatmu (opsional)': 'Your device name (optional)',
    'Pilih file': 'Choose files',
    'Pilih folder': 'Choose folder',
    'Tanpa aplikasi, tanpa internet. Cukup satu jaringan Wi-Fi atau hotspot.': 'No app, no internet. Just one Wi-Fi network or hotspot.',
    'Teks atau URL': 'Text or URL',
    'Tolak': 'Decline',
    'Unduh': 'Download',
    'Unduh semua (ZIP)': 'Download all (ZIP)',
    'atau seret & lepas di sini': 'or drag & drop here',
    'mis. iPhone Rina': 'e.g. Rina\'s iPhone',
    ' item • ': ' items • ',
    '. Pindai ulang QR jika halaman berhenti merespons.': '. Rescan the QR if the page stops responding.',
    'Alamat Android berubah ke ': 'Android\'s address changed to ',
    'Berhasil dikirim!': 'Sent!',
    'Gagal memuat daftar file.': 'Couldn\'t load the file list.',
    'Gagal memulai transfer.': 'Couldn\'t start the transfer.',
    'Gagal mengirim teks.': 'Couldn\'t send the text.',
    'Gagal: ': 'Failed: ',
    'Hapus ': 'Remove ',
    'Koneksi terputus.': 'Connection lost.',
    'Mengirim…': 'Sending…',
    'Menunggu persetujuan di ': 'Waiting for approval on ',
    'Kode berubah. Masukkan kode baru dari tab Terima.': 'The code changed. Enter the new code from the Receive tab.',
    'Kode harus 4 digit.': 'The code must be 4 digits.',
    'Kode salah, coba lagi.': 'Wrong code, try again.',
    'Perangkat Web': 'Web device',
    'Permintaan ditolak oleh Android.': 'Android declined the request.',
    'Salin': 'Copy',
    'Salin manual: tekan lama teksnya': 'Copy manually: long-press the text',
    'Salin teks': 'Copy text',
    'Sebagian file gagal dikirim.': 'Some files failed to send.',
    'Semua file terkirim': 'All files sent',
    'Server Android dihentikan.': 'The Android server stopped.',
    'Sesi upload kedaluwarsa': 'Upload session expired',
    'Teks disalin': 'Text copied',
    'Teks masih kosong.': 'The text is empty.',
    'Teks terkirim & disalin di Android': 'Text sent & copied on Android',
    'Tidak ada respons. Coba lagi.': 'No response. Try again.',
    'Tidak dapat terhubung ke Android.': 'Can\'t connect to Android.',
    'Tidak dapat terhubung ke Android. Pastikan server aktif.': 'Can\'t connect to Android. Make sure the server is running.',
    'Total ': 'Total ',
    ' dari ': ' of ',
    ' file ditempel': ' files pasted',
    ' file terkirim': ' files sent',
    ' file': ' files',
    ' ingin mengirim': ' wants to send',
    ' lainnya': ' more',
    ' selesai': ' done',
    'dan ': 'and '
  };
  function T(s) { if (!s) return s; return LANG === 'en' && Object.prototype.hasOwnProperty.call(EN, s) ? EN[s] : s; }
  function translatePage() {
    if (LANG !== 'en') return;
    document.documentElement.lang = 'en';
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      var raw = node.nodeValue, t = raw.trim();
      if (t && Object.prototype.hasOwnProperty.call(EN, t)) node.nodeValue = raw.replace(t, EN[t]);
    }
    Array.prototype.forEach.call(document.querySelectorAll('[placeholder],[aria-label]'), function (n) {
      ['placeholder', 'aria-label'].forEach(function (a) {
        var v = n.getAttribute(a);
        if (v && Object.prototype.hasOwnProperty.call(EN, v)) n.setAttribute(a, EN[v]);
      });
    });
  }


  function Sha256() {
    this.h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
    this.buf = new Uint8Array(64);
    this.bufLen = 0;
    this.total = 0;
    this.w = new Uint32Array(64);
  }
  Sha256.K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ]);
  Sha256.prototype.block = function (d, o) {
    var w = this.w, h = this.h, K = Sha256.K, i, x, y, t1, t2;
    for (i = 0; i < 16; i++) {
      w[i] = (d[o + i * 4] << 24) | (d[o + i * 4 + 1] << 16) | (d[o + i * 4 + 2] << 8) | d[o + i * 4 + 3];
    }
    for (i = 16; i < 64; i++) {
      x = w[i - 15]; y = w[i - 2];
      w[i] = (w[i - 16] + (((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3)) + w[i - 7] +
        (((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10))) | 0;
    }
    var a = h[0], b = h[1], c = h[2], e4 = h[3], e = h[4], f = h[5], g = h[6], k = h[7];
    for (i = 0; i < 64; i++) {
      t1 = (k + (((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7))) +
        ((e & f) ^ (~e & g)) + K[i] + w[i]) | 0;
      t2 = ((((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10))) +
        ((a & b) ^ (a & c) ^ (b & c))) | 0;
      k = g; g = f; f = e; e = (e4 + t1) | 0; e4 = c; c = b; b = a; a = (t1 + t2) | 0;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += e4; h[4] += e; h[5] += f; h[6] += g; h[7] += k;
  };
  Sha256.prototype.update = function (bytes) {
    var len = bytes.length, pos = 0;
    this.total += len;
    if (this.bufLen > 0) {
      var take = Math.min(64 - this.bufLen, len);
      this.buf.set(bytes.subarray(0, take), this.bufLen);
      this.bufLen += take; pos = take;
      if (this.bufLen === 64) { this.block(this.buf, 0); this.bufLen = 0; }
    }
    while (pos + 64 <= len) { this.block(bytes, pos); pos += 64; }
    if (pos < len) { this.buf.set(bytes.subarray(pos), 0); this.bufLen = len - pos; }
    return this;
  };
  Sha256.prototype.hex = function () {
    var bits = this.total * 8;
    var pad = new Uint8Array(((this.bufLen < 56) ? 56 : 120) - this.bufLen + 8);
    pad[0] = 0x80;
    var hi = Math.floor(bits / 0x100000000), lo = bits >>> 0, n = pad.length;
    pad[n - 8] = hi >>> 24; pad[n - 7] = hi >>> 16; pad[n - 6] = hi >>> 8; pad[n - 5] = hi;
    pad[n - 4] = lo >>> 24; pad[n - 3] = lo >>> 16; pad[n - 2] = lo >>> 8; pad[n - 1] = lo;
    var saveTotal = this.total;
    this.update(pad);
    this.total = saveTotal;
    var out = '';
    for (var i = 0; i < 8; i++) out += ('00000000' + this.h[i].toString(16)).slice(-8);
    return out;
  };
  Sha256.hexOf = function (bytes) { return new Sha256().update(bytes).hex(); };
  Sha256.prototype.clone = function () {
    var c = new Sha256();
    c.h.set(this.h); c.buf.set(this.buf); c.bufLen = this.bufLen; c.total = this.total;
    return c;
  };


  var CONCURRENCY = 3;
  var MAX_RETRIES = 40;
  var state = { pin: '', info: null, files: [], busy: false, ws: null, wsRetry: null, authed: false, deviceId: '', wakers: [] };

  function el(id) { return document.getElementById(id); }

  function fmtSize(bytes) {
    if (bytes === undefined || bytes === null || bytes < 0) return '—';
    if (bytes < 1024) return bytes + ' B';
    var units = ['KB', 'MB', 'GB', 'TB'];
    var v = bytes / 1024, i = 0;
    while (v >= 1024 && i < units.length - 1) { v = v / 1024; i++; }
    return (v >= 100 ? v.toFixed(0) : v.toFixed(1)) + ' ' + units[i];
  }

  function fmtSpeed(bps) {
    if (!bps || bps <= 0) return '';
    var kb = bps / 1024;
    return kb < 1024 ? kb.toFixed(0) + ' KB/s' : (kb / 1024).toFixed(1) + ' MB/s';
  }

  function extOf(name) {
    var idx = name.lastIndexOf('.');
    if (idx < 0 || idx === name.length - 1) return 'FILE';
    return name.substring(idx + 1).substring(0, 4);
  }

  var toastTimer = null;
  function toast(msg, kind) {
    var t = el('toast');
    t.textContent = msg;
    t.className = 'toast show' + (kind ? ' ' + kind : '');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = 'toast'; }, 3000);
  }

  /* ---------- Clipboard sync (Kirimly Pro) ---------- */
  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(function () { return true; }, function () { return legacyCopy(text); });
    }
    return Promise.resolve(legacyCopy(text));
  }
  function legacyCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    ta.style.fontSize = '16px';
    document.body.appendChild(ta);
    var range = document.createRange();
    range.selectNodeContents(ta);
    var sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    ta.setSelectionRange(0, text.length);
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    return ok;
  }
  var clipValue = '';
  function showClip(text, from) {
    if (!text) return;
    clipValue = text;
    el('clipFrom').textContent = T('Clipboard dari ') + (from || 'Android');
    el('clipText').textContent = text.length > 600 ? text.slice(0, 600) + '…' : text;
    el('clipbar').className = 'clipbar show';
  }
  el('clipClose').addEventListener('click', function () { el('clipbar').className = 'clipbar'; });
  el('clipCopy').addEventListener('click', function () {
    copyText(clipValue).then(function (ok) {
      toast(ok ? T('Disalin ke clipboard') : T('Tidak bisa menyalin. Tekan lama teksnya untuk menyalin.'), ok ? '' : 'err');
      if (ok) el('clipbar').className = 'clipbar';
    });
  });

  function guessDevice() {
    var ua = navigator.userAgent || '';
    if (/iPhone/.test(ua)) return 'iPhone';
    if (/iPad/.test(ua)) return 'iPad';
    if (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) return 'iPad';
    if (/Macintosh/.test(ua)) return 'Mac';
    if (/Android/.test(ua)) return 'Android';
    if (/Windows/.test(ua)) return 'Windows PC';
    return T('Perangkat Web');
  }

  function senderName() {
    var v = el('senderName').value.trim();
    return v || guessDevice();
  }

  function storeGet(key) { try { return window.localStorage.getItem(key); } catch (e) { return null; } }
  function storeSet(key, value) { try { window.localStorage.setItem(key, value); } catch (e) { } }

  function makeDeviceId() {
    var bytes = new Uint8Array(16), out = '';
    try { window.crypto.getRandomValues(bytes); } catch (e) { for (var i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256); }
    for (var j = 0; j < 16; j++) out += ('0' + bytes[j].toString(16)).slice(-2);
    return out;
  }

  function authHeaders(extra) {
    var h = extra || {};
    if (state.pin) h['X-Kirimly-Pin'] = state.pin;
    if (state.deviceId) h['X-Kirimly-Device'] = state.deviceId;
    return h;
  }

  function authQuery() {
    var parts = [];
    if (state.pin) parts.push('pin=' + encodeURIComponent(state.pin));
    if (state.deviceId) parts.push('device=' + encodeURIComponent(state.deviceId));
    return parts.length ? '?' + parts.join('&') : '';
  }

  function api(method, path, body) {
    var opts = { method: method, headers: authHeaders(body ? { 'Content-Type': 'application/json' } : {}) };
    if (body) opts.body = JSON.stringify(body);
    return fetch(path, opts).then(function (r) {
      return r.text().then(function (t) {
        var data = {};
        try { data = t ? JSON.parse(t) : {}; } catch (e) { data = {}; }
        return { status: r.status, ok: r.ok, data: data };
      });
    });
  }

  function hasFeature(name) {
    return !!(state.info && state.info.features && state.info.features.indexOf(name) >= 0);
  }

  /* ---------- PIN ---------- */
  var pinInputs = Array.prototype.slice.call(document.querySelectorAll('.pin-inputs input'));

  function showPin(message) {
    el('pinModal').className = 'modal-back show';
    el('pinError').textContent = message || '';
    pinInputs.forEach(function (i) { i.value = ''; });
    setTimeout(function () { pinInputs[0].focus(); }, 60);
  }

  function hidePin() { el('pinModal').className = 'modal-back'; }

  pinInputs.forEach(function (input, idx) {
    input.addEventListener('input', function () {
      input.value = input.value.replace(/[^0-9]/g, '').slice(-1);
      if (input.value && idx < pinInputs.length - 1) pinInputs[idx + 1].focus();
      if (pinInputs.every(function (i) { return i.value.length === 1; })) submitPin();
    });
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Backspace' && !input.value && idx > 0) pinInputs[idx - 1].focus();
      if (e.key === 'Enter') submitPin();
    });
    input.addEventListener('paste', function (e) {
      var text = (e.clipboardData || window.clipboardData).getData('text').replace(/[^0-9]/g, '');
      if (text.length >= 4) {
        e.preventDefault();
        pinInputs.forEach(function (i, n) { i.value = text.charAt(n); });
        submitPin();
      }
    });
  });

  function verifyPin(pin, onOk, onFail) {
    state.pin = pin;
    api('POST', '/api/verify-pin').then(function (res) {
      if (res.ok) { storeSet('kirimly_pin', pin); onOk(); }
      else { state.pin = ''; onFail(T(res.data && res.data.message) || T('Kode salah, coba lagi.')); }
    }).catch(function () { state.pin = ''; onFail(T('Tidak dapat terhubung ke Android.')); });
  }

  function submitPin() {
    var pin = pinInputs.map(function (i) { return i.value; }).join('');
    if (pin.length !== 4) { el('pinError').textContent = T('Kode harus 4 digit.'); return; }
    verifyPin(pin, function () { hidePin(); afterAuth(); }, function (msg) {
      var card = el('pinCard');
      card.classList.remove('shake');
      void card.offsetWidth;
      card.classList.add('shake');
      el('pinError').textContent = msg;
      pinInputs.forEach(function (i) { i.value = ''; });
      pinInputs[0].focus();
    });
  }

  el('pinSubmit').addEventListener('click', submitPin);

  /* ---------- Info & auth ---------- */
  function pinFromFragment() {
    var m = /pin=([0-9]{4})/.exec(location.hash || '');
    if (m) {
      try { history.replaceState(null, '', location.pathname + location.search); } catch (e) { }
      return m[1];
    }
    return '';
  }

  function loadInfo() {
    api('GET', '/api/info').then(function (res) {
      state.info = res.data || {};
      el('deviceName').textContent = state.info.deviceName || 'Android';
      splashStatus(T('Terhubung ke ') + (state.info.deviceName || 'Android'), true);
      document.title = 'Kirimly • ' + (state.info.deviceName || 'Android');
      if (!state.info.pinRequired || state.info.trusted) { afterAuth(); return; }
      var candidate = pinFromFragment() || storeGet('kirimly_pin') || '';
      if (candidate.length === 4) {
        verifyPin(candidate, afterAuth, function () { showPin(); });
      } else {
        showPin();
      }
    }).catch(function () {
      splashStatus(T('Mencari Android…'), false);
      toast(T('Tidak dapat terhubung ke Android. Pastikan server aktif.'), 'err');
      setTimeout(loadInfo, 4000);
    });
  }

  function afterAuth() {
    state.authed = true;
    loadShared();
    connectWs();
  }

  function handleUnauthorized() {
    state.pin = '';
    state.authed = false;
    showPin(T('Kode berubah. Masukkan kode baru dari tab Terima.'));
  }

  /* ---------- Shared from Android ---------- */
  function loadShared() {
    api('GET', '/api/shared-files').then(function (res) {
      if (res.status === 401) { handleUnauthorized(); return; }
      renderShared((res.data && res.data.items) || []);
    }).catch(function () { toast(T('Gagal memuat daftar file.'), 'err'); });
  }

  function copyText(text) {
    function fallback() {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      ta.setSelectionRange(0, text.length);
      var ok = false;
      try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
      document.body.removeChild(ta);
      toast(ok ? T('Teks disalin') : T('Salin manual: tekan lama teksnya'), ok ? 'ok' : 'err');
    }
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(function () { toast(T('Teks disalin'), 'ok'); }, fallback);
    } else {
      fallback();
    }
  }

  function renderShared(items) {
    var list = el('sharedList');
    list.innerHTML = '';
    el('sharedEmpty').style.display = items.length ? 'none' : 'block';
    var badge = el('sharedBadge');
    badge.textContent = String(items.length);
    badge.style.display = items.length ? 'inline-block' : 'none';
    var zipOk = items.length > 1 && hasFeature('zip');
    el('zipRow').style.display = zipOk ? 'flex' : 'none';
    if (zipOk) el('zipLink').href = '/api/download-all' + authQuery();
    items.forEach(function (item) {
      var li = document.createElement('li');
      li.className = 'row';
      var ico = document.createElement('div');
      ico.className = 'ico';
      ico.textContent = item.type === 'text' ? 'TXT' : extOf(item.name);
      if (item.type === 'file' && /^(image|video)[/]/.test(item.mimeType || '')) {
        var img = document.createElement('img');
        img.alt = '';
        img.loading = 'lazy';
        img.src = '/api/thumbnail/' + encodeURIComponent(item.id) + authQuery();
        img.onload = function () { ico.textContent = ''; ico.appendChild(img); };
      }
      var meta = document.createElement('div');
      meta.className = 'meta';
      if (item.type === 'text') {
        var pv = document.createElement('div');
        pv.className = 'text-preview';
        pv.textContent = item.text || item.name;
        meta.appendChild(pv);
        var btn = document.createElement('button');
        btn.className = 'btn small ghost';
        btn.type = 'button';
        btn.textContent = T('Salin');
        btn.addEventListener('click', function () { copyText(item.text || ''); });
        li.appendChild(ico); li.appendChild(meta); li.appendChild(btn);
      } else {
        var nm = document.createElement('div');
        nm.className = 'name';
        nm.textContent = item.name;
        var sz = document.createElement('div');
        sz.className = 'size';
        sz.textContent = fmtSize(item.size);
        meta.appendChild(nm); meta.appendChild(sz);
        var a = document.createElement('a');
        a.className = 'btn small';
        a.textContent = T('Unduh');
        a.href = '/api/download/' + encodeURIComponent(item.id) + authQuery();
        a.setAttribute('download', item.name);
        li.appendChild(ico); li.appendChild(meta); li.appendChild(a);
      }
      list.appendChild(li);
    });
  }

  /* ---------- Offers pushed from Android ---------- */
  var currentOffer = null;

  function wsSend(obj) {
    try { if (state.ws && state.ws.readyState === 1) state.ws.send(JSON.stringify(obj)); } catch (e) { }
  }

  function showOffer(msg) {
    var items = msg.items || [];
    if (!items.length) return;
    showTab('receive');
    currentOffer = items;
    el('offerTitle').textContent = (msg.message || 'Android') + T(' ingin mengirim');
    var total = items.reduce(function (a, it) { return a + (it.size > 0 ? it.size : 0); }, 0);
    el('offerSub').textContent = items.length + T(' item • ') + fmtSize(total);
    var list = el('offerList');
    list.innerHTML = '';
    items.slice(0, 6).forEach(function (it) {
      var li = document.createElement('li');
      li.className = 'row';
      var nm = document.createElement('div');
      nm.className = 'name';
      nm.textContent = it.type === 'text' ? (it.text || it.name) : it.name;
      li.appendChild(nm);
      list.appendChild(li);
    });
    if (items.length > 6) {
      var more = document.createElement('li');
      more.className = 'row size';
      more.textContent = T('dan ') + (items.length - 6) + T(' lainnya');
      list.appendChild(more);
    }
    var onlyText = items.length === 1 && items[0].type === 'text';
    el('offerAccept').textContent = onlyText ? T('Salin teks') : (items.length > 1 ? T('Unduh semua (ZIP)') : T('Unduh'));
    el('offerModal').className = 'modal-back show';
    el('offerAccept').focus();
  }

  function hideOffer() { el('offerModal').className = 'modal-back'; currentOffer = null; }

  el('offerDecline').addEventListener('click', function () { wsSend({ type: 'offer-declined' }); hideOffer(); });
  el('offerAccept').addEventListener('click', function () {
    var items = currentOffer || [];
    hideOffer();
    if (!items.length) return;
    wsSend({ type: 'offer-accepted' });
    if (items.length === 1 && items[0].type === 'text') { copyText(items[0].text || ''); return; }
    var a = document.createElement('a');
    if (items.length === 1) {
      a.href = '/api/download/' + encodeURIComponent(items[0].id) + authQuery();
      a.setAttribute('download', items[0].name);
    } else {
      a.href = '/api/download-all' + authQuery();
      a.setAttribute('download', 'Kirimly.zip');
    }
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    loadShared();
  });

  el('refreshBtn').addEventListener('click', function () { if (state.authed) loadShared(); else loadInfo(); });

  /* ---------- WebSocket live updates ---------- */
  function connectWs() {
    if (state.ws) { try { state.ws.close(); } catch (e) { } }
    var proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    var ws;
    var q = authQuery();
    q += (q ? '&' : '?') + 'name=' + encodeURIComponent(senderName());
    try { ws = new WebSocket(proto + location.host + '/ws' + q); } catch (e) { scheduleWs(); return; }
    state.ws = ws;
    ws.onopen = function () { el('wsDot').className = 'dot on'; };
    ws.onmessage = function (ev) {
      var msg = {};
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      if (msg.type === 'shared-updated') loadShared();
      if (msg.type === 'offer') showOffer(msg);
      if (msg.type === 'clip') showClip(msg.message || '', msg.sender || '');
      if (msg.type === 'name-changed' && msg.message) el('deviceName').textContent = msg.message;
      if (msg.type === 'pin-changed' && !(state.info && state.info.trusted)) handleUnauthorized();
      if (msg.type === 'server-stopping') toast(T('Server Android dihentikan.'), 'err');
      if (msg.type === 'address-changed') toast(T('Alamat Android berubah ke ') + msg.message + T('. Pindai ulang QR jika halaman berhenti merespons.'), 'err');
    };
    ws.onclose = function () { el('wsDot').className = 'dot'; scheduleWs(); };
    ws.onerror = function () { el('wsDot').className = 'dot'; };
  }

  function scheduleWs() {
    if (state.wsRetry) clearTimeout(state.wsRetry);
    state.wsRetry = setTimeout(function () { if (state.authed) connectWs(); }, 3000);
  }

  /* ---------- File selection (picker, folder, drop, paste) ---------- */
  function addEntry(file, relPath) {
    state.files.push({ file: file, rel: relPath || '' });
  }

  function addFiles(fileList, useRelative) {
    for (var i = 0; i < fileList.length; i++) {
      var f = fileList[i];
      addEntry(f, useRelative ? (f.webkitRelativePath || '') : '');
    }
    renderSelected();
  }

  function walkEntry(entry, prefix, done) {
    if (entry.isFile) {
      entry.file(function (f) { addEntry(f, prefix + f.name); done(); }, function () { done(); });
    } else if (entry.isDirectory) {
      var reader = entry.createReader(), all = [];
      var readBatch = function () {
        reader.readEntries(function (batch) {
          if (!batch.length) {
            var left = all.length;
            if (!left) { done(); return; }
            all.forEach(function (child) { walkEntry(child, prefix + entry.name + '/', function () { if (--left === 0) done(); }); });
          } else { all = all.concat(Array.prototype.slice.call(batch)); readBatch(); }
        }, function () { done(); });
      };
      readBatch();
    } else { done(); }
  }

  function renderSelected() {
    var list = el('selectedList');
    list.innerHTML = '';
    var total = 0;
    state.files.forEach(function (entry, idx) {
      var f = entry.file;
      total += f.size;
      var li = document.createElement('li');
      li.className = 'row';
      var ico = document.createElement('div');
      ico.className = 'ico';
      ico.textContent = extOf(f.name);
      var meta = document.createElement('div');
      meta.className = 'meta';
      var nm = document.createElement('div');
      nm.className = 'name';
      nm.textContent = entry.rel || f.name;
      var sz = document.createElement('div');
      sz.className = 'size';
      sz.id = 'size-' + idx;
      sz.textContent = fmtSize(f.size);
      meta.appendChild(nm); meta.appendChild(sz);
      var bar = document.createElement('div');
      bar.className = 'bar';
      bar.style.display = 'none';
      bar.id = 'bar-' + idx;
      bar.appendChild(document.createElement('span'));
      meta.appendChild(bar);
      var rm = document.createElement('button');
      rm.className = 'icon-btn';
      rm.type = 'button';
      rm.setAttribute('aria-label', T('Hapus ') + f.name);
      rm.textContent = '×';
      rm.disabled = state.busy;
      rm.addEventListener('click', function () {
        if (state.busy) return;
        state.files.splice(idx, 1);
        renderSelected();
      });
      li.appendChild(ico); li.appendChild(meta); li.appendChild(rm);
      list.appendChild(li);
    });
    var has = state.files.length > 0;
    el('selectedSummary').style.display = has ? 'flex' : 'none';
    el('selCount').textContent = state.files.length + T(' file');
    el('selSize').textContent = T('Total ') + fmtSize(total);
    el('sendBtn').disabled = !has || state.busy;
    el('clearBtn').style.display = has && !state.busy ? 'block' : 'none';
  }

  el('fileInput').addEventListener('change', function (e) { addFiles(e.target.files, false); e.target.value = ''; });
  el('folderInput').addEventListener('change', function (e) { addFiles(e.target.files, true); e.target.value = ''; });
  el('folderBtn').addEventListener('keydown', function (e) {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); el('folderInput').click(); }
  });

  var drop = el('drop');
  ['dragenter', 'dragover'].forEach(function (evt) {
    drop.addEventListener(evt, function (e) { e.preventDefault(); drop.classList.add('drag'); });
  });
  ['dragleave', 'drop'].forEach(function (evt) {
    drop.addEventListener(evt, function (e) { e.preventDefault(); drop.classList.remove('drag'); });
  });
  drop.addEventListener('drop', function (e) {
    var dt = e.dataTransfer;
    if (!dt) return;
    var items = dt.items ? Array.prototype.slice.call(dt.items) : [];
    var entries = items.map(function (it) { return it.webkitGetAsEntry ? it.webkitGetAsEntry() : null; }).filter(Boolean);
    if (entries.length) {
      var left = entries.length;
      entries.forEach(function (entry) {
        walkEntry(entry, '', function () { if (--left === 0) renderSelected(); });
      });
    } else if (dt.files) {
      addFiles(dt.files, false);
    }
  });

  document.addEventListener('paste', function (e) {
    var target = e.target;
    if (target && (target.tagName === 'TEXTAREA' || target.tagName === 'INPUT')) return;
    var files = e.clipboardData && e.clipboardData.files;
    if (!files || !files.length) return;
    e.preventDefault();
    for (var i = 0; i < files.length; i++) {
      var f = files[i];
      var name = f.name && f.name !== 'image.png' ? f.name : 'tempel-' + Date.now() + '-' + (i + 1) + '.' + ((f.type || 'image/png').split('/')[1] || 'png');
      var renamed = f;
      try { renamed = new File([f], name, { type: f.type }); } catch (err) { renamed = f; }
      addEntry(renamed, '');
    }
    renderSelected();
    toast(files.length + T(' file ditempel'), 'ok');
  });

  el('clearBtn').addEventListener('click', function () {
    if (state.busy) return;
    state.files = [];
    renderSelected();
    el('overall').style.display = 'none';
  });

  /* ---------- Upload flow ---------- */
  function setBusy(busy) {
    state.busy = busy;
    el('sendBtn').textContent = busy ? T('Mengirim…') : T('Kirim');
    renderSelected();
  }

  function showApproval(show, text) {
    el('approvalBox').className = 'status-box' + (show ? ' show' : '');
    if (text) el('approvalText').textContent = text;
  }

  function setBar(bar, pct, cls) {
    if (!bar) return;
    bar.style.display = 'block';
    bar.className = 'bar' + (cls ? ' ' + cls : '');
    bar.firstChild.style.width = Math.max(0, Math.min(100, pct)) + '%';
  }

  /* Waits ms, but wakes early when the page becomes visible again (screen unlocked). */
  function pause(ms) {
    return new Promise(function (resolve) {
      var timer = setTimeout(finish, ms);
      function finish() { clearTimeout(timer); state.wakers = state.wakers.filter(function (w) { return w !== finish; }); resolve(); }
      state.wakers.push(finish);
    });
  }

  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) state.wakers.slice().forEach(function (w) { w(); });
  });

  function readBlob(blob) {
    if (blob.arrayBuffer) return blob.arrayBuffer();
    return new Promise(function (resolve, reject) {
      var fr = new FileReader();
      fr.onload = function () { resolve(fr.result); };
      fr.onerror = function () { reject(fr.error); };
      fr.readAsArrayBuffer(blob);
    });
  }

  function xhrSend(method, url, headers, body, onProgress) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open(method, url, true);
      Object.keys(headers).forEach(function (k) { xhr.setRequestHeader(k, headers[k]); });
      if (onProgress) xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(e.loaded); };
      xhr.onload = function () {
        var data = {};
        try { data = xhr.responseText ? JSON.parse(xhr.responseText) : {}; } catch (e) { data = {}; }
        resolve({ status: xhr.status, data: data });
      };
      xhr.onerror = function () { reject(new Error('network')); };
      xhr.onabort = function () { reject(new Error('abort')); };
      xhr.ontimeout = function () { reject(new Error('timeout')); };
      xhr.timeout = 120000;
      xhr.send(body);
    });
  }

  function jsonPost(path, body) {
    return api('POST', path, body).then(function (res) {
      if (!res.ok) { var err = new Error(T(res.data && res.data.message) || ('HTTP ' + res.status)); err.status = res.status; throw err; }
      return res.data;
    });
  }

  /* Resumable upload: init, 4 MB chunks with SHA-256 each, resume from the server offset. */
  function uploadChunked(entry, index, token, onProgress) {
    var file = entry.file;
    var hasher = new Sha256();
    var hashed = 0;
    var hashValid = true;
    var retries = 0;
    var uploadId = null;
    var chunkSize = 8 * 1024 * 1024;

    function retryOr(err) {
      retries++;
      if (retries > MAX_RETRIES) throw err;
      var wait = Math.min(1000 * Math.pow(2, Math.min(retries, 4)), 15000);
      return pause(wait).then(function () {
        return api('GET', '/api/upload/status?id=' + encodeURIComponent(uploadId)).then(function (res) {
          if (res.status === 404) throw new Error(T('Sesi upload kedaluwarsa'));
          return res.data.offset;
        }, function () { return null; });
      });
    }

    function catchUpHash(target) {
      if (!hashValid || target === hashed) return Promise.resolve();
      if (target < hashed) { hashValid = false; return Promise.resolve(); }
      return readBlob(file.slice(hashed, target)).then(function (buf) { hasher.update(new Uint8Array(buf)); hashed = target; });
    }

    function sendFrom(offset) {
      if (offset >= file.size) return Promise.resolve();
      var end = Math.min(offset + chunkSize, file.size);
      return readBlob(file.slice(offset, end)).then(function (buf) {
        var bytes = new Uint8Array(buf);
        var url = '/api/upload/chunk?id=' + encodeURIComponent(uploadId) + '&offset=' + offset;
        var sending = xhrSend('POST', url, { 'Content-Type': 'application/octet-stream' }, buf, function (loaded) {
          onProgress(offset + loaded);
        });
        /* Hash this chunk while the browser is still uploading it; commit only if the server accepts. */
        var tentative = (hashValid && hashed === offset) ? hasher.clone().update(bytes) : null;
        return sending.then(function (res) {
          if (res.status >= 200 && res.status < 300) {
            retries = 0;
            if (tentative && hashed === offset) { hasher = tentative; hashed = end; }
            onProgress(res.data.offset);
            return sendFrom(res.data.offset);
          }
          if (res.status === 409) return catchUpHash(res.data.offset).then(function () { return sendFrom(res.data.offset); });
          if (res.status === 404) throw new Error(T('Sesi upload kedaluwarsa'));
          return retryOr(new Error('HTTP ' + res.status)).then(function (next) { return sendFrom(next === null ? offset : next); });
        }, function (err) {
          return retryOr(err).then(function (next) {
            var resume = next === null ? offset : next;
            return catchUpHash(resume).then(function () { return sendFrom(resume); });
          });
        });
      });
    }

    var initBody = {
      token: token, index: index, name: file.name, size: file.size,
      mimeType: file.type || 'application/octet-stream', relativePath: entry.rel || null
    };
    function init() {
      return jsonPost('/api/upload/init', initBody).catch(function (err) {
        if (err.status && err.status < 500) throw err;
        retries++;
        if (retries > MAX_RETRIES) throw err;
        return pause(2000).then(init);
      });
    }
    return init().then(function (res) {
      uploadId = res.uploadId;
      chunkSize = res.chunkSize || chunkSize;
      return catchUpHash(res.offset).then(function () { return sendFrom(res.offset); });
    }).then(function () {
      var body = hashValid && hashed === file.size ? { sha256: hasher.hex() } : {};
      return jsonPost('/api/upload/complete?id=' + encodeURIComponent(uploadId), body);
    });
  }

  /* Legacy single-request multipart upload for older Kirimly servers. */
  function uploadLegacy(entry, index, token, onProgress) {
    var fd = new FormData();
    fd.append('file', entry.file, entry.file.name);
    var url = '/api/upload?token=' + encodeURIComponent(token) + '&index=' + index + '&name=' + encodeURIComponent(entry.file.name);
    return xhrSend('POST', url, { 'X-Kirimly-Token': token }, fd, onProgress).then(function (res) {
      if (res.status < 200 || res.status >= 300) throw new Error('HTTP ' + res.status);
    });
  }

  el('sendBtn').addEventListener('click', function () {
    if (state.busy || !state.files.length) return;
    if (!state.authed && state.info && state.info.pinRequired && !state.info.trusted) { showPin(); return; }
    var entries = state.files.slice();
    setBusy(true);
    showApproval(true, T('Menunggu persetujuan di ') + ((state.info && state.info.deviceName) || 'Android') + '…');
    var meta = entries.map(function (en) { return { name: en.file.name, size: en.file.size, mimeType: en.file.type || 'application/octet-stream' }; });
    api('POST', '/api/request-upload', { deviceName: senderName(), deviceId: state.deviceId, files: meta }).then(function (res) {
      showApproval(false);
      if (res.status === 401) { setBusy(false); handleUnauthorized(); return; }
      if (res.status === 403) { setBusy(false); toast(T('Permintaan ditolak oleh Android.'), 'err'); return; }
      if (res.status === 408) { setBusy(false); toast(T('Tidak ada respons. Coba lagi.'), 'err'); return; }
      if (!res.ok || !res.data.token) { setBusy(false); toast(T(res.data && res.data.message) || T('Gagal memulai transfer.'), 'err'); return; }
      uploadAll(entries, res.data.token);
    }).catch(function () {
      showApproval(false);
      setBusy(false);
      toast(T('Koneksi terputus.'), 'err');
    });
  });

  function uploadAll(entries, token) {
    var chunked = hasFeature('chunked-v2');
    var totalBytes = entries.reduce(function (s, en) { return s + en.file.size; }, 0) || 1;
    var progress = entries.map(function () { return 0; });
    var started = Date.now();
    var failures = 0, nextIndex = 0, finished = 0;
    el('overall').style.display = 'block';
    el('keepAwake').style.display = 'block';
    setBar(el('overallBar'), 0);

    function refreshOverall() {
      var loaded = progress.reduce(function (a, b) { return a + b; }, 0);
      setBar(el('overallBar'), (loaded / totalBytes) * 100);
      var secs = (Date.now() - started) / 1000;
      el('overallLabel').textContent = finished + T(' dari ') + entries.length + T(' selesai');
      if (secs > 0.3) el('overallSpeed').textContent = fmtSpeed(loaded / secs) + ' • ' + Math.round((loaded / totalBytes) * 100) + '%';
    }

    return new Promise(function (resolveAll) {
      function worker() {
        if (nextIndex >= entries.length) return Promise.resolve();
        var i = nextIndex++;
        var entry = entries[i];
        var bar = el('bar-' + i);
        var fn = chunked ? uploadChunked : uploadLegacy;
        return fn(entry, i, token, function (loaded) {
          progress[i] = Math.min(loaded, entry.file.size);
          setBar(bar, entry.file.size ? (progress[i] / entry.file.size) * 100 : 100);
          refreshOverall();
        }).then(function () {
          progress[i] = entry.file.size;
          setBar(bar, 100, 'done');
        }, function (err) {
          failures++;
          setBar(bar, 100, 'fail');
          var sz = el('size-' + i);
          if (sz) sz.textContent = T('Gagal: ') + (err && err.message ? err.message : 'koneksi');
        }).then(function () {
          finished++;
          refreshOverall();
          return worker();
        });
      }
      var pool = [];
      for (var w = 0; w < Math.min(CONCURRENCY, entries.length); w++) pool.push(worker());
      Promise.all(pool).then(resolveAll);
    }).then(function () {
      setBar(el('overallBar'), 100, failures ? 'fail' : 'done');
      el('overallLabel').textContent = failures ? (entries.length - failures) + T(' dari ') + entries.length + T(' file terkirim') : T('Semua file terkirim');
      el('overallSpeed').textContent = '';
      el('keepAwake').style.display = 'none';
      toast(failures ? T('Sebagian file gagal dikirim.') : T('Berhasil dikirim!'), failures ? 'err' : 'ok');
      state.files = [];
      setBusy(false);
    });
  }

  window.addEventListener('beforeunload', function (e) {
    if (state.busy) { e.preventDefault(); e.returnValue = ''; }
  });

  /* ---------- Text ---------- */
  el('sendTextBtn').addEventListener('click', function () {
    var text = el('textInput').value;
    if (!text.trim()) { toast(T('Teks masih kosong.'), 'err'); return; }
    if (!state.authed && state.info && state.info.pinRequired && !state.info.trusted) { showPin(); return; }
    var btn = el('sendTextBtn');
    btn.disabled = true;
    api('POST', '/api/text', { text: text, deviceName: senderName(), deviceId: state.deviceId }).then(function (res) {
      btn.disabled = false;
      if (res.status === 401) { handleUnauthorized(); return; }
      if (res.ok) { el('textInput').value = ''; toast(T('Teks terkirim & disalin di Android'), 'ok'); }
      else toast(T(res.data && res.data.message) || T('Gagal mengirim teks.'), 'err');
    }).catch(function () { btn.disabled = false; toast(T('Koneksi terputus.'), 'err'); });
  });

  /* ---------- Send / Receive tabs ---------- */
  var INTRO = {
    send: ['Kirim file ke Android, langsung dari Safari.', 'Tanpa aplikasi, tanpa internet. Cukup satu jaringan Wi-Fi atau hotspot.'],
    receive: ['Ambil file yang dibagikan dari Android.', 'Pilih file di Android, lalu ketuk iPhone ini di daftar tujuan. Atau unduh yang sudah dibagikan di bawah.']
  };
  function showTab(name) {
    var send = name !== 'receive';
    el('paneSend').hidden = !send;
    el('paneReceive').hidden = send;
    el('tabSend').className = 'tab' + (send ? ' active' : '');
    el('tabReceive').className = 'tab' + (send ? '' : ' active');
    el('tabSend').setAttribute('aria-selected', send ? 'true' : 'false');
    el('tabReceive').setAttribute('aria-selected', send ? 'false' : 'true');
    var intro = send ? INTRO.send : INTRO.receive;
    el('introTitle').textContent = T(intro[0]);
    el('introSub').textContent = T(intro[1]);
    storeSet('kirimly_tab', send ? 'send' : 'receive');
  }
  el('tabSend').addEventListener('click', function () { showTab('send'); });
  el('tabReceive').addEventListener('click', function () { showTab('receive'); loadShared(); });

  /* ---------- Intro animation: about 6 s ---------- */
  var splashDone = false;
  function endSplash() {
    if (splashDone) return;
    splashDone = true;
    var sp = el('splash');
    if (!sp) return;
    sp.className = 'splash hide';
    setTimeout(function () { if (sp.parentNode) sp.parentNode.removeChild(sp); }, 700);
    var first = document.querySelector('#pinModal.show input');
    if (first) first.focus();
  }
  function splashStatus(text, ok) {
    var st = el('splashStatus');
    if (!st) return;
    st.textContent = text;
    var bar = el('splashBar');
    if (bar && ok) bar.className = 'splash-bar ok';
  }
  setTimeout(endSplash, 6000);

  /* ---------- Boot ---------- */
  translatePage();
  showTab(storeGet('kirimly_tab') === 'receive' ? 'receive' : 'send');
  state.deviceId = storeGet('kirimly_device') || '';
  if (!state.deviceId) { state.deviceId = makeDeviceId(); storeSet('kirimly_device', state.deviceId); }
  var savedName = storeGet('kirimly_sender');
  if (savedName) el('senderName').value = savedName;
  el('senderName').addEventListener('change', function () { storeSet('kirimly_sender', el('senderName').value.trim()); });
  renderSelected();
  loadInfo();
})();
</script>
</body>
</html>
"""
}

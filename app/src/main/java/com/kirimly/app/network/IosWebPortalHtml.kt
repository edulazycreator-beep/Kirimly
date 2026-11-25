package com.kirimly.app.network

/**
 * Self-contained, mobile-first web client served at `GET /` for iOS Safari (and any browser).
 * Pure HTML5 + CSS3 + vanilla JS – no external requests, works fully offline on a hotspot.
 *
 * NOTE: this raw string deliberately contains no dollar signs so Kotlin performs no templating.
 */
object IosWebPortalHtml {

    const val PAGE: String = """<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<meta name="theme-color" content="#1F5FD6">
<meta name="format-detection" content="telephone=no">
<title>Kirimly</title>
<style>
:root {
  --primary: #1F5FD6;
  --primary-soft: #DCE6FF;
  --on-primary: #FFFFFF;
  --bg: #F4F6FB;
  --card: #FFFFFF;
  --text: #171B24;
  --muted: #5F6676;
  --border: #E3E7EF;
  --success: #12875C;
  --danger: #D93F3F;
  --shadow: 0 6px 24px rgba(23, 27, 36, 0.06);
  --radius: 20px;
}
@media (prefers-color-scheme: dark) {
  :root {
    --primary: #8FB1FF;
    --primary-soft: #1E2F55;
    --on-primary: #0B1A3A;
    --bg: #0E1117;
    --card: #171B24;
    --text: #EEF1F7;
    --muted: #9AA2B4;
    --border: #262C3A;
    --success: #4FD1A0;
    --danger: #FF8A8A;
    --shadow: 0 6px 24px rgba(0, 0, 0, 0.35);
  }
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  background: var(--bg);
  color: var(--text);
  line-height: 1.45;
  -webkit-font-smoothing: antialiased;
  padding-bottom: env(safe-area-inset-bottom);
}
.wrap { max-width: 560px; margin: 0 auto; padding: 16px 16px 40px; }
header.hero {
  background: linear-gradient(135deg, #1F5FD6 0%, #3E7BF0 55%, #00A3B4 100%);
  color: #FFFFFF;
  border-radius: 0 0 28px 28px;
  padding: calc(env(safe-area-inset-top) + 22px) 20px 26px;
}
.hero-inner { max-width: 560px; margin: 0 auto; }
.brand { display: flex; align-items: center; gap: 12px; }
.logo {
  width: 44px; height: 44px; border-radius: 14px; background: rgba(255,255,255,0.18);
  display: flex; align-items: center; justify-content: center;
}
.logo svg { width: 26px; height: 26px; }
.brand h1 { font-size: 22px; margin: 0; letter-spacing: -0.3px; }
.brand p { margin: 2px 0 0; font-size: 13px; opacity: 0.85; }
.peer-pill {
  margin-top: 18px; display: flex; align-items: center; gap: 10px;
  background: rgba(255,255,255,0.16); border-radius: 999px; padding: 8px 14px; font-size: 14px;
}
.dot { width: 9px; height: 9px; border-radius: 50%; background: #FFC857; flex: none; }
.dot.on { background: #4CFFB2; box-shadow: 0 0 0 4px rgba(76,255,178,0.2); }
.card {
  background: var(--card); border-radius: var(--radius); padding: 18px; margin-top: 14px;
  box-shadow: var(--shadow); border: 1px solid var(--border);
}
.card h2 { font-size: 17px; margin: 0 0 4px; letter-spacing: -0.2px; }
.card .sub { color: var(--muted); font-size: 13px; margin: 0 0 14px; }
.drop {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  border: 2px dashed var(--border); border-radius: 16px; padding: 24px 12px; text-align: center;
  color: var(--muted); cursor: pointer; transition: border-color .2s, background .2s;
}
.drop.drag { border-color: var(--primary); background: var(--primary-soft); }
.drop strong { color: var(--primary); font-size: 15px; }
.drop svg { width: 34px; height: 34px; color: var(--primary); }
input[type=file] { display: none; }
.list { list-style: none; margin: 12px 0 0; padding: 0; }
.row {
  display: flex; align-items: center; gap: 12px; padding: 10px 0; border-top: 1px solid var(--border);
}
.row:first-child { border-top: none; }
.ico {
  width: 38px; height: 38px; border-radius: 12px; background: var(--primary-soft); color: var(--primary);
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex: none;
  text-transform: uppercase;
}
.meta { flex: 1; min-width: 0; }
.name { font-size: 14px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.size { font-size: 12px; color: var(--muted); }
.text-preview {
  font-size: 13px; color: var(--text); white-space: pre-wrap; word-break: break-word;
  max-height: 84px; overflow: hidden;
}
.btn {
  appearance: none; border: none; border-radius: 14px; padding: 13px 18px; font-size: 15px; font-weight: 600;
  background: var(--primary); color: var(--on-primary); cursor: pointer; width: 100%;
  transition: transform .08s ease, opacity .2s;
}
.btn:active { transform: scale(0.98); }
.btn[disabled] { opacity: 0.45; }
.btn.ghost { background: var(--primary-soft); color: var(--primary); }
.btn.small { width: auto; padding: 8px 14px; font-size: 13px; border-radius: 12px; text-decoration: none; display: inline-block; }
.icon-btn {
  appearance: none; border: none; background: transparent; color: var(--muted); font-size: 20px;
  width: 34px; height: 34px; border-radius: 10px; cursor: pointer; flex: none;
}
.actions { display: flex; gap: 10px; margin-top: 14px; }
.summary { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); margin-top: 10px; }
textarea, input.field {
  width: 100%; border: 1px solid var(--border); border-radius: 14px; padding: 12px 14px; font-size: 16px;
  background: transparent; color: var(--text); font-family: inherit; outline: none; resize: vertical;
}
textarea { min-height: 96px; }
textarea:focus, input.field:focus { border-color: var(--primary); }
.bar { height: 8px; background: var(--border); border-radius: 999px; overflow: hidden; margin-top: 6px; }
.bar > span { display: block; height: 100%; width: 0; background: var(--primary); border-radius: 999px; transition: width .15s linear; }
.bar.done > span { background: var(--success); }
.bar.fail > span { background: var(--danger); }
.prog-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--muted); margin-top: 4px; }
.status-box {
  margin-top: 12px; padding: 12px 14px; border-radius: 14px; background: var(--primary-soft);
  color: var(--primary); font-size: 14px; display: none; align-items: center; gap: 10px;
}
.status-box.show { display: flex; }
.spinner {
  width: 16px; height: 16px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent;
  animation: spin .8s linear infinite; flex: none;
}
@keyframes spin { to { transform: rotate(360deg); } }
.empty { text-align: center; color: var(--muted); font-size: 14px; padding: 14px 0 4px; }
.toast {
  position: fixed; left: 50%; bottom: calc(env(safe-area-inset-bottom) + 22px); transform: translateX(-50%) translateY(30px);
  background: #171B24; color: #FFFFFF; padding: 12px 18px; border-radius: 14px; font-size: 14px; opacity: 0;
  transition: all .25s ease; pointer-events: none; max-width: 88vw; text-align: center; z-index: 50;
}
.toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }
.toast.err { background: var(--danger); }
.toast.ok { background: var(--success); }
.modal-back {
  position: fixed; inset: 0; background: rgba(8, 12, 20, 0.55); display: none; align-items: center;
  justify-content: center; z-index: 40; padding: 20px;
}
.modal-back.show { display: flex; }
.modal { background: var(--card); border-radius: 24px; padding: 24px 20px; width: 100%; max-width: 360px; text-align: center; }
.modal h3 { margin: 0 0 6px; font-size: 19px; }
.modal p { margin: 0 0 18px; color: var(--muted); font-size: 14px; }
.pin-inputs { display: flex; gap: 10px; justify-content: center; margin-bottom: 14px; }
.pin-inputs input {
  width: 54px; height: 62px; text-align: center; font-size: 26px; font-weight: 700; border-radius: 14px;
  border: 1.5px solid var(--border); background: transparent; color: var(--text); outline: none;
}
.pin-inputs input:focus { border-color: var(--primary); }
.shake { animation: shake .4s; }
@keyframes shake { 20%, 60% { transform: translateX(-8px); } 40%, 80% { transform: translateX(8px); } }
.err-text { color: var(--danger); font-size: 13px; min-height: 18px; margin-bottom: 8px; }
.label { font-size: 13px; color: var(--muted); margin: 0 0 6px; display: block; }
.footer { text-align: center; color: var(--muted); font-size: 12px; margin-top: 22px; }
</style>
</head>
<body>
<header class="hero">
  <div class="hero-inner">
    <div class="brand">
      <div class="logo">
        <svg viewBox="0 0 24 24" fill="none"><path d="M2.5 11.2L21.5 3.5L15.2 21.5L11.6 13.9Z" fill="#FFFFFF"/><path d="M11.6 13.9L21.5 3.5L12.8 15.6Z" fill="#DCE6FF"/></svg>
      </div>
      <div>
        <h1>Kirimly</h1>
        <p>Transfer cepat tanpa aplikasi</p>
      </div>
    </div>
    <div class="peer-pill"><span id="wsDot" class="dot"></span><span>Terhubung ke <b id="deviceName">Android</b></span></div>
  </div>
</header>

<main class="wrap">
  <section class="card">
    <h2>Kirim file ke Android</h2>
    <p class="sub">Foto, video, dokumen, atau arsip — sebanyak yang kamu mau.</p>
    <label id="drop" class="drop" for="fileInput">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4"/><path d="M7 9l5-5 5 5"/><path d="M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/></svg>
      <strong>Pilih file</strong>
      <span>atau seret &amp; lepas di sini</span>
    </label>
    <input id="fileInput" type="file" multiple>
    <ul id="selectedList" class="list"></ul>
    <div id="selectedSummary" class="summary" style="display:none"><span id="selCount"></span><span id="selSize"></span></div>
    <div id="approvalBox" class="status-box"><span class="spinner"></span><span id="approvalText">Menunggu persetujuan di Android…</span></div>
    <div id="overall" style="display:none; margin-top:12px">
      <div class="prog-meta"><span id="overallLabel">Mengunggah…</span><span id="overallSpeed"></span></div>
      <div class="bar" id="overallBar"><span></span></div>
    </div>
    <div class="actions">
      <button id="clearBtn" class="btn ghost" type="button" style="display:none">Bersihkan</button>
      <button id="sendBtn" class="btn" type="button" disabled>Kirim</button>
    </div>
  </section>

  <section class="card">
    <h2>Kirim teks atau tautan</h2>
    <p class="sub">Langsung masuk ke clipboard &amp; riwayat Android.</p>
    <textarea id="textInput" placeholder="Ketik atau tempel teks / URL…"></textarea>
    <div class="actions"><button id="sendTextBtn" class="btn" type="button">Kirim Teks</button></div>
  </section>

  <section class="card">
    <div style="display:flex; align-items:center; justify-content:space-between; gap:8px">
      <div>
        <h2>Dibagikan dari Android</h2>
        <p class="sub" style="margin-bottom:0">Ketuk untuk mengunduh.</p>
      </div>
      <button id="refreshBtn" class="btn small ghost" type="button">Muat ulang</button>
    </div>
    <ul id="sharedList" class="list"></ul>
    <div id="sharedEmpty" class="empty">Belum ada file yang dibagikan.</div>
  </section>

  <section class="card">
    <label class="label" for="senderName">Nama perangkatmu (opsional)</label>
    <input id="senderName" class="field" type="text" maxlength="40" placeholder="mis. iPhone Rina">
  </section>

  <p class="footer">Kirimly • Jaringan lokal, tanpa internet</p>
</main>

<div id="pinModal" class="modal-back">
  <div class="modal" id="pinCard">
    <h3>Masukkan PIN</h3>
    <p>Lihat PIN 4 digit di layar Terima pada perangkat Android.</p>
    <div class="pin-inputs">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1" autocomplete="one-time-code">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1">
      <input inputmode="numeric" pattern="[0-9]*" maxlength="1">
    </div>
    <div id="pinError" class="err-text"></div>
    <button id="pinSubmit" class="btn" type="button">Hubungkan</button>
  </div>
</div>

<div id="toast" class="toast"></div>

<script>
(function () {
  'use strict';

  var state = { pin: '', info: null, files: [], busy: false, ws: null, wsRetry: null, authed: false };

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
    toastTimer = setTimeout(function () { t.className = 'toast'; }, 2800);
  }

  function guessDevice() {
    var ua = navigator.userAgent || '';
    if (/iPhone/.test(ua)) return 'iPhone';
    if (/iPad/.test(ua)) return 'iPad';
    if (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1) return 'iPad';
    if (/Macintosh/.test(ua)) return 'Mac';
    if (/Android/.test(ua)) return 'Android';
    if (/Windows/.test(ua)) return 'Windows PC';
    return 'Perangkat Web';
  }

  function senderName() {
    var v = el('senderName').value.trim();
    return v || guessDevice();
  }

  function storeGet(key) { try { return window.localStorage.getItem(key); } catch (e) { return null; } }
  function storeSet(key, value) { try { window.localStorage.setItem(key, value); } catch (e) { } }

  function authHeaders(extra) {
    var h = extra || {};
    if (state.pin) h['X-Kirimly-Pin'] = state.pin;
    return h;
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

  function submitPin() {
    var pin = pinInputs.map(function (i) { return i.value; }).join('');
    if (pin.length !== 4) { el('pinError').textContent = 'PIN harus 4 digit.'; return; }
    state.pin = pin;
    api('POST', '/api/verify-pin').then(function (res) {
      if (res.ok) {
        storeSet('kirimly_pin', pin);
        hidePin();
        afterAuth();
      } else {
        state.pin = '';
        var card = el('pinCard');
        card.classList.remove('shake');
        void card.offsetWidth;
        card.classList.add('shake');
        el('pinError').textContent = (res.data && res.data.message) || 'PIN salah, coba lagi.';
        pinInputs.forEach(function (i) { i.value = ''; });
        pinInputs[0].focus();
      }
    }).catch(function () { el('pinError').textContent = 'Tidak dapat terhubung ke Android.'; });
  }

  el('pinSubmit').addEventListener('click', submitPin);

  /* ---------- Info & auth ---------- */
  function loadInfo() {
    api('GET', '/api/info').then(function (res) {
      state.info = res.data || {};
      el('deviceName').textContent = state.info.deviceName || 'Android';
      document.title = 'Kirimly • ' + (state.info.deviceName || 'Android');
      if (state.info.pinRequired) {
        var saved = storeGet('kirimly_pin');
        if (saved && saved.length === 4) {
          state.pin = saved;
          api('POST', '/api/verify-pin').then(function (r) {
            if (r.ok) afterAuth(); else { state.pin = ''; showPin(); }
          }).catch(function () { showPin(); });
        } else {
          showPin();
        }
      } else {
        afterAuth();
      }
    }).catch(function () {
      toast('Tidak dapat terhubung ke Android. Pastikan server aktif.', 'err');
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
    showPin('PIN berubah atau tidak valid.');
  }

  /* ---------- Shared from Android ---------- */
  function loadShared() {
    api('GET', '/api/shared-files').then(function (res) {
      if (res.status === 401) { handleUnauthorized(); return; }
      renderShared((res.data && res.data.items) || []);
    }).catch(function () { toast('Gagal memuat daftar file.', 'err'); });
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
      toast(ok ? 'Teks disalin' : 'Salin manual: tekan lama teksnya', ok ? 'ok' : 'err');
    }
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(function () { toast('Teks disalin', 'ok'); }, fallback);
    } else {
      fallback();
    }
  }

  function renderShared(items) {
    var list = el('sharedList');
    list.innerHTML = '';
    el('sharedEmpty').style.display = items.length ? 'none' : 'block';
    items.forEach(function (item) {
      var li = document.createElement('li');
      li.className = 'row';
      var ico = document.createElement('div');
      ico.className = 'ico';
      ico.textContent = item.type === 'text' ? 'TXT' : extOf(item.name);
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
        btn.textContent = 'Salin';
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
        a.textContent = 'Unduh';
        a.href = '/api/download/' + encodeURIComponent(item.id) + (state.pin ? '?pin=' + encodeURIComponent(state.pin) : '');
        a.setAttribute('download', item.name);
        li.appendChild(ico); li.appendChild(meta); li.appendChild(a);
      }
      list.appendChild(li);
    });
  }

  el('refreshBtn').addEventListener('click', function () { if (state.authed) loadShared(); else loadInfo(); });

  /* ---------- WebSocket live updates ---------- */
  function connectWs() {
    if (state.ws) { try { state.ws.close(); } catch (e) { } }
    var proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    var url = proto + location.host + '/ws' + (state.pin ? '?pin=' + encodeURIComponent(state.pin) : '');
    var ws;
    try { ws = new WebSocket(url); } catch (e) { scheduleWs(); return; }
    state.ws = ws;
    ws.onopen = function () { el('wsDot').className = 'dot on'; };
    ws.onmessage = function (ev) {
      var msg = {};
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      if (msg.type === 'shared-updated') loadShared();
      if (msg.type === 'pin-changed') handleUnauthorized();
      if (msg.type === 'server-stopping') toast('Server Android dihentikan.', 'err');
    };
    ws.onclose = function () { el('wsDot').className = 'dot'; scheduleWs(); };
    ws.onerror = function () { el('wsDot').className = 'dot'; };
  }

  function scheduleWs() {
    if (state.wsRetry) clearTimeout(state.wsRetry);
    state.wsRetry = setTimeout(function () { if (state.authed) connectWs(); }, 3000);
  }

  /* ---------- File selection ---------- */
  function addFiles(fileList) {
    for (var i = 0; i < fileList.length; i++) state.files.push(fileList[i]);
    renderSelected();
  }

  function renderSelected() {
    var list = el('selectedList');
    list.innerHTML = '';
    var total = 0;
    state.files.forEach(function (f, idx) {
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
      nm.textContent = f.name;
      var sz = document.createElement('div');
      sz.className = 'size';
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
      rm.setAttribute('aria-label', 'Hapus');
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
    el('selCount').textContent = state.files.length + ' file';
    el('selSize').textContent = 'Total ' + fmtSize(total);
    el('sendBtn').disabled = !has || state.busy;
    el('clearBtn').style.display = has && !state.busy ? 'block' : 'none';
  }

  el('fileInput').addEventListener('change', function (e) {
    addFiles(e.target.files);
    e.target.value = '';
  });

  var drop = el('drop');
  ['dragenter', 'dragover'].forEach(function (evt) {
    drop.addEventListener(evt, function (e) { e.preventDefault(); drop.classList.add('drag'); });
  });
  ['dragleave', 'drop'].forEach(function (evt) {
    drop.addEventListener(evt, function (e) { e.preventDefault(); drop.classList.remove('drag'); });
  });
  drop.addEventListener('drop', function (e) {
    if (e.dataTransfer && e.dataTransfer.files) addFiles(e.dataTransfer.files);
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
    el('sendBtn').textContent = busy ? 'Mengirim…' : 'Kirim';
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

  el('sendBtn').addEventListener('click', function () {
    if (state.busy || !state.files.length) return;
    if (!state.authed && state.info && state.info.pinRequired) { showPin(); return; }
    var files = state.files.slice();
    setBusy(true);
    showApproval(true, 'Menunggu persetujuan di ' + ((state.info && state.info.deviceName) || 'Android') + '…');
    var meta = files.map(function (f) { return { name: f.name, size: f.size, mimeType: f.type || 'application/octet-stream' }; });
    api('POST', '/api/request-upload', { deviceName: senderName(), files: meta }).then(function (res) {
      showApproval(false);
      if (res.status === 401) { setBusy(false); handleUnauthorized(); return; }
      if (res.status === 403) { setBusy(false); toast('Permintaan ditolak oleh Android.', 'err'); return; }
      if (res.status === 408) { setBusy(false); toast('Tidak ada respons. Coba lagi.', 'err'); return; }
      if (!res.ok || !res.data.token) { setBusy(false); toast((res.data && res.data.message) || 'Gagal memulai transfer.', 'err'); return; }
      uploadAll(files, res.data.token);
    }).catch(function () {
      showApproval(false);
      setBusy(false);
      toast('Koneksi terputus.', 'err');
    });
  });

  function uploadAll(files, token) {
    var totalBytes = files.reduce(function (s, f) { return s + f.size; }, 0) || 1;
    var doneBytes = 0;
    var started = Date.now();
    var failures = 0;
    el('overall').style.display = 'block';
    setBar(el('overallBar'), 0);

    function next(i) {
      if (i >= files.length) {
        setBar(el('overallBar'), 100, failures ? 'fail' : 'done');
        el('overallLabel').textContent = failures ? (files.length - failures) + ' dari ' + files.length + ' file terkirim' : 'Semua file terkirim';
        el('overallSpeed').textContent = '';
        toast(failures ? 'Sebagian file gagal dikirim.' : 'Berhasil dikirim!', failures ? 'err' : 'ok');
        state.files = [];
        setBusy(false);
        return;
      }
      var file = files[i];
      var bar = el('bar-' + i);
      el('overallLabel').textContent = 'Mengunggah ' + (i + 1) + ' dari ' + files.length;
      uploadOne(file, i, token, function (loaded) {
        var pct = file.size ? (loaded / file.size) * 100 : 100;
        setBar(bar, pct);
        var overallLoaded = doneBytes + loaded;
        setBar(el('overallBar'), (overallLoaded / totalBytes) * 100);
        var secs = (Date.now() - started) / 1000;
        if (secs > 0.3) el('overallSpeed').textContent = fmtSpeed(overallLoaded / secs) + ' • ' + Math.round((overallLoaded / totalBytes) * 100) + '%';
      }).then(function () {
        setBar(bar, 100, 'done');
      }, function () {
        failures++;
        setBar(bar, 100, 'fail');
      }).then(function () {
        doneBytes += file.size;
        next(i + 1);
      });
    }
    next(0);
  }

  function uploadOne(file, index, token, onProgress) {
    return new Promise(function (resolve, reject) {
      var xhr = new XMLHttpRequest();
      var url = '/api/upload?token=' + encodeURIComponent(token) + '&index=' + index + '&name=' + encodeURIComponent(file.name);
      xhr.open('POST', url, true);
      xhr.setRequestHeader('X-Kirimly-Token', token);
      xhr.upload.onprogress = function (e) { if (e.lengthComputable) onProgress(Math.min(e.loaded, file.size)); };
      xhr.onload = function () { if (xhr.status >= 200 && xhr.status < 300) resolve(); else reject(new Error('HTTP ' + xhr.status)); };
      xhr.onerror = function () { reject(new Error('network')); };
      xhr.onabort = function () { reject(new Error('abort')); };
      var fd = new FormData();
      fd.append('file', file, file.name);
      xhr.send(fd);
    });
  }

  /* ---------- Text ---------- */
  el('sendTextBtn').addEventListener('click', function () {
    var text = el('textInput').value;
    if (!text.trim()) { toast('Teks masih kosong.', 'err'); return; }
    if (!state.authed && state.info && state.info.pinRequired) { showPin(); return; }
    var btn = el('sendTextBtn');
    btn.disabled = true;
    api('POST', '/api/text', { text: text, deviceName: senderName() }).then(function (res) {
      btn.disabled = false;
      if (res.status === 401) { handleUnauthorized(); return; }
      if (res.ok) { el('textInput').value = ''; toast('Teks terkirim & disalin di Android', 'ok'); }
      else toast((res.data && res.data.message) || 'Gagal mengirim teks.', 'err');
    }).catch(function () { btn.disabled = false; toast('Koneksi terputus.', 'err'); });
  });

  /* ---------- Boot ---------- */
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

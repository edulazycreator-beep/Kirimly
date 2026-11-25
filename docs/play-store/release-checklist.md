# Release checklist

- [ ] Signed release build (signing config / Play App Signing) — not set up yet in CI.
- [ ] Test the release (R8-minified) build on a real device: server, Safari upload, Nearby, hotspot.
- [ ] Publish `docs/privacy-policy.md` at a public URL and paste it into Play Console.
- [ ] Fill Data safety from `data-safety.md`; foreground-service and permission forms from `permissions.md`.
- [ ] Screenshots: phone (Send radar, Receive with QR, approval sheet with code, History) and the Safari portal; 1 feature graphic 1024×500.
- [ ] Store listing from `listing.md` (English default + Indonesian).
- [ ] Content rating questionnaire (Tools; no user-generated public content).
- [ ] Target API 35 ✔, versionCode increments with each CI run ✔.

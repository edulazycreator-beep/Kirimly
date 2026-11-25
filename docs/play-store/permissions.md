# Permission declarations (Play Console)

| Permission | Used for | Notes for review |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE` | Local HTTP server, mDNS discovery, joining an iPhone hotspot | No internet use. |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`) | Local-only hotspot, Nearby Connections (Android 13+) | Never used to derive location. |
| `ACCESS_FINE_LOCATION` / `COARSE` (max SDK 32) | Required by Android ≤12 for local-only hotspot and Bluetooth/Nearby discovery | Location is never read. |
| `BLUETOOTH_SCAN` / `ADVERTISE` / `CONNECT` | Nearby Connections discovery between Android devices | |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Keeps the transfer server alive while files move | Declare **Data sync** in the FGS form: user-initiated transfers, stops when idle. Video: show starting the server and a transfer. |
| `POST_NOTIFICATIONS` | Server status and Accept/Decline for incoming transfers | |
| `WAKE_LOCK` | Wi-Fi lock during transfers | |
| `WRITE_EXTERNAL_STORAGE` (max SDK 28) | Save to Downloads on Android 8–9 | |

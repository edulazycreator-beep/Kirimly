# Data safety form — suggested answers

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share user data? | **No** | Nothing is sent to the developer or third parties. Transfers go device-to-device only. |
| Is data encrypted in transit? | **Partly — answer “No” to be safe** | Android ↔ Android is E2E encrypted; browser (Safari) transfers use local HTTP. |
| Can users request deletion? | Yes (not required when no data is collected) | History and settings can be cleared in the app; uninstall removes everything. |
| Files & docs, Photos & videos | Not collected | Processed on device only (“ephemeral, on-device” is not collection). |
| Device or other IDs | Not collected | The random device ID stays on the device / local network. |
| Crash logs | Not collected | Stored locally; shared only if the user chooses to share. |

Google Play Services (Nearby Connections) is a Google SDK; check Google’s current SDK data disclosure when submitting.

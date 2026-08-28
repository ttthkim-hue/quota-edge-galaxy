# Quota Edge 🤖

**Galaxy-native Claude & Codex usage monitor** — 2-line glance (`75%/86%` + `142m` / `3.2d`), live API sync, overlay, lock screen widget.

![mockup](./assets/mockup-hero.png)

## Features

| Feature | Description |
|---------|-------------|
| **Usage sync** | Claude OAuth API + Codex WHAM API (same as TokenEater / llmquota) |
| **Glance format** | Line 1: `5h%/weekly%` + `142m` reset · Line 2: `3.2d` weekly reset |
| **Status overlay** | Top-left below clock (overlay permission) |
| **Lock screen** | Persistent notification + keyguard widget |
| **Home widget** | 2×2 Glance widget |
| **Foreground sync** | 60s refresh via foreground service |

## Display format

```
● C 75%/86%  142m
     3.2d
● X 45%/62%  089m
     2.1d
```

- `75%/86%` = 5-hour / weekly utilization
- `142m` = minutes until 5h window reset (`%03dm`)
- `3.2d` = days until weekly reset (`%.1fd`)
- Colors: Claude `#D97757` · Codex `#10A37F`

## Quick start (Galaxy)

### 1. Install APK

Download from [GitHub Releases](../../releases) or build locally:

```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Transfer to Galaxy → allow unknown sources → install.

### 2. Connect accounts

Open **Quota Edge** → paste tokens:

| Provider | Token source |
|----------|--------------|
| **Claude** | Claude Code OAuth token (Pro/Max/Team). From desktop: macOS Keychain / `~/.claude/.credentials.json` |
| **Codex** | `~/.codex/auth.json` → `access_token` + `account_id` |

Tap **저장 & 연동** → **지금 동기화**.

### 3. Enable glance

1. **알림 허용** — lock screen에서 quota 표시
2. **다른 앱 위에 표시** — status bar overlay (시간 아래)
3. **잠금화면 glance** ON
4. (Optional) 홈 화면에 **Quota Edge** 위젯 추가 — `keyguard` category 지원

### 4. Battery

Tap **배터리 최적화 제외** so 60s sync keeps running.

## API endpoints (read-only)

```
GET https://api.anthropic.com/api/oauth/usage
Authorization: Bearer <claude_oauth>
anthropic-beta: oauth-2025-04-20

GET https://chatgpt.com/backend-api/wham/usage
Authorization: Bearer <codex_oauth>
ChatGPT-Account-Id: <account_id>
```

Tokens stored in **EncryptedSharedPreferences** on device only.

## Project structure

```
quota-edge-galaxy/
├── android/          ← Kotlin + Compose app
├── mockups/          ← HTML concept previews
├── assets/           ← promo images
└── .github/workflows/build-apk.yml
```

## Build requirements

- JDK 17+
- Android SDK 35
- Android Studio Ladybug+ (recommended)

## Roadmap

- [x] Claude + Codex usage sync
- [x] 2-line glance UI
- [x] Overlay + widget + lock screen notification
- [ ] In-app OAuth WebView (no manual token paste)
- [ ] Samsung Edge Panel native panel
- [ ] Play Store release

## License

MIT

---

Inspired by [Vinz's macOS notch concept](https://x.com/hivinz_/status/2092996055248126353) · Built for Galaxy / One UI

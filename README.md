# Quota Edge 🤖

**Galaxy-native Claude & Codex usage monitor** — 2-line glance (`75%/86%` + `142m/3.2d`), live API sync, overlay, lock screen widget.

![mockup](./assets/mockup-hero.png)

## Features

| Feature | Description |
|---------|-------------|
| **Usage sync** | Claude OAuth API + Codex WHAM API (same as TokenEater / llmquota) |
| **Glance format** | Line 1: `5h%/weekly%` · Line 2: `142m/3.2d` reset |
| **Status overlay** | Top-left below clock (overlay permission) |
| **Lock screen** | Persistent notification + keyguard widget |
| **Home widget** | 2×2 Glance widget |
| **Foreground sync** | 60s refresh via foreground service |

## Display format

```
● C 75%/86%
     142m/3.2d
● X 45%/62%
     089m/2.1d
```

- `75%/86%` = 5-hour / weekly utilization
- `142m/3.2d` = 5h reset minutes / weekly reset days
- Colors: Claude `#D97757` · Codex `#10A37F`

## Quick start (Galaxy)

### 1. Build & install APK (로컬)

GitHub Actions 없이 **Android Studio** 또는 로컬 Gradle로 빌드합니다.

**Android Studio (권장)**

1. Android Studio에서 `android/` 폴더 열기
2. Galaxy 연결 또는 에뮬레이터 실행
3. Run ▶ 또는 `Build → Build APK(s)`

**CLI**

```bash
cd android
# local.properties에 SDK 경로 설정 (local.properties.example 참고)
./gradlew assembleDebug   # Windows: gradlew.bat assembleDebug
```

APK 경로: `android/app/build/outputs/apk/debug/app-debug.apk`

Galaxy로 옮겨 설치 (출처 unknown 허용).

### 2. Connect accounts

Open **Quota Edge** → paste tokens:

| Provider | Token source |
|----------|--------------|
| **Claude** | Claude Code OAuth token (Pro/Max/Team). Desktop: Keychain / `~/.claude/.credentials.json` |
| **Codex** | `~/.codex/auth.json` → `access_token` + `account_id` |

Tap **저장 & 연동** → **지금 동기화**.

### 3. Enable glance

1. **알림 허용** — lock screen에서 quota 표시
2. **다른 앱 위에 표시** — status bar overlay (시간 아래)
3. **잠금화면 glance** ON
4. (Optional) 홈 화면에 **Quota Edge** 위젯 추가

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
└── assets/           ← promo images
```

## Build requirements

- JDK 17+
- Android SDK 35
- Android Studio Ladybug+ (recommended)

## Roadmap

- [x] Claude + Codex usage sync
- [x] 2-line glance UI (`75%/86%` + `142m/3.2d`)
- [x] Overlay + widget + lock screen notification
- [ ] In-app OAuth WebView (no manual token paste)
- [ ] Samsung Edge Panel native panel
- [ ] Play Store release

## License

MIT

---

Inspired by [Vinz's macOS notch concept](https://x.com/hivinz_/status/2092996055248126353) · Built for Galaxy / One UI

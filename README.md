# Zerno

Android-приложение на Kotlin + Jetpack Compose.

## Как получить APK

1. GitHub → вкладка **Actions** → последний запуск **Android build**.
2. Внизу, в **Artifacts**, скачать `zerno-debug-apk` (zip с `app-debug.apk`).
3. Установить на телефон (разрешить установку из неизвестных источников).

## Локальная сборка

```bash
./gradlew assembleDebug
```

Нужны JDK 17+ и Android SDK (platform 37, build tools 36). Инструкции для Claude — в `CLAUDE.md`.

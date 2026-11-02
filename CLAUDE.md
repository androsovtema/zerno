# Zerno — Android-приложение

Отвечай пользователю по-русски. Пользователь работает в основном с телефона через облачные сессии Claude Code — делай изменения сам, коммить и пушь, давай короткие отчёты.

## Стек (версии проверены 2026-11-02)

- Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), Material 3, Navigation 3
- AGP 9.5.0 со встроенным Kotlin (плагин `org.jetbrains.kotlin.android` не нужен)
- Gradle 9.8.2 (wrapper), JDK 21 в облаке, байткод Java 17
- compileSdk / targetSdk 37, minSdk 24
- applicationId и namespace: `app.zerno`
- Версии зависимостей — только в `gradle/libs.versions.toml`

## Структура

- `app/src/main/kotlin/app/zerno/` — код (MainActivity, Navigation, ui/, data/)
- `app/src/test/` — юнит-тесты (JVM), `app/src/androidTest/` — инструментальные (нужен девайс/эмулятор, в облаке не запускаются)

## Команды

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # юнит-тесты
./gradlew lintDebug            # Android Lint
```

Android CLI (`android`, Google): `android docs ...` — поиск по официальной документации, `android sdk install <pkg>` — пакеты SDK. Подробнее — skill `android-cli`.

## Облачное окружение

- `.claude/hooks/session-start.sh` при старте облачной сессии ставит Android SDK в `~/android-sdk` и пишет `local.properties`. Если меняешь compileSdk или build tools — обнови `PACKAGES` в хуке.
- Maven Central (repo.maven.apache.org) отвечает 429 на облачные IP, поэтому в `settings.gradle.kts` первым стоит зеркало Google. Не убирай его.
- Эмулятора в облаке нет. Проверка — сборка, юнит-тесты, lint. Готовый APK пользователь скачивает из GitHub Actions (workflow `Android build`, артефакт `zerno-debug-apk`).

## Правила

- Ветка по умолчанию — `main`. CI собирает каждый пуш в `main` и каждый PR.
- Перед коммитом: `./gradlew assembleDebug testDebugUnitTest` должен проходить.
- Перед добавлением библиотеки проверяй её актуальную версию в репозитории (maven.google.com / Maven Central), а не по памяти.

# Zerno — Android-приложение

Отвечай пользователю по-русски. Пользователь работает в основном с телефона через облачные сессии Claude Code — делай изменения сам, коммить и пушь, давай короткие отчёты.

**Начни с `docs/HISTORY.md`** — журнал всех решений, проблем и обходов первой сессии, план следующих шагов.

APK последней сборки `main` (для установки на телефон): https://github.com/androsovtema/zerno/releases/latest/download/zerno.apk

## Стек (версии проверены 2026-11-02)

- Kotlin 2.4.21, Jetpack Compose (BOM 2026.09.00), Material 3, Navigation 3
- AGP 9.5.0 со встроенным Kotlin (плагин `org.jetbrains.kotlin.android` не нужен)
- Gradle 9.8.2 (wrapper), JDK 21 в облаке, байткод Java 17
- compileSdk / targetSdk 37, minSdk 29 (MediaStore без разрешений, физические камеры)
- applicationId и namespace: `app.zerno`
- Версии зависимостей — только в `gradle/libs.versions.toml`

## Структура

- `app/src/main/kotlin/app/zerno/raw/` — проявка RAW на чистом Kotlin без Android API (тестируется на JVM)
- `app/src/main/kotlin/app/zerno/camera/` — Camera2: сканер камер, RAW-съёмка, перевод метаданных в типы `raw`, сохранение в MediaStore
- `app/src/main/kotlin/app/zerno/ui/` — Compose-экраны (камера, редактор, галерея, настройки) и `ZernoViewModel`;
  экраны разделены на `*Screen` (ViewModel) и `*Content` (вёрстка от состояния, для скриншотов).
  `Brand.kt` — общие компоненты (логотип, профили, спуск, шкала экспозиции, кнопки, карточки настроек);
  `theme/` — палитра и шрифты (Onest, Unbounded только для логотипа). Интерфейс чистый, без зерна и текстур:
  зерно — в снимках и в знаке (решение пользователя, см. `docs/HISTORY.md` §10)
- `tools/brand/make_icons.py` — генерирует знак и иконку (камера из рассыпанных точек; нужны numpy и Pillow);
  `docs/brand/` — превью иконки
- `app/src/main/kotlin/app/zerno/` — `ZernoApp` (журнал, перехват падений), `Prefs`, `ShotRepository`
- `docs/research/` — исследования (Lampa, особенности устройств)
- `app/src/test/` — юнит-тесты (JVM), `app/src/androidTest/` — инструментальные (нужен девайс/эмулятор, в облаке не запускаются)

## Команды

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # юнит-тесты
./gradlew lintDebug            # Android Lint
```

Инструменты отладки без телефона (в CI не запускаются):

```bash
# Проявить настоящие DNG всеми профилями в PNG (папка/out). Образцы: raw.pixls.us (OnePlus, Xiaomi)
ZERNO_DNG_DIR=/путь/к/dng ./gradlew testDebugUnitTest --tests '*DngDevelopTool*'
# Скриншоты экранов (Robolectric). sample.png в папке подставляется как снимок
ZERNO_SHOTS_DIR=/путь ./gradlew testDebugUnitTest --tests '*ScreenshotTool*'
```

Android CLI (`android`, Google): `android docs ...` — поиск по официальной документации, `android sdk install <pkg>` — пакеты SDK. Подробнее — skill `android-cli`.

## Облачное окружение

- `.claude/hooks/session-start.sh` при старте облачной сессии ставит Android SDK в `~/android-sdk` и пишет `local.properties`. Если меняешь compileSdk или build tools — обнови `PACKAGES` в хуке.
- Maven Central (repo.maven.apache.org) отвечает 429 на облачные IP, поэтому в `settings.gradle.kts` первым стоит зеркало Google. Не убирай его.
- Эмулятора в облаке нет (нет KVM). Проверка — сборка, юнит-тесты, lint, проявка реальных DNG, скриншоты на JVM.
- CI на push в `main` публикует APK в релиз `latest-build` (ссылка выше). Подпись — `app/debug.keystore` из репозитория,
  чтобы обновления ставились поверх. Не меняй ключ без нужды: у тестировщиков придётся удалять приложение.

## Продукт

Камера в духе Lampa (iOS): RAW → проявка своими профилями, без HDR и ИИ. Версия 0.3.0 (айдентика и интерфейс — `docs/HISTORY.md` §10); первый отчёт с Oppo Find X9 Ultra разобран в `docs/HISTORY.md` §9.
Тестовое устройство: Oppo Find X9 Ultra (у тестировщицы Алины). У Oppo/realme/OnePlus ненадёжные цветовые данные:
матрица ISP бывает только в первых кадрах (дальше единичная), ForwardMatrix бывает заглушкой sRGB.
Проверено на DNG OnePlus 6 и Redmi Note 8T: ColorMatrix там тоже заглушка (= XYZ→sRGB, «сенсор как sRGB»),
цвета с ней блёклые. Порядок в «Авто»: настоящая ColorMatrix (D65 и A смешиваются по температуре сцены, как в DNG) → матрица ISP (запоминаем из первых кадров превью) →
типовая матрица смартфона (`ColorPipelineResolver.GENERIC_PHONE`). ForwardMatrix не используем.
Поток кадра: снимок → копия RAW (буфер камеры сразу свободен) → фоновая очередь: автоэкспозиция → миниатюра →
проявка (MHC, полное разрешение до 26 Мп) → JPEG в Pictures/Zerno → .zraw в files/shots для перепроявки.
Падения пишутся в files/last_crash.txt и предлагаются к отправке при следующем запуске. См. `docs/research/lampa.md`.

## Правила

- Ветка по умолчанию — `main`. CI собирает каждый пуш в `main` и каждый PR.
- Перед коммитом: `./gradlew assembleDebug testDebugUnitTest` должен проходить.
- Перед добавлением библиотеки проверяй её актуальную версию в репозитории (maven.google.com / Maven Central), а не по памяти.

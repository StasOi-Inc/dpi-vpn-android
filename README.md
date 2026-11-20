# dpi-vpn-android

Подборка свободных Android-приложений с обходом блокировок — открытая альтернатива закрытому приложению **NetFixMobile**. Два приложения собраны из исходников без изменений логики и автоматически собираются GitHub Actions.

## Состав

| Приложение | Что делает | Версия | Апстрим |
|---|---|---|---|
| **ByeDPIAndroid** | Локальный VPN на базе byedpi (обход DPI-блокировок: сплит запросов, фейковые пакеты и т.д.) | 1.2.0 | [dovecoteescapee/ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid) |
| **tg-ws-proxy-android** | Локальный SOCKS5/HTTP-прокси с фейковым TLS для Telegram (WebSocket-транспорт, из проекта tg-ws-proxy) | 1.2.3 | [amurcanov/tg-ws-proxy-android](https://github.com/amurcanov/tg-ws-proxy-android) |

Собранные бинарники нижележащих движков — [hufrea/byedpi](https://github.com/hufrea/byedpi), [heiher/hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) и Rust-библиотека `libtgwsproxy.so` (исходники внутри `tg-ws-proxy-android/src/`) — собираются из подключённых исходников, ничего не скачивается в готовом виде.

## APK

Сборка полностью автоматизирована: push в `main` запускает GitHub Actions, готовые APK выгружаются в артефакты workflow и дублируются в папку `apk/`.

- **ByeDPIAndroid**: `assembleRelease`, 4 ABI (armeabi-v7a, arm64-v8a, x86, x86_64).
- **tg-ws-proxy**: `assembleUniversalRelease` (arm64-v8a + armeabi-v7a; есть отдельные flavors `arm32`/`arm64`).

APK из CI **unsigned** — для установки подпишите локально:

```bash
keytool -genkeypair -v -keystore selfsign.keystore -alias selfsign -keyalg RSA -keysize 2048 -validity 10000
zipalign -f 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks selfsign.keystore --ks-key-alias selfsign --out signed.apk aligned.apk
apksigner verify --print-certs signed.apk
```

## Сборка вручную

Требования: JDK 17, Android SDK (platform 34/35, NDK 25.2.9519653, cmake 3.22.1), для tg-ws — Rust stable с таргетами `aarch64-linux-android`, `armv7-linux-androideabi`.

```bash
# ByeDPIAndroid (C libbyedpi + ndk-build hev-socks5-tunnel вшиты в preBuild)
cd ByeDPIAndroid && ./gradlew assembleRelease

# tg-ws-proxy (Rust -> .so -> gradle universal flavor)
cd tg-ws-proxy-android && ./build_so.sh && ./gradlew assembleUniversalRelease
```

`build_so.sh` — Linux/macOS-версия апстримного `build_so.bat`; на Windows используйте `build_so.bat`.

## Безопасность: отличие от NetFixMobile

NetFixMobile (закрытая сборка) вызывал вопросы: закрытая нативная библиотека `libtgwsproxy.so`, невоспроизводимая сборка и запрошенное разрешение `QUERY_ALL_PACKAGES` со сторонними автообновлениями. Здесь:

- **Все компоненты из открытых исходников**, включая `libtgwsproxy.so` (Rust, собирается этим же репозиторием).
- **Сборка воспроизводима** через GitHub Actions — можно пересобрать самому и сравнить.
- **Разрешения минимальны** (проверено по манифестам): оба приложения не содержат `QUERY_ALL_PACKAGES`; tg-ws использует scoped-блок `<queries>` для определения установленных приложений, ByeDPI запрашивает только `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*`. Нет `RECEIVE_BOOT_COMPLETED`, нет автообновлений из сторонних источников.

Приложения форкнуты без изменения кода, поэтому их безопасность определяется апстримами — при обнаружении проблем в оригиналах обновляйте исходники здесь.

## Лицензия

Оба проекта — GPL-3.0 (`LICENSE`). Соответствующие LICENSE сохранены в каждом подпроекте.

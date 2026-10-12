<div align="center">
  <img src="design/logo/icon.svg" width="112" alt="Логотип ReFlux">
  <h1>ReFlux для Android</h1>
  <p>Клиент серверов ReFlux: зашифрованный туннель через документы Mail.ru и Яндекса.</p>
  <p>
    <a href="https://github.com/BeleBob/ReFlux-Android/releases/latest"><img src="https://img.shields.io/github/v/release/BeleBob/ReFlux-Android?display_name=tag&amp;sort=semver&amp;style=flat-square&amp;color=EA1A1A" alt="Последний релиз"></a>
    <a href="https://github.com/BeleBob/ReFlux-Android/actions/workflows/ci.yml"><img src="https://github.com/BeleBob/ReFlux-Android/actions/workflows/ci.yml/badge.svg" alt="Статус CI"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue?style=flat-square" alt="Лицензия GPL-3.0 или новее"></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8 и новее">
  </p>
  <p>
    <a href="https://github.com/BeleBob/ReFlux"><img src="https://img.shields.io/badge/core-ReFlux-455a64?style=flat-square" alt="Ядро ReFlux"></a>
    <img src="https://img.shields.io/badge/ABI-ARM64%20%7C%20ARMv7%20%7C%20x86__64%20%7C%20x86-455a64?style=flat-square" alt="Поддерживаемые архитектуры Android">
    <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&amp;logo=openjdk&amp;logoColor=white" alt="Java 17">
    <img src="https://img.shields.io/badge/IPv4%20%2F%20TCP%20%2B%20UDP-supported-f59e0b?style=flat-square" alt="IPv4: TCP и UDP">
  </p>
  <p><strong>Русский</strong> · <a href="README.en.md">English</a></p>
</div>

ReFlux для Android подключает телефон к серверу [ReFlux](https://github.com/BeleBob/ReFlux).
Трафик идёт не обычным VPN-протоколом, а через сессию совместного
редактирования документа, зашифрованный AES-256-GCM. Канал выдаёт владелец
сервера: ссылкой `openflux://` или QR-кодом.

**[Скачать последний релиз](https://github.com/BeleBob/ReFlux-Android/releases/latest)**

```text
Приложение (VPN или SOCKS5) -> документ Mail.ru / Яндекса -> нода ReFlux -> туннели сервера -> Интернет
```

## Содержание
- [Возможности](#возможности)
- [Ограничения](#ограничения)
- [Установка](#установка)
- [Подключение](#подключение)
- [Сборка из исходников](#сборка-из-исходников)
- [Ядро и обновления](#ядро-и-обновления)
- [Структура](#структура)
- [Происхождение и лицензия](#происхождение-и-лицензия)

## Возможности
- **Подключение по ссылке или QR-коду.** Профиль создаётся из ссылки
  `openflux://` (нажатием или вставкой скопированной) или QR-кода (камерой
  или фото из галереи); вручную ничего вводить не нужно.
- **Несколько документов на канал.** В режиме Session приложение держит все
  документы канала сразу: трафик идёт через первый работающий, при его отказе —
  через следующий.
- **Связь восстанавливается сама.** При включении приложение повторяет
  попытки, пока не подключится; оборванное соединение с документом (смена
  Wi-Fi на мобильную сеть, обрыв NAT) заменяется новым примерно за минуту.
- **Kill Switch.** Пока канал восстанавливается, трафик не уходит мимо туннеля
  (включён по умолчанию).
- **Шифрование.** AES-256-GCM с ключом, выведенным через scrypt; ссылка на
  документ и ключ хранятся в Android Keystore.
- **Маршрутизация по приложениям**: белый или чёрный список.
- **Режим SOCKS5-прокси** — вместо системного VPN, по желанию доступный из
  локальной сети с авторизацией.
- **Уведомление** со скоростью загрузки и отдачи и кнопкой отключения.
- **Язык и тема.** Русский или английский (по умолчанию — как в телефоне);
  тема светлая, тёмная или как в телефоне, с чёрным фоном для AMOLED-экранов.
- **Журнал событий** с поиском, фильтром ошибок, копированием и отправкой
  файлом.
- **Транспорты**: Mail.ru Docs (основной для серверов ReFlux), Яндекс
  Документы и Volga, Cups.online.

Остальные возможности исходного приложения сохранены: установка ноды на VPS
из приложения, режим «Без сервера» с PHP-нодой, подписанные JS-транспорты.

## Ограничения
- Туннелируется IPv4 (TCP и UDP, включая DNS); IPv6 не туннелируется.
- Сервис документов видит метаданные: время соединений и объём трафика.
  Содержимое зашифровано.
- Это экспериментальное ПО, а не аудированная замена WireGuard. Используйте
  его только в сетях, где у вас есть на это право.

## Установка
1. Откройте [последний релиз](https://github.com/BeleBob/ReFlux-Android/releases/latest)
   и скачайте APK:
   - большинство телефонов (2018 год и новее) → `ReFlux-android-arm64-v8a-release.apk`;
   - старые 32-битные → `ReFlux-android-armeabi-v7a-release.apk`;
   - не уверены → `ReFlux-android-universal-release.apk`.
2. Установите, разрешив установку из этого источника.

Приложение ставится рядом с другими клиентами OpenFlux и не трогает их
настройки. Релизы подписаны постоянным сертификатом проекта, поэтому новые
версии ставятся поверх и сохраняют профили.

## Подключение
1. Получите у владельца сервера ссылку `openflux://` или QR-код. Если у
   сервера есть бот для клиентов, он присылает их сам после одобрения заявки.
2. В приложении откройте «Профили» и отсканируйте QR-код (кнопка QR) или
   вставьте ссылку.
3. Включите подключение и подтвердите системный запрос на VPN.

Если владелец сервера заменит документ канала, бот пришлёт новую ссылку:
импортируйте её так же.

## Сборка из исходников

### Требования
- Java 17 или 21 (JDK);
- Android SDK с платформой 35, Build Tools 35.0.0 и NDK 27.0.12077973;
- Gradle 8.14.3;
- Go (версия из `core/go.mod`) и `gomobile`.

### Сборка
```bash
git clone --recursive https://github.com/BeleBob/ReFlux-Android.git
cd ReFlux-Android
go install golang.org/x/mobile/cmd/gomobile@v0.0.0-20260908204917-8b95e45f8d3e
go install golang.org/x/mobile/cmd/gobind@v0.0.0-20260908204917-8b95e45f8d3e
gomobile init
export ANDROID_HOME=~/Android/Sdk          # путь к SDK
./build_android_app.sh                      # debug-сборка
```

APK появляются в `dist/`: по одному на архитектуру и универсальный.
Release-сборка подписывается ключом проекта:

```bash
BUILD_TYPE=release \
ANDROID_KEYSTORE_FILE=... ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... ANDROID_KEY_PASSWORD=... \
./build_android_app.sh
```

APK, подписанный другим сертификатом, не обновит установленное приложение:
сначала придётся удалить его вместе с настройками.

## Ядро и обновления
Ядро туннеля — репозиторий [ReFlux](https://github.com/BeleBob/ReFlux),
подключённый сабмодулем `core/`: OpenFlux с исправлениями ReFlux. Обновить
ядро:

```bash
git -C core fetch origin && git -C core checkout origin/main
./build_android_app.sh
git add core && git commit -m "core: update to ReFlux <commit>"
```

Релиз собирается тегом `vX.Y.Z`: GitHub Actions подписывает APK и публикует
их вместе с `SHA256SUMS`.

## Структура
```
ReFlux-Android/
  core/                 # ядро ReFlux (сабмодуль): транспорты, туннель, мост mobile/
  android/              # Android-приложение
  build_android_app.sh  # сборка APK поверх core/mobile
  design/               # логотип и иконки
  docs/                 # документация исходного приложения
```

## Происхождение и лицензия
ReFlux для Android — изменённая версия
[OpenFlux-Android](https://github.com/damnurmum/OpenFlux-Android) (damnurmum),
которое основано на [OpenFlux](https://github.com/p1neappleXpress/OpenFlux)
(p1neappleXpress). С октября 2026 года изменены название и идентификатор
приложения (`io.github.belebob.reflux`), ядро (ReFlux вместо OpenFlux) и
поведение при потере связи; перечень изменений — в истории коммитов.

Распространяется по лицензии GNU GPL v3.0 или более поздней версии, как и исходные проекты: см.
[LICENSE](LICENSE), [COPYRIGHT](COPYRIGHT) и [NOTICE](NOTICE). Проект не
связан с Яндексом и Mail.ru и не одобрен ими.

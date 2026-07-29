# Русский перевод Liminal Industries

Перевод квестов, реестров предметов и блоков, а также книг Patchouli для двух
вариантов модпака:

- Liminal Industries `1.19.3` (`1.19.3-original`);
- Liminal Industries - Rescripted `1.19.3 - 7 (AE2 fix)`
  (`1.19.3-7-ae2-fix`).

Обе версии работают на Minecraft `1.20.1` и Forge `47.4.13`. Для каждой
собирается отдельный клиентский Forge-мод с точным payload её книги квестов и
набор независимых ресурс-паков с названиями предметов для отдельных модов.

## Установка

1. Для полного перевода выберите JAR, соответствующий установленному модпаку,
   и поместите его в каталог `mods`.
2. Для перевода только отдельных модов вместо JAR поместите нужные ZIP в
   каталог `resourcepacks` и включите их в меню Minecraft.
3. Выберите русский язык в настройках Minecraft.

Артефакты:

```text
liminal-industries-russian-translation-1.19.3-original.jar
liminal-industries-russian-translation-1.19.3-7-ae2-fix.jar
```

На выделенный сервер JAR устанавливать не нужно. Названия предметов загружаются
как обычные ресурсы, а русский текст квестов подставляется только в памяти игры.
Файлы FTB Quests на диске не изменяются.

## Структура

Глобальный каталог переводов находится в `translation-catalog/`. Манифест,
настройки сборки и готовый payload каждой поддерживаемой версии находятся в:

```text
translation-versions/<version>/
├── build-config.json
├── manifest.json
├── resource-packs.json
├── sources.json
├── payload/
│   ├── quests/
│   └── resourcepack/
└── work/
```

Payload пересобирается из манифеста и каталога командой
`scripts/build_version_resources.py`. Gradle выбирает его через
`-PtranslationVersion=<version>`.

Полный процесс описан в
[`TRANSLATION_METHODOLOGY.md`](TRANSLATION_METHODOLOGY.md). Обновление версии
описано в [`docs/UPDATING.md`](docs/UPDATING.md), сборка JAR — в
[`docs/PACKAGING.md`](docs/PACKAGING.md).

Подключение новых поверхностей перевода описано в
[`docs/TRANSLATION_SOURCES.md`](docs/TRANSLATION_SOURCES.md).

Служебный runtime-аудит переводимых поверхностей находится отдельно в `audit-mod/`.
Он никогда не входит в публикуемый мод.

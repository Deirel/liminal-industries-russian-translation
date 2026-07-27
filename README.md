# Русский перевод Liminal Industries - Rescripted

Перевод квестов и названий предметов для модпака
[Liminal Industries - Rescripted](https://www.curseforge.com/minecraft/modpacks/liminal-industries-rescripted).

Последняя поддерживаемая версия:

- Liminal Industries - Rescripted `1.19.3 - 7 (AE2 fix)`
- Minecraft `1.20.1`
- Forge `47.4.13`
- FTB Quests `2001.4.22`

Скачать файлы можно на странице [последнего релиза](../../releases/latest).
Часть `1.19.3-7-ae2-fix` в имени файла — это версия модпака, а не Minecraft.

Перевод распространяется одним Forge-модом: JAR содержит переводы названий и
безопасно устанавливает переведённую книгу квестов.

## Установка

1. Скачайте
   `liminal-industries-russian-translation-1.19.3-7-ae2-fix.jar`.
2. Поместите JAR в каталог `mods` профиля.
3. Для перевода книги на выделенном сервере установите тот же JAR на сервер.
4. Выберите русский язык в настройках Minecraft.

Перед первой установкой переведённой книги мод проверяет полный набор исходных
квестов и создаёт backup в
`config/liminal_industries_ru/backups/<UTC_TIMESTAMP>/quests`. Неизвестную или
изменённую книгу мод не перезаписывает.

## Работа над переводом

Исходники лежат в `src/`. Как обновить перевод, читайте в
[`docs/UPDATING.md`](docs/UPDATING.md). Как собрать и выпустить мод — в
[`docs/PACKAGING.md`](docs/PACKAGING.md).

Исходный код Forge-мода находится в `mod/`. Его Gradle-сборка напрямую
подключает актуальные `src/resourcepack` и `src/quests`, поэтому отдельную копию
перевода внутри мода обновлять не нужно.

# Подготовка пакетов перевода

## Артефакты

В релиз входят:

- `liminal-industries-russian-translation-1.0.0.jar` — Forge-мод, содержащий
  оба вида перевода;
- `liminal-industries-russian-resource-pack-1.19.3-7-ae2-fix.zip` — названия
  предметов для ручной установки;
- `liminal-industries-russian-quest-configs-1.19.3-7-ae2-fix.zip` — книга
  квестов для ручной установки.

Для установки модом нужен только JAR. При ручной установке в одиночной игре
нужны оба ZIP-архива. На выделенном сервере конфиги квестов устанавливает
администратор, а ресурс-пак устанавливает каждый игрок.
`1.19.3-7 (AE2 fix)` — версия модпака. Он работает на Minecraft `1.20.1`.

## Структура исходников

```text
src/
├── quests/
│   ├── data.snbt
│   ├── chapter_groups.snbt
│   └── chapters/
│       └── *.snbt
└── resourcepack/
    ├── pack.mcmeta
    └── assets/
        └── MOD_ID/
            └── lang/
                └── ru_ru.json
```

В `src/quests` хранится полное содержимое `config/ftbquests/quests`. Не
добавляйте туда прогресс игроков, миры, журналы и настройки сервера.

`src/resourcepack` — обычный ресурс-пак Minecraft. `pack.mcmeta` и `assets`
лежат в его корне.

## Обновление

Читайте [`UPDATING.md`](UPDATING.md).

## Сборка архивов

Нужен Python 3. Запустите из корня репозитория:

```sh
python3 scripts/build_packages.py
```

По умолчанию скрипт собирает версию `1.19.3-7-ae2-fix`. Другую версию укажите
так:

```sh
python3 scripts/build_packages.py --version NEW-MODPACK-VERSION
```

Скрипт:

1. проверяет обязательные файлы квестов и `pack.mcmeta`;
2. проверяет JSON и повторяющиеся ключи;
3. собирает готовый ресурс-пак с `pack.mcmeta` в корне;
4. собирает архив конфигов с папкой `config` в корне;
5. записывает SHA-256 обоих файлов в `dist/SHA256SUMS`.

Одинаковые исходники и версия дают одинаковые архивы и контрольные суммы.

## Сборка мода

Нужен JDK 17. Из корня репозитория запустите:

```sh
cd mod
./gradlew clean test build
```

Gradle берёт языковые файлы из `src/resourcepack`, квесты из `src/quests` и
создаёт `translatedFiles` в manifest по их актуальным SHA-256. Готовый JAR
создаётся в `mod/build/libs`.

## Готовые архивы

Ресурс-пак:

```text
pack.mcmeta
assets/...
```

Архив конфигов квестов:

```text
config/ftbquests/quests/...
```

Посмотреть содержимое без распаковки:

```sh
unzip -l dist/liminal-industries-russian-resource-pack-1.19.3-7-ae2-fix.zip
unzip -l dist/liminal-industries-russian-quest-configs-1.19.3-7-ae2-fix.zip
```

Внутри ресурс-пака не должно быть папки `config`, другого ZIP-файла или лишней
папки над `pack.mcmeta`.

## Публикация

`dist/` не хранится в Git. Соберите его перед выпуском и приложите файлы к
GitHub Release.

Перед выпуском:

1. Сверьте версию в `README.md`, `DEFAULT_VERSION` и именах архивов.
2. Соберите архивы дважды. `dist/SHA256SUMS` не должен измениться.
3. Запустите `python3 -m py_compile scripts/build_packages.py`.
4. Выполните `cd mod && ./gradlew clean test build`.
5. Проверьте оба архива через `unzip -t`.
6. Проверьте JAR и ручные пакеты в чистом клиенте и на тестовом сервере.
7. Обновите копию старого мира и проверьте прогресс.
8. Проверьте, что в Git нет `dist/`, JAR-файлов, журналов, миров, настроек
   сервера и архивов модпака.
9. Получите разрешение на публикацию перевода квестов. Не распространяйте
   чужой текст под своей лицензией без согласия автора.

При публикации на GitHub:

1. Создайте тег, например `v1.19.3-7-ae2-fix.1`.
2. Возьмите описание из [`RELEASE_NOTES.md`](RELEASE_NOTES.md). Добавьте
   результаты проверки в игре.
3. Приложите JAR, ресурс-пак, архив конфигов и `SHA256SUMS`.
4. Напомните, куда устанавливается каждый файл.
5. Напомните о резервной копии.
6. Перечислите изменения и известные проблемы.

Тексты и имена файлов для CurseForge хранятся в
[`CURSEFORGE.md`](CURSEFORGE.md).

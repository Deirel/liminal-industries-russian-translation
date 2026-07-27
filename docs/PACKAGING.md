# Подготовка пакетов перевода

## Пакеты

В релиз входят два архива:

- `liminal-industries-russian-client-1.19.3-7-ae2-fix.zip` — квесты и названия
  предметов
- `liminal-industries-russian-server-1.19.3-7-ae2-fix.zip` — только квесты

Клиентский пакет нужен в одиночной игре и каждому игроку на выделенном сервере.
Серверный пакет ставится на сервер. `1.19.3-7 (AE2 fix)` — версия модпака. Он
работает на Minecraft `1.20.1`.

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

## Сборка

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
3. упаковывает ресурс-пак;
4. собирает клиентский и серверный архивы без лишней папки в корне;
5. записывает SHA-256 обоих файлов в `dist/SHA256SUMS`.

Одинаковые исходники и версия дают одинаковые архивы и контрольные суммы.

## Готовые архивы

Клиентский архив:

```text
config/ftbquests/quests/...
resourcepacks/liminal-industries-russian-resources.zip
```

Серверный архив:

```text
config/ftbquests/quests/...
```

Посмотреть содержимое без распаковки:

```sh
unzip -l dist/liminal-industries-russian-client-1.19.3-7-ae2-fix.zip
unzip -l dist/liminal-industries-russian-server-1.19.3-7-ae2-fix.zip
```

Проверить вложенный ресурс-пак:

```sh
rm -rf /tmp/liminal-ru-check
mkdir -p /tmp/liminal-ru-check
unzip -q dist/liminal-industries-russian-client-1.19.3-7-ae2-fix.zip \
  resourcepacks/liminal-industries-russian-resources.zip \
  -d /tmp/liminal-ru-check
unzip -l \
  /tmp/liminal-ru-check/resourcepacks/liminal-industries-russian-resources.zip
```

## Публикация

`dist/` не хранится в Git. Соберите его перед выпуском и приложите файлы к
GitHub Release.

Перед выпуском:

1. Сверьте версию в `README.md`, `DEFAULT_VERSION` и именах архивов.
2. Соберите архивы дважды. `dist/SHA256SUMS` не должен измениться.
3. Запустите `python3 -m py_compile scripts/build_packages.py`.
4. Проверьте внешние архивы и вложенный ресурс-пак через `unzip -t`.
5. Проверьте перевод в чистом клиенте и на тестовом сервере.
6. Обновите копию старого мира и проверьте прогресс.
7. Проверьте, что в Git нет `dist/`, JAR-файлов, журналов, миров, настроек
   сервера и архивов модпака.
8. Получите разрешение на публикацию перевода квестов. Не распространяйте
   чужой текст под своей лицензией без согласия автора.

При публикации на GitHub:

1. Создайте тег, например `v1.19.3-7-ae2-fix.1`.
2. Возьмите описание из [`RELEASE_NOTES.md`](RELEASE_NOTES.md). Добавьте
   результаты проверки в игре.
3. Приложите клиентский архив, серверный архив и `SHA256SUMS`.
4. Напомните, что для выделенного сервера нужны оба пакета.
5. Напомните о резервной копии.
6. Перечислите изменения и известные проблемы.

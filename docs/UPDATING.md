# Обновление перевода

Каждая версия модпака имеет собственные манифест, рабочую дельту, настройки
сборки и готовый payload в `translation-versions/<version>`. Глобальный каталог
`translation-catalog/catalog.json` общий для всех версий.

## 1. Построение манифеста и дельты

Для original:

```sh
python3 scripts/build_version_delta.py \
  --instance-root "$HOME/Library/Application Support/sklauncher/instances/liminal-industries" \
  --sklauncher-manifest "$HOME/Library/Application Support/sklauncher/manifests/liminal-industries.json" \
  --item-hints translation-versions/1.19.3-original/item-source-hints.tsv \
  --version-slug 1.19.3-original
```

Для Rescripted:

```sh
python3 scripts/build_version_delta.py \
  --instance-root "$HOME/Projects/minecraft-industrial-backrooms/data" \
  --sklauncher-manifest "$HOME/Library/Application Support/sklauncher/manifests/liminal-industries-rescripted.json" \
  --item-hints translation-versions/1.19.3-7-ae2-fix/item-source-hints.tsv \
  --version-slug 1.19.3-7-ae2-fix
```

Используйте чистый источник выбранной версии. Собственные translation/audit JAR
из `mods` извлекатель игнорирует. Пустые lang-файлы считаются пустыми; прочий
невалидный JSON останавливает построение.

Включённые источники задаются в
`translation-versions/<version>/sources.json`. Контракт адаптеров и порядок
добавления новых источников описаны в
[`TRANSLATION_SOURCES.md`](TRANSLATION_SOURCES.md).

Результат записывается в `manifest.json`, `migration-report.json` и
`work/pending.tsv`. Источники книг с `review_native: true` включают в дельту
штатные переводы со статусами `REVIEW_NATIVE`, `MISSING_NATIVE`,
`INVALID_NATIVE` и `STALE_NATIVE`. Точная утверждённая пара
`ID + source_hash` всегда получает `FINALIZED` и при повторном сканировании в
дельту не возвращается.

## 2. Перевод дельты

Заполняйте только колонку `translation` в `work/pending.tsv`.
`native_translation` служит неутверждённой подсказкой и может быть исправлена
или принята только после проверки по общей методологии. После независимого
ревью добавьте утверждённые записи в каталог:

```sh
python3 scripts/approve_version_translations.py --version <version>
```

Для миграции или ревью части большой дельты можно добавить `--allow-partial`.

Повторное построение манифеста должно дать нулевую дельту.

## 3. Построение payload

```sh
python3 scripts/build_version_resources.py \
  --version <version> \
  --instance-root "/path/to/clean/modpack"
```

Сборщик полностью заменяет `translation-versions/<version>/payload`, поэтому
в нём не остаются ресурсы другой версии. Затем проверьте результат:

```sh
python3 scripts/build_version_resources.py \
  --version <version> \
  --instance-root "/path/to/clean/modpack" \
  --check
```

## 4. Сборка и проверка

```sh
cd mod
./gradlew clean test build -PtranslationVersion=<version>
```

Проверки в игре и порядок публикации описаны в `docs/PACKAGING.md`.

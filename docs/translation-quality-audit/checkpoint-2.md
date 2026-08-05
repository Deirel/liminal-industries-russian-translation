# Чекпоинт 2: эффективные русские тексты

Дата проверки: 2026-08-05.

`scripts/export_effective_translations.py` создаёт отдельный TSV для каждой
версии из манифеста и уже собранного финального payload. Поэтому порядок слоёв
остаётся единственным порядком `build_version_resources.py`: источники модов,
KubeJS и resource pack учитываются при построении manifest, затем применяются
catalog, compatibility resources и версионные `translation-overrides`.

Каждая строка содержит `id`, `source_hash`, английский и эффективный русский
текст, JSON-контекст, версию и происхождение. Структурно удалённые полем
override исходные записи сохраняются с пустым текстом и явным состоянием
`suppressed_by_override`, а не выдаются за видимый перевод.

## Original

- 19 853 строки: 12 033 `native_ru`, 6 381 `catalog`,
  1 439 `translation-overrides`.
- 19 847 видимых записей и 6 исходных полей, подавленных структурным
  override; необъяснимых отсутствующих переводов нет.
- TSV: `translation-versions/1.19.3-original/work/effective-translations.tsv`,
  SHA-256 `8f696316058cb3f995657a9e14568723f101949605942437f25d156586bca57e`.

## Rescripted

- 22 659 строк: 13 156 `native_ru`, 7 991 `catalog`,
  1 512 `translation-overrides`.
- 22 656 видимых записей и 3 исходных поля, подавленных структурным
  override; необъяснимых отсутствующих переводов нет.
- TSV:
  `translation-versions/1.19.3-7-ae2-fix/work/effective-translations.tsv`,
  SHA-256 `60715872fd5bd0f6f21d3541b308f3957aa2192b50ed22494999119c2bc1cd74`.

## Runtime-сверка

Для обеих версий получены свежие отчёты `/liminal_ru_audit`:

- Original: `PASS`, 25 317 проверок, 0 ошибок,
  `2026-08-05T19:26:07.217840Z`, SHA-256
  `6b452a81f6008e695d54318776cbe06e45477c4f950bfe0d4fe3f998979d594d`.
- Rescripted: `PASS`, 28 003 проверки, 0 ошибок,
  `2026-08-05T19:25:06.694753Z`, SHA-256
  `81f0a39f3f321fa157792bf7590d712b482503d47bb40241e01514812486c29f`.

По десять первых сопоставимых item/lang-записей каждой версии автоматически
проверены по translation key. `display_name` запущенной игры точно совпал с
`effective_translation` во всех 20 случаях. Воспроизводимые выборки сохранены
в `work/runtime-sample.tsv`: Original SHA-256
`6168d5219927470661d7d4d3b25a8a5a2ef4f5a28a85c13809e97f999a86d7b1`,
Rescripted SHA-256
`98729ffb795481c40781d672791e9804ef191de8533d60adced42d8399bec192`.

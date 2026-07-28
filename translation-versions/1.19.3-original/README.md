# Liminal Industries 1.19.3

Эта версия относится к оригинальному модпаку **Liminal Industries**, а не к
**Liminal Industries - Rescripted**.

Источник:

- CurseForge project ID: `1276799`
- CurseForge file ID: `7397248`
- Minecraft: `1.20.1`
- Forge: `47.4.13`
- FTB Quests: `2001.4.13`

Манифест и дельта создаются командой:

```sh
python3 scripts/build_version_delta.py \
  --instance-root "$HOME/Library/Application Support/sklauncher/instances/liminal-industries" \
  --sklauncher-manifest "$HOME/Library/Application Support/sklauncher/manifests/liminal-industries.json" \
  --item-hints translation-versions/1.19.3-original/item-source-hints.tsv \
  --version-slug 1.19.3-original
```

В оригинальной сборке нет ProbeJS. Реестр восстанавливается по моделям,
имеющим фактический языковой ключ, предметным ссылкам из квестов и объявлениям
KubeJS из startup-скриптов. Так в реестр входят динамические предметы без
отдельной модели, но не попадают вспомогательные модели брони, кабелей и
состояний предметов. Динамические английские имена CoFH, которых нет в
статических lang-файлах, закреплены в `item-source-hints.tsv` после проверки
по редакторскому каталогу. Источник отмечен в `migration-report.json` как
`registry_source: language_models+quest_refs+kubejs_startup+reviewed_hints`.

Переводы новых квестовых строк и названий предметов находятся в
`work/quest-translations.tsv` и `work/item-translations.tsv`. Они проверены
против манифеста и добавлены в глобальный каталог только операцией
`scripts/approve_version_translations.py`. Готовые ресурсы собраны из
пересечения манифеста этой версии и каталога.

Дополнительные динамические ключи, обнаруженные игровым аудитом JEI и
runtime-реестра, хранятся в `runtime-audit-overrides.json`. Сборщик добавляет
их поверх статического манифеста, поскольку такие варианты зелий, жидкостей и
составных инструментов невозможно полностью восстановить по моделям и
языковым файлам без запуска игры.

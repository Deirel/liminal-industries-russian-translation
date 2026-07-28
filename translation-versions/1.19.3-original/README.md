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
  --version-slug 1.19.3-original
```

В оригинальной сборке нет ProbeJS, поэтому реестр предметов восстанавливается
по моделям предметов из Minecraft, модов и KubeJS. Это отмечено в
`migration-report.json` как `registry_source: item_models`.

Первичный перевод новых квестовых строк находится в
`work/quest-translations.tsv`. До добавления в глобальный каталог он должен
пройти независимое ревью. Новые названия предметов остаются в
`work/pending.tsv`.

# Сборка и публикация

Для каждой версии модпака собирается отдельный JAR и комплект независимых
ресурс-паков:

| Версия | FTB Quests | Артефакт |
| --- | --- | --- |
| `1.19.3-original` | `2001.4.13` | `liminal-industries-russian-translation-1.19.3-original.jar` |
| `1.19.3-7-ae2-fix` | `2001.4.22` | `liminal-industries-russian-translation-1.19.3-7-ae2-fix.jar` |

## Payload

Готовые ресурсы каждой версии находятся в
`translation-versions/<version>/payload`. Они строятся только из манифеста
выбранной версии и глобального каталога:

```sh
python3 scripts/build_version_resources.py \
  --version 1.19.3-original \
  --instance-root "$HOME/Library/Application Support/sklauncher/instances/liminal-industries"
```

Для проверки без изменения файлов добавьте `--check`.

Ручные русские редакторские правки находятся в `translation-overrides` и
попадают в payload. Каталог принимает только ресурсы `ru_ru`.

Технические исправления исходных книг находятся в
`compatibility-resourcepack`. Русские ресурсы при сборке вливаются в payload,
а остальные парные ресурсы Gradle добавляет в управляемый ресурсный слой JAR.
Тот же каталог собирается в отдельный compatibility ZIP для установки без JAR.

## Разбиение ресурс-паков

`translation-versions/<version>/resource-packs.json` явно связывает переводы с
публикуемыми паками и точными версиями целевых модов. Namespace сам по себе не
считается надёжным идентификатором владельца:

- `kubejs` и `minecraft` входят в `Liminal Industries Extras`;
- `cfm_circuit_breaker` в Rescripted также входит в Extras, поскольку
  соответствующего мода в этой версии сборки нет;
- namespace `thermal`, общий для модулей Thermal, публикуется одним паком
  `Thermal Series`;
- при необходимости поле `keys` позволяет разделить один namespace по точному
  списку ключей.

Скрипт `scripts/build_resource_packs.py` строит ZIP напрямую из канонического
`payload/resourcepack`. В корне каждого ZIP находятся `pack.mcmeta`, `pack.png`
и только назначенные этому паку файлы `assets/*/lang/ru_ru.json`. Порядок,
timestamp и сжатие воспроизводимы.

Ручная сборка и проверка:

```sh
python3 scripts/build_resource_packs.py --version 1.19.3-original
python3 scripts/build_resource_packs.py --version 1.19.3-original --check
```

По умолчанию ZIP записываются в
`mod/build/resourcepacks/<version>/`. Имя каждого файла содержит Minecraft,
версию целевого мода и версию перевода.

## Сборка мода и релиза

Требуется JDK 17. Из каталога `mod/` выполните одну из команд:

```sh
./gradlew clean test build -PtranslationVersion=1.19.3-original
./gradlew clean test build -PtranslationVersion=1.19.3-7-ae2-fix
```

Gradle читает `build-config.json` выбранной версии, подключает соответствующий
payload и подставляет правильные версию артефакта, описание и диапазон
FTB Quests в `META-INF/mods.toml`.

Во время `processResources` также строится `book-translations.json`. Он
содержит source fingerprints для Patchouli, IE Manual и Mantle/TConstruct и
позволяет JAR безопасно сохранить совпавшие переводы при разумном обновлении
целевого мода. Индекс строится из version-specific manifest и payload и не
объединяет Original и Rescripted.

Обычный `build` также запускает `buildResourcePacks` и
`verifyResourcePackPartition`, а также сборку и проверку compatibility ZIP.
Проверка доказывает, что каждый канонический ключ назначен ровно один раз,
неизвестных ключей нет, JAR содержит объединение паков и управляемые
compatibility-ресурсы, JSON строгий, а ZIP воспроизводимы.

Полный version-specific набор удобно собрать одной задачей:

```sh
./gradlew clean assembleTranslationRelease \
  -PtranslationVersion=1.19.3-original
```

JAR, его ресурс-паки и отдельный compatibility ZIP будут собраны в
`mod/build/releases/<version>/`. SNBT квестов остаётся только в JAR и никогда
не попадает в обычные ресурс-паки. Compatibility ZIP находится в подкаталоге
`compatibility` и нужен только для установки без JAR. Чтобы сохранить оба
набора в одном `build/releases`, используйте `clean` только перед первым:

```sh
./gradlew clean assembleTranslationRelease \
  -PtranslationVersion=1.19.3-original
./gradlew assembleTranslationRelease \
  -PtranslationVersion=1.19.3-7-ae2-fix
```

Каждая задача синхронизирует только каталог выбранной версии, поэтому второй
запуск не удаляет первый.

## Проверка релиза

1. Перестройте манифест и убедитесь, что `pending` равен нулю.
2. Пересоберите payload и повторите команду с `--check`.
3. Выполните `assembleTranslationRelease` для нужной версии.
4. Проверьте имя JAR, ZIP-паков, версию и диапазон FTB Quests.
5. Установите JAR или выбранные ZIP-паки в чистый профиль соответствующего
   модпака.
6. Отдельно включите compatibility ZIP, если нужны технические исправления книг.
7. Проверьте названия предметов; для JAR также проверьте все главы квестов.
8. Выполните runtime-аудит названий предметов.
9. Проверьте `book_translation_compatibility` в отчёте: штатная версия должна
   давать `EXACT`, а `SOURCE_CHANGED` содержит конкретные пропущенные поля.
10. Переключите язык и удалите JAR/ZIP: должен отображаться оригинальный текст, а
   файлы квестов не должны измениться.
11. Убедитесь, что в Git нет собранных JAR/ZIP, журналов, миров и архивов
   модпака.

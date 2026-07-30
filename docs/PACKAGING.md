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

Для публикуемого исправления передайте положительный счётчик
`-PreleaseIteration=<N>`. Он добавляется к версии JAR и внутренней версии мода;
для Original служебный суффикс `-original` при этом удаляется:

```sh
./gradlew clean build \
  -PtranslationVersion=1.19.3-original \
  -PreleaseIteration=2
```

Результат: `liminal-industries-russian-translation-1.19.3-2.jar`.

Обычный `build` также запускает `buildResourcePacks` и
`verifyResourcePackPartition`. Проверка доказывает, что каждый канонический
ключ назначен ровно один раз, неизвестных ключей нет, объединение паков равно
ресурсам в JAR, JSON строгий, а ZIP воспроизводимы.

Полный version-specific набор удобно собрать одной задачей:

```sh
./gradlew clean assembleTranslationRelease \
  -PtranslationVersion=1.19.3-original
```

JAR и его ресурс-паки будут собраны в
`mod/build/releases/<version>/`. SNBT квестов остаётся только в JAR и никогда
не попадает в обычные ресурс-паки. Чтобы сохранить оба набора в одном
`build/releases`, используйте `clean` только перед первым:

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
6. Проверьте названия предметов; для JAR также проверьте все главы квестов.
7. Выполните runtime-аудит названий предметов.
8. Переключите язык и удалите JAR/ZIP: должен отображаться оригинальный текст, а
   файлы квестов не должны измениться.
9. Убедитесь, что в Git нет собранных JAR/ZIP, журналов, миров и архивов
   модпака.

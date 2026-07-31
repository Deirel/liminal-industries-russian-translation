# Liminal Industries Russian Translation Mod

Клиентский Forge-мод с русским переводом Liminal Industries и
Liminal Industries - Rescripted. Для каждой версии модпака собирается отдельный
JAR с собственным payload квестов и языковых ресурсов.

## Сборка

Требуется JDK 17:

```sh
./gradlew clean test build -PtranslationVersion=1.19.3-original
./gradlew clean test build -PtranslationVersion=1.19.3-7-ae2-fix
```

Gradle читает `../translation-versions/<version>/build-config.json` и подключает
`../translation-versions/<version>/payload`. Готовый файл создаётся в
`build/libs`.

Переводы названий загружаются как обычные ресурсы Forge. Текст FTB Quests
подменяется на клиенте только во время отображения и никогда не записывается в
каталог конфигурации. Каждое поле применяется независимо при совпадении
`ID + поле + исходный текст`.

Встроенный ресурс-пак обязателен, но зарегистрирован внизу стека: пользовательские
ресурс-паки могут переопределять его тексты. Технические исправления книг не
входят в JAR и собираются отдельным необязательным compatibility ZIP.
Переводы Patchouli, IE Manual и Mantle сверяются с текущими исходными
ресурсами по полям: неизменившиеся строки остаются переведёнными, а новые,
изменённые и неоднозначные фрагменты не получают устаревший перевод.

Полное техническое описание находится в
[`TECHNICAL_SPEC.md`](TECHNICAL_SPEC.md).

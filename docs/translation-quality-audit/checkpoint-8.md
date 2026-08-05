# Чекпоинт 8: сборка и runtime-проверка

Дата проверки: 2026-08-06 (московское время).

## Статические проверки

- Повторное построение Original: 19 853 записи, 7 232 точных catalog hits,
  0 строк дельты.
- Повторное построение Rescripted: 22 659 записей, 9 618 точных catalog hits,
  0 строк дельты.
- Оба payload проходят `build_version_resources.py --check`, обе эффективные
  выгрузки проходят `export_effective_translations.py --check` с полным
  редакционным реестром.
- `PYTHONPATH=scripts python3 -m unittest discover -s scripts/tests`: 66
  тестов, `OK`.
- Состав установленных JAR совпадает с 57 resource packs Original и 63
  resource packs Rescripted.

Финальная сборка выявила отсутствовавшие описания нескольких component packs.
В `resource-packs.json` добавлены только фактически уже входившие в payload
моды и их namespaces. Для Original также добавлен английский compatibility-key
`item.botania.ender_air_bottle = Liminal Air Bottle`: именно такое имя задаёт
KubeJS, а без этого слоя runtime-аудит сравнивал перевод с базовым английским
именем Botania. После исправления источника повторный аудит прошёл без ошибок.

## Сборка и установка

Original и Rescripted собраны и установлены последовательно, поскольку их
Gradle-проекты используют общие каталоги `build`. В каждый экземпляр
установлены соответствующие translation- и audit-JAR.

| Версия | Файл | Время установки, UTC | SHA-256 |
| --- | --- | --- | --- |
| Original | `liminal-industries-russian-translation-1.19.3-original.jar` | `2026-08-05T21:08:19.327484Z` | `84ac1a12503104e97a73b28dafe6bbdcbf9bfcfee467220f45380b2320837cc4` |
| Original | `liminal-industries-russian-translation-audit-1.19.3-original.jar` | `2026-08-05T21:08:39.934880Z` | `fa9771272215932570d849d3c0e960e74198ee1cc93324cad7e152ce41217fbc` |
| Rescripted | `liminal-industries-russian-translation-1.19.3-7-ae2-fix.jar` | `2026-08-05T21:05:15.953916Z` | `ec3b4a08a22d1386974ce1c945f37607f0a3dc8efaf005f4b11cc915ecead593` |
| Rescripted | `liminal-industries-russian-translation-audit-1.19.3-7-ae2-fix.jar` | `2026-08-05T21:05:36.919070Z` | `5cc425495c55d7ba8ada67d07420c34840449baf74ff2a6d6bb675f6fe28be39` |

## Runtime-отчёты

Все четыре отчёта созданы после установки соответствующих JAR.

| Версия | Аудит | `generated_at`, UTC | Проверено | Ошибки | SHA-256 |
| --- | --- | --- | ---: | ---: | --- |
| Original | тексты | `2026-08-05T21:09:36.999729Z` | 25 317 | 0 | `16a8fab20b023fca8547d6ac4c3a62015dd79cc5c6e138f743b847a586ceb617` |
| Rescripted | тексты | `2026-08-05T21:10:50.251854Z` | 28 003 | 0 | `36148829dc535ade0f7b23abe97585802549420400c3097a05e5a501b730f785` |
| Original | книги | `2026-08-05T21:15:16.235612Z` | 3 490 экранов | 0 блокирующих | `8eacb546c74886151e3565151f380856bc72ec113ce23ff95f23f6dac0643fb0` |
| Rescripted | книги | `2026-08-05T21:22:31.176917Z` | 3 846 экранов | 0 блокирующих | `e12cdff9f8a1880173b5b841181cf9e63e937ea5385bfb57d5301948f53e5f5c` |

Оба текстовых отчёта имеют `result=PASS`, `MISSING_RU=0`, ноль required- и
extended-ошибок. В Original непосредственно подтверждены, среди прочего,
`Пузырёк лиминального воздуха`, `Пружинный пускатель`, `Порошок маны` и
`Продвинутое улучшение «Помпа»`.

Оба книжных отчёта имеют `result=PASS`, ноль translation-, missing-content-,
missing-language-page- и unpaired-language-ошибок. Автоаудит открыл страницы с
исправленными текстами Immersive Engineering, Thermal, Botania и Tinkers'
Construct. Страницы `brewer`, `catalysts` и `conveyors` прошли без замечаний к
русской компоновке. Скриншот Original со слизневыми ботинками дополнительно
проверен визуально: исправленный абзац помещается на странице. Зафиксированные
на этой странице и в остальных книгах 100 Original и 89 Rescripted замечаний
относятся только к `UPSTREAM_LAYOUT`; переводческих классификаций среди них нет,
поэтому они не являются блокирующими.

## Итоговый список коррекций

Полный список из 475 принятых версионных решений сохранён в четырёх TSV без
агрегации контекста:

| Версия и основание | Строк | SHA-256 |
| --- | ---: | --- |
| Rescripted `approved-changes.tsv` | 195 | `73ec93f3126079a0dcb41fd9abc37ec7c0edec5a0e2ec276039ca0d24e280546` |
| Original `reused-changes.tsv` | 170 | `30fca09f372f34878949ac16cfc5d261453cec9a0b95b26d753215be97907c3b` |
| Original `approved-new-changes.tsv` | 109 | `ba5b0e83016d9bc187a0ebec53f9b9f1abdac61c62297868d8b20a499be925f6` |
| Original `application/derived-changes.tsv` | 1 | `a5e369cfb51b5ae8d497596943bfb47c2c7dd9b77ecdd44244aa6de66081c6d6` |

Каждая строка содержит старый и новый текст, причину, решение независимого
ревьюера и замечания ревью. С учётом одного общего физического изменения
translation key эти 475 решений соответствуют 474 применённым правкам, как
зафиксировано в чекпоинте 6.

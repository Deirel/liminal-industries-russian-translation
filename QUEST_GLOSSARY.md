# Глоссарий перевода квестов Liminal Industries

Этот документ задаёт терминологию для русского перевода квестов. Он рассчитан
на установленную сборку Liminal Industries - Rescripted для Minecraft 1.20.1
и на язык клиента `ru_ru`, который сейчас выбран в SKLauncher.

Короткая версия для передачи переводящим агентам находится в
`QUEST_GLOSSARY.tsv`.

## Стратегия

### Источники в порядке приоритета

1. Точный item/block ID в исходном SNBT квеста.
2. Итоговое имя объекта при `ru_ru`: ресурс-пак
   `liminal-industries-russian`, затем ресурсные переопределения сборки и
   `ru_ru.json` установленного мода.
3. Официальный `ru_ru` Minecraft 1.20.1 для ванильных объектов.
4. Каталоги в `item-translation-work/`, если исходный мод не содержит русской
   строки.
5. Редакторское решение только для лора и обычной прозы.

Нельзя выбирать перевод только по английской фразе. Сначала нужно установить,
какой объект имеет в виду квест. В сборке встречаются одинаково названные
объекты из разных модов с разными русскими именами.

### Три класса терминов

- **UI_RU** — у конкретного объекта есть работающая русская строка. В квесте
  используется она, включая необычный перевод, регистр и дефис.
- **KEEP_EN** — собственные имена, бренды и технические обозначения, которые
  намеренно остаются английскими.
- **LORE** — это не имя объекта UI. Термин переводится естественно, но всегда
  одинаково во всех главах.

### Что всегда оставлять неизменным

1. Имена модов и брендов: `Liminal Industries`, `Create`,
   `Immersive Engineering`, `Thermal Expansion`, `Applied Energistics 2`,
   `AE2`, `Botania`, `Mekanism`, `Ender IO`, `Eidolon`, `JEI`, `KubeJS`,
   `FTB Quests`.
2. Собственные названия измерений `Backrooms` и `Poolrooms`.
3. Технические идентификаторы: `namespace:id`, команды, NBT, имена файлов,
   координаты и адреса.
4. Коды и разметку: `&o`, `&l`, `&6`, `{image:...}`, `%s`, `%d`.
5. Единицы и сокращения: `FE`, `RF`, `AE`, `FE/t`, `RF/t`, `NBT`, `P2P`.
6. Названия клавиш: `Shift`, `Ctrl`, `Alt`. Сочетания действий можно писать
   как `Shift + ПКМ` и `Shift + ЛКМ`.
7. Названия музыкальных композиций и прочие явно обозначенные собственные
   имена.

Прежнее правило KEEP_EN для KubeJS-предметов отменено: их интерфейсные названия
теперь локализованы отдельным ресурс-паком. Например, `Unstable Compound`
называется «Нестабильное соединение» и так же должно называться в квестах.

### Как добавлять новые термины

1. Найти строку квеста и соответствующий quest/task в SNBT.
2. Выписать точный item/block ID.
3. Проверить каталог `item-translation-work/` и ресурс-пак локализации.
4. Проверить пару `en_us`/`ru_ru` в JAR конкретного мода.
5. Если русской строки нет, добавить перевод в соответствующий TSV-каталог и
   пересобрать ресурс-пак.
6. Если это не объект UI, добавить одно редакторское решение в LORE.
7. После изменения глоссария обновить и `QUEST_GLOSSARY.tsv`.

## KEEP_EN

Здесь остаются только собственные имена и бренды:

| Оставлять точно | Причина |
| --- | --- |
| Liminal Industries | бренд модпака |
| Backrooms | собственное имя измерения |
| Poolrooms | собственное имя измерения |
| Create, AE2, Botania, Mekanism и названия других модов | бренды |
| Snad, Suol Snad | намеренные названия-перевёртыши и имя мода |
| Ars Ecclesia | собственное название книги |
| Summoning 101 | собственное название книги Patchouli |

Все прежние английские названия KubeJS, Thermal и других предметов перенесены
в UI_RU. Полный машинно-проверяемый список находится в
`item-translation-work/*.tsv`.

## UI_RU

### Локализация сборки

Полный перечень содержит 514 ключей и хранится в
`item-translation-work/*.tsv`. Основные квестовые термины:

| English | Русское UI-имя |
| --- | --- |
| Carpet Dust | Ковровая пыль |
| Soggy Carpet | Мокрый ковёр |
| Carpet Fluid | Ковровая жидкость |
| Almond Water | Миндальная вода |
| Unstable Compound | Нестабильное соединение |
| Compound Charge | Заряд нестабильного соединения |
| Copycat Alloy | Копирующий сплав |
| Hot Steel | Раскалённая сталь |
| Wallpaper | Обои |
| Sculk Scrubber | Скалковый очиститель |
| Porous Stone | Пористый камень |
| Putty Knife | Шпатель |
| Soulcoal | Уголь душ |
| Reality Frame | Каркас реальности |
| Reality Controller | Контроллер реальности |
| Reality Spawner | Призыватель реальности |
| Reality Charge | Заряд реальности |
| Reality Alloy | Сплав реальности |
| Reality Storage Cell | Ячейка хранения реальности |
| Data Chip | Чип данных |
| Modified Data Chip | Модифицированный чип данных |
| Broken Data Chip | Сломанный чип данных |
| Wall Piercer | Пробойник стен |
| Pool Tile / Pool Tiles | Плитка бассейна |
| Pool Locker | Шкафчик у бассейна |
| Location Terminal | Терминал местоположений |
| Location Card | Карта местоположения |
| Artificial Intelligence | Искусственный интеллект |
| Electronic Component | Электронный компонент |
| Advanced Electronic Component | Продвинутый электронный компонент |
| Duroplast Sheet | Лист дюропласта |
| Phenolic Resin | Фенольная смола |
| Fiberboard | Древесноволокнистая плита |
| Redstone Acid | Редстоуновая кислота |
| Machine Frame | Каркас механизма |
| Igneous Extruder | Магматический экструдер |
| Blast Chiller | Шоковый охладитель |
| Induction Smelter | Индукционная плавильня |
| Phytogenic Insolator | Фитогенный облучатель |
| Centrifugal Separator | Центробежный сепаратор |
| Multiservo Press | Мультисерво-пресс |
| Pulverizer | Измельчитель |
| Fluid Encapsulator | Жидкостный наполнитель |
| Fractionating Still | Фракционный дистиллятор |
| Pyrolyzer | Пиролизёр |
| Fluxduct | Энергетическая труба |
| Crude Oil | Сырая нефть |
| Bitumen | Битум |
| Tar | Гудрон |
| Light Oil | Лёгкая нефть |
| Refined Fuel | Очищенное топливо |
| Fluxtooth Spores | Споры флюсозуба |
| Block of Charcoal | Блок древесного угля |
| Cured Glue | Затвердевший клей |
| Diving Fabric | Ткань для гидрокостюма |
| Niter Dust | Селитровая пыль |
| Time in a Bottle | Время в бутылке |
| Floaty Boat | Надувная лодочка |
| Coconut | Кокос |
| Palm Sprout | Росток пальмы |
| Concrete Tile | Бетонная плитка |
| Fluorescent Tube | Люминесцентная лампа |
| Exotic Eye (KubeJS) | Экзотическое Око |

### Minecraft

| English | Русское UI-имя |
| --- | --- |
| Crafting Table | Верстак |
| Furnace | Печь |
| Blast Furnace (vanilla) | Плавильная печь |
| Cobblestone | Булыжник |
| Dirt | Земля |
| Mud | Грязь |
| Sculk | Скалк |
| Silverfish | Чешуйница |
| Husk | Кадавр |
| Allay | Тихоня |
| Wither Skeleton | Визер-скелет |
| Stray | Зимогор |
| Drowned | Утопленник |
| Soul Fire | Огонь душ |
| Wet Sponge | Мокрая губка |
| Prismarine Shard | Осколок призмарина |
| Lapis Lazuli | Лазурит |
| Dragon's Breath | Дыхание дракона |
| End Stone | Эндерняк |
| Shulker Shell | Панцирь шалкера |
| Chorus Fruit | Плод хоруса |
| Sniffer Egg | Яйцо нюхача |
| Overworld | Обычный мир |

### Immersive Engineering

| English | Русское UI-имя |
| --- | --- |
| Engineer's Manual | Руководство инженера |
| Engineer's Hammer | Молот инженера |
| Alloy Kiln | Плавильная печь |
| Blast Furnace (IE) | Доменная печь |
| Improved Blast Furnace | Продвинутая доменная печь |
| Blast Furnace Preheater | Воздухонагреватель доменной печи |
| Heavy Engineering Block | Тяжёлый инженерный блок |
| Light Engineering Block | Лёгкий инженерный блок |
| Redstone Engineering Block | Редстоуновый инженерный блок |
| Mixer | Смеситель |
| Industrial Fermenter | Бродильный аппарат |
| Refinery (IE) | Очиститель |
| Arc Furnace | Дуговая печь |
| Thermoelectric Generator | Термоэлектрический генератор |
| Vacuum Tube | Электронная лампа |
| Fluorescent Tube (IE) | Люминесцентная лампа |
| Coke Oven | Коксовая печь |
| Coal Coke | Коксовый уголь |
| Treated Wood Planks | Доски из обработанной древесины |

### Create и дополнения

| English | Русское UI-имя |
| --- | --- |
| Encased Fan | Вентилятор в корпусе |
| Mechanical Press | Механический пресс |
| Propeller | Пропеллер |
| Electron Tube | Электронная лампа |
| Rose Quartz | Розовый кварц |
| Brass Ingot | Латунный слиток |
| Brass Casing | Латунный корпус |
| Blaze Burner | Горелка всполоха |
| Empty Blaze Burner | Пустая горелка всполоха |
| Blaze Cake | Всполоховый торт |
| Hand Crank | Рукоятка |
| Steam Engine | Паровой двигатель |
| Depot | Депо |
| Package | Коробка |
| Deployer | Автономный активатор |
| Andesite Alloy | Андезитовый сплав |
| Sturdy Sheet | Прочный лист |
| Train Station | Железнодорожная станция |
| Train Casing | Железнодорожный корпус |
| Train Track | Железнодорожный путь |

`Ash Alloy` — английское переименование сборки для Andesite Alloy, заданное
только в `en_us`. При текущем `ru_ru` игрок видит «Андезитовый сплав».

### Applied Energistics 2

| English | Русское UI-имя |
| --- | --- |
| ME Controller | МЭ-регулятор |
| ME Chest | МЭ-сундук |
| ME Drive | МЭ-дисковод |
| ME Crafting Terminal | МЭ терминал изготовления |
| Inscriber | Вырезатель |
| Certus Quartz | Истинный кварц |
| Certus Quartz Crystal | Кристалл истинного кварца |
| Fluix Crystal | Флюисовый кристалл |
| Sky Stone | Небесный камень |
| Mysterious Cube | Таинственный куб |
| Calculation Processor | Вычислительный процессор |
| Engineering Processor | Инженерный процессор |
| Logic Processor | Логический процессор |
| Inscriber Calculation Press | Вычислительная печать для вырезателя |
| Inscriber Engineering Press | Инженерная печать для вырезателя |
| Inscriber Logic Press | Логическая печать для вырезателя |
| Inscriber Silicon Press | Кремниевая печать для вырезателя |
| Storage Cell | Ячейка хранения |
| Matter Condenser | Конденсатор материи |
| Singularity | Сингулярность |

### Botania

| English | Русское UI-имя |
| --- | --- |
| Lexica Botania | Лексикон Ботании |
| Petal Apothecary | Лепестковая чаша |
| Pure Daisy | Чистая маргаритка |
| Livingwood | Жизнедерево |
| Floral Fertilizer | Цветочное удобрение |
| Runic Altar | Рунический алтарь |
| Mana Spreader | Распространитель маны |
| Mana Pool | Бассейн маны |
| Endoflame | Эндопламя |
| Kekimurus | Кекимурус |
| Alchemy Catalyst | Алхимический катализатор |
| Terrestrial Agglomeration Plate | Теллурическая агломерационная пластина |

### Actually Additions, Eidolon и Ender IO

| English | Русское UI-имя |
| --- | --- |
| Atomic Reconstructor | Атомный реконстрактор |
| Black Quartz | Чёрный кварц |
| Void Crystal | Кристалл Пустоты |
| Empowerer | Зарядник |
| Display Stand | Выставочный стенд |
| Soul Shard | Осколок души |
| Lesser Soul Gem | Малый самоцвет души |
| Soulfire Wand | Жезл пламени душ |
| Apothecary Stand | Аптекарская стойка |
| Brazier (Eidolon) | Жаровня |
| Crucible (Eidolon) | Тигель |
| Wraith | Призрак |
| Confusing Powder | Запутывающий порошок |
| Ensouled Chassis | Корпус механизма душ |
| Void Chassis | Корпус механизма |
| Alloy Smelter (Ender IO) | Завод сплавов |
| SAG Mill | Дробитель |
| Soul Binder | Связыватель душ |
| Slice'N'Splice | Отрубатель и сращиватель |
| Item Conduit | Предметная труба |

### Прочие установленные моды

| English | Русское UI-имя |
| --- | --- |
| Cutting Board / Chopping Board | Разделочная доска |
| Basket | Корзина |
| Keg | Бочонок |
| Camera (Exposure) | Фотоаппарат |
| Lightroom | Фотолаборатория |
| Eccentric Tome | Чудаковатый Фолиант |
| Backpack | Рюкзак |
| Chalk | мелок |
| Smeltery | Плавильня |
| Smeltery Controller | Контроллер плавильни |
| Warp Stone | Камень перемещения |
| Warp Dust | Пыль перемещения |
| Portstone | Портовый обелиск |
| waystone | путеводный обелиск |
| Ender Chest | Эндер-сундук |
| Liminal Jam | Сладкое ягодное варенье |
| Vanilla Pods | Стручки ванили |
| Timber Frame | Деревянный каркас |
| Beach Hat | Пляжная шляпа |
| Bikini | Бикини |
| Swimming Trunks | Плавки |
| Crocs | Кроксы |
| Swim Wings | Нарукавники для плавания |
| Rubber Ring | Надувной круг |
| Open Coconut | Вскрытый кокос |
| Palm Bar | Тики бар |
| Raw Mussel Meat | Сырое мясо мидии |
| Beach Towel | Пляжное полотенце |
| Seashell | Ракушка |
| Message in a Bottle | Послание в бутылке |
| Mini Fridge | Мини-холодильник |

## Mekanism

| English | Русское UI-имя |
| --- | --- |
| Osmium Ingot | Осмиевый слиток |
| Metallurgic Infuser | Металлургический наполнитель |
| Basic Control Circuit | Базовая схема управления |
| Advanced Control Circuit | Продвинутая схема управления |
| Elite Control Circuit | Элитная схема управления |
| Ultimate Control Circuit | Совершенная схема управления |
| Steel Casing | Стальной корпус |
| Electrolytic Separator | Электролитический сепаратор |
| Purification Chamber | Камера очистки |
| Osmium Compressor | Осмиевый компрессор |
| Combiner | Объединитель |
| Chemical Injection Chamber | Химическая инъекционная камера |
| Enrichment Chamber | Камера обогащения |
| Pressurized Reaction Chamber | Герметичная реакционная камера |
| Dimensional Stabilizer | Пространственный стабилизатор |
| Antimatter Pellet | Гранула антиматерии |

## End Remastered

Для предметов End Remastered слово `Eye` всегда переводится как «Око».

| English | Русское UI-имя |
| --- | --- |
| Magical Eye | Магическое Око |
| Evil Eye | Зловещее Око |
| Wither Eye | Око Иссушителя |
| Lost Eye | Потерянное Око |
| Undead Eye | Око Нежити |
| Cold Eye | Ледяное Око |
| Cryptic Eye | Загадочное Око |
| Black Eye | Чёрное Око |
| Nether Eye / Forged Eye | Око Ада |
| Cursed Eye | Проклятое Око |
| Old Eye | Старинное Око |
| Rogue Eye | Око Изгоя |
| Exotic Eye (`endrem:exotic_eye`) | Экзотическое Око |
| Ancient Portal Frame | Рамка древнего портала |

Английский ресурс сборки переименовывает `Nether Eye` в `Forged Eye`, но только
в `en_us`. При `ru_ru` действующее имя — «Око Ада».

## Коллизии

| Фраза | Как различать |
| --- | --- |
| Blast Furnace | vanilla — «Плавильная печь»; IE — «Доменная печь» |
| Refinery | IE — «Очиститель»; Thermal — `Fractionating Still` KEEP_EN |
| Concrete Tile | `immersiveengineering:concrete_tile` — «Кафельный бетон»; `kubejs:concrete_tile` — `Concrete Tile` KEEP_EN |
| Fluorescent Tube | IE — «Люминесцентная лампа»; KubeJS custom — `Fluorescent Tube` KEEP_EN |
| Exotic Eye | `endrem:exotic_eye` — «Экзотическое Око»; `kubejs:exotic_eye` — `Exotic Eye` KEEP_EN |
| Brazier | Eidolon — «Жаровня»; Decorative Blocks — «Очаг» |
| Camera | Exposure — «Фотоаппарат»; Camera Mod — «Камера» |
| Straw | Farmer's Delight — «Солома»; Create Crafts & Additions — «Соломинка» |
| Barrel / Keg | Barrel — «Бочка»; Keg — «Бочонок» |
| Cauldron / Crucible | Cauldron — «Котёл»; Eidolon Crucible — «Тигель» |
| Carpet | обычный ковёр переводится; `Soggy Carpet` и `Carpet Dust` — KEEP_EN |

## LORE

| English | Единый русский вариант |
| --- | --- |
| The Infinite Library | Бесконечная библиотека |
| limbo | лимб |
| liminal / liminality | лиминальный / лиминальность |
| noclip / noclipping | ноклип / провалиться ноклипом |
| sector | сектор |
| chapter | глава |
| multiblock | мультиблок |
| chunk loading | загрузка чанков |
| seed | сид |
| Equivalent Exchange | равноценный обмен |
| carpet inhabitants | обитатели ковра |
| sculk infection | скалковое заражение |
| realm beyond time | мир вне времени |

## Исправления исходного текста

Перевод не должен сохранять ошибки, из-за которых игрок ищет несуществующее имя.

| Ошибка в квесте | Использовать |
| --- | --- |
| Mechanical Fan | Вентилятор в корпусе (`Encased Fan`) |
| Advanced Blast Furnace | Продвинутая доменная печь (`Improved Blast Furnace`) |
| Industrial Mixer | Смеситель (`Mixer`) |
| Machine Chiller | Blast Chiller |
| Machine Smelter | Induction Smelter |
| Machine Insolator | Phytogenic Insolator |
| Machine Press | Multiservo Press |
| Machine Bottler | Fluid Encapsulator |
| Machine Refinery | Fractionating Still |
| Machine Pyrolyzer | Pyrolyzer |
| Reality Chips | Data Chip |
| Duroplast Plate | Duroplast Sheet |
| Terra Plate | Теллурическая агломерационная пластина |
| Rubber tubes | Надувные круги (`Rubber Ring`) |
| marine fabric | Diving Fabric |
| Walkie-Talkie task | Рация (`Transceiver`) |
| Alloy Klin | Alloy Kiln |
| Ars Eccelecia | Ars Ecclesia |
| Lexa Botania | Лексикон Ботании |
| Soulshards | Осколки души |
| Lesser Soulgems | Малые самоцветы души |
| manapool | Бассейн маны |
| Endstone | Эндерняк |

## Контроль качества

1. Искать термин по всем файлам, а не проверять главы по отдельности.
2. Для каждого нового имени предмета проверять task item ID в SNBT.
3. Проверить, что слова из KEEP_EN не переведены и не склоняются.
4. Проверить отсутствие ошибочных форм: `скулк`, `цертус`, `флуикс`,
   `Mechanical Fan`, `Reality Chips`, `Ars Eccelecia`, `Lexa Botania`.
5. Проверить коллизии `Blast Furnace`, `Refinery`, `Concrete Tile`,
   `Fluorescent Tube`, `Exotic Eye`, `Camera`, `Brazier`.
6. После литературной правки снова выполнить
   `python3 scripts/pack_quest_texts.py --check`.
7. После добавления будущего русского ресурспака сначала обновить UI-имена,
   затем синхронно перевести соответствующие строки KEEP_EN в квестах.

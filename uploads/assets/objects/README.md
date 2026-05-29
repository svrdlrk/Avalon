# uploads/assets/objects

Папка с объектами карты: мебель, укрытия, декор, двери, бочки, ящики, элементы окружения.

Канонический root для этого каталога:
`uploads/assets/objects`

## Структура каталога

В папке лежат:
- изображения объектов;
- `catalog.json` — описание подпапок и их параметров;
- `names.ru.json` — русские названия файлов/ассетов;
- `example.catalog.json` — пример структуры каталога;
- `example.names.ru.json` — пример файла русских названий.

## Как устроен `catalog.json`

Файл задаёт:
- `root` — корень каталога;
- `defaults` — значения по умолчанию;
- `folders` — список подпапок.

## `catalog.json`

Это основной файл описания каталога.

Пример структуры:

```json
{
    "root": "uploads/assets/objects",
    "defaults": {
        "kind": "object",
        "category": "objects",
        "defaultWidth": 1,
        "defaultHeight": 1,
        "blocksMovement": true,
        "blocksSight": true,
        "isDecoration": true,
        "displayNameFrom": "filename",
        "nameTransform": "title_case"
    },
    "folders": [
      {
        "path": "Shelters/Tents",
        "category": "Shelters/Tents",
        "defaultWidth": 6,
        "defaultHeight": 5,
        "blocksMovement": true,
        "blocksSight": true
      }
    ]
}
```

### `root`

Это корень для всех путей внутри каталога объектов.

### Поля `defaults`

Значения по умолчанию для всех папок:

- `kind` — тип ассета. Здесь: `object`.
- `category` — категория по умолчанию.
- `defaultWidth` — ширина по умолчанию в клетках.
- `defaultHeight` — высота по умолчанию в клетках.
- `blocksMovement` — блокирует ли объект перемещение.
- `blocksSight` — блокирует ли объект обзор.
- `isDecoration` — признак декоративного объекта.
- `displayNameFrom` — источник названия. Здесь: имя файла.
- `nameTransform` — преобразование имени файла в отображаемое название. Здесь: `title_case`.

### Поля внутри `folders`

Каждый элемент массива описывает отдельную папку с объектами.

- `path` — подпапка относительно `uploads/assets/objects`.
- `category` — категория в UI.
- `defaultWidth` — ширина по умолчанию для объектов из этой папки.
- `defaultHeight` — высота по умолчанию для объектов из этой папки.
- `blocksMovement` — блокирует ли объект перемещение.
- `blocksSight` — блокирует ли объект обзор.

## `names.ru.json`

Файл задаёт русские названия для изображений объектов.

Пример:
```json
{
  "barrel.png": "Бочка",
  "chest.png": "Сундук",
  "table.png": "Стол",
  "Shelters/Tents/red_tent_6x5.png": "Красная палатка"
}
```

### Что писать в ключе
- имя файла;
- относительный путь от `uploads/assets/objects`;
- полный путь от корня каталога.
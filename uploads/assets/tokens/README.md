# uploads/assets/tokens

## Папка с токенами: персонажи, NPC, монстры, игроки

Канонический root для этого каталога:
`uploads/assets/tokens`

## Структура каталога

В папке лежат:
- изображения токенов;
- `catalog.json` — описание подпапок и их параметров;
- `names.ru.json` — русские названия файлов/ассетов;
- `example.catalog.json` — пример структуры каталога;
- `example.names.ru.json` — пример файла русских названий.

## Как устроен `catalog.json`

Файл задаёт:
- `root` — корень каталога;
- `defaults` — значения по умолчанию;
- `folders` — список подпапок, которые сканируются как отдельные группы ассетов.


## `catalog.json`

Это основной файл описания каталога.

Обычно используется структура такого вида:

```json
{
    "root": "uploads/assets/tokens",
    "defaults": {
        "kind": "token", 
        "category": "neutral",
        "gridSize": 1,
        "defaultWidth": 1,
        "defaultHeight": 1,
        "blocksMovement": true,
        "blocksSight": false,
        "isToken": true,
        "isDecoration": false,
        "displayNameFrom": "filename",
        "nameTransform": "title_case"
    },
    "folders": [
    {
        "path": "medium/creatures",
        "category": "tokens/medium/creatures",
        "kind": "TOKEN",
        "width": 6,
        "height": 3,
        "blocksMovement": false,
        "blocksSight": false,
        "dayVision": 0,
        "nightVision": 0
    }
    ]
}
```

### root

Логический корень каталога токенов. Все пути внутри `folders.path` считаются относительно него.

### Поля defaults

Значения по умолчанию для всех папок:

- `kind` — тип ассета. Для этой папки используется `token`.
- `category` — категория по умолчанию.
- `gridSize` — базовый размер в клетках.
- `defaultWidth` — ширина по умолчанию в клетках.
- `defaultHeight` — высота по умолчанию в клетках.
- `blocksMovement` — блокирует ли токен перемещение.
- `blocksSight` — блокирует ли токен обзор.
- `isToken` — признак, что запись относится к токенам.
- `isDecoration` — признак декорации. Для токенов обычно `false`.
- `displayNameFrom` — из чего брать имя по умолчанию. Здесь: из имени файла.
- `nameTransform` — как преобразовывать имя файла в отображаемое название. Здесь: `title_case`.

### Поля внутри folders:

Каждый элемент массива описывает отдельную подпапку внутри `uploads/assets/tokens`.

- `path` — подпапка относительно `uploads/assets/tokens`.
- `category` — категория, которую увидит UI.
- `gridSize` — размер токена в клетках для этой папки.
- `defaultWidth` — ширина по умолчанию.
- `defaultHeight` — высота по умолчанию.
- `dayVision` — обзор днём.
- `nightVision` — обзор ночью.

## names.ru.json

Файл задаёт русские названия для файлов токенов.

Пример:
```json
{
  "orc.png": "Орк",
  "medium/creatures/orc.png": "Орк",
  "uploads/assets/tokens/medium/creatures/orc.png": "Орк"
}
```

### Какой ключ допустим
- имя файла;
- путь относительно `uploads/assets/tokens`;
- полный путь от root каталога.
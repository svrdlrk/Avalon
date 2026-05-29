# uploads/maps

Папка с картами, библиотекой карт, эталонными картами и сохранениями.

Канонический root для этого каталога:
`uploads/maps`

## Структура каталога

В `catalog.json` для карт описаны:
- `root`
- `finishedRoot`
- `backupsRoot`
- `referenceRoot`
- `referenceCatalog`

Это означает, что карты разделяются на несколько физических областей хранения:
- `uploads/maps/finished`
- `uploads/maps/backups`
- `uploads/maps/reference`

## Как устроен `catalog.json`

Пример из репозитория:

```json
{
  "schemaVersion": 1,
  "root": "uploads/maps",
  "finishedRoot": "uploads/maps/finished",
  "backupsRoot": "uploads/maps/backups",
  "referenceRoot": "uploads/maps/reference",
  "referenceCatalog": "uploads/maps/reference/catalog.json"
}
```

### `schemaVersion`
Версия схемы каталога карт.

### `root`
Корень каталога карт:
`uploads/maps`

### `finishedRoot`
Папка с готовыми картами или сессиями, которые уже завершены:
`uploads/maps/finished`

### `backupsRoot`
Папка резервных копий:
`uploads/maps/backups`

### `referenceRoot`
Папка эталонных карт и референсов:
`uploads/maps/reference`

### `referenceCatalog`
Путь к каталогу референсных карт:
`uploads/maps/reference/catalog.json`

## Что хранить в папках

### `finished`
Готовые карты, которые используются в работе или уже прошли подготовку.

### `backups`
Автосохранения, резервные копии, промежуточные версии.

### `reference`
Эталонные изображения и исходники, по которым строится карта.

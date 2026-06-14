# map-editor

## Назначение

`map-editor` — отдельное JavaFX-приложение для создания и редактирования карт Avalon. Оно работает с проектами карт, слоями, ассетами, стенами, Fog of War, микролокациями и объектами размещения.

## Роль в общей архитектуре

Редактор формирует и поддерживает контент, который затем потребляется сервером и игровыми клиентами:
- подготавливает карту;
- редактирует слои и геометрию;
- сохраняет workspace/project;
- помогает собрать данные для battle view.

## Зависимости

- Java 17
- JavaFX 17
- `shared`
- `jackson-databind`

## Публичные интерфейсы

Ключевые точки входа и сервисы:
- `com.avalon.dnd.mapeditor.MapEditorApplication`
- `com.avalon.dnd.mapeditor.ui.MapEditorPane`
- `com.avalon.dnd.mapeditor.model.MapProject`
- `com.avalon.dnd.mapeditor.model.AssetCatalog`
- `com.avalon.dnd.mapeditor.service.ProjectRepository`
- `com.avalon.dnd.mapeditor.service.AssetCatalogLoader`

## Конфигурация

Поддерживаются:
- `--project` — открыть проект при старте
- `--assets` — указать каталог ассетов
- `avalon.assets.dir` — альтернативный способ задать каталог ассетов

## Локальный запуск

```bash
./gradlew :map-editor:run
```

Пример с параметрами:

```bash
./gradlew :map-editor:run --args="--project=/path/to/project --assets=/path/to/assets"
```

## Тестирование

```bash
./gradlew :map-editor:test
```

## Примеры использования

- Создать пустой workspace.
- Открыть существующий проект карты.
- Настроить фон, reference overlay, terrain layer, wall layer и fog.
- Разместить токены, ассеты и микролокации.
- Сохранить проект и продолжить редактирование позже.

## Особенности реализации

- Редактор не является частью player-client; это отдельный инструмент для контент-креатора.
- В UI есть набор инструментов: select, move, reference, place, token, brush, terrain, wall, wallEdit, erase, pan.
- Поддерживаются autosave, undo/redo и работа с несколькими вкладками проектов.
- Слой стены и слой terrain отражают то, что затем используется на runtime.
- В проекте есть отдельная модель workspace/ProjectRepository, а не только один JSON-файл.

## Ограничения и известные проблемы

- Формат workspace/project нужно стабилизировать и задокументировать отдельно.
- Часть логики зависит от внутренних editor model классов.
- Пока отсутствует отдельная API-документация по формату карт.

[TODO: ОПИСАТЬ точный формат workspace, структуру JSON и правила совместимости между версиями редактора.]

# shared

## Назначение

`shared` — это общий контрактный модуль Avalon. Здесь находятся DTO, модели состояния, вспомогательные правила размещения и структуры WebSocket-сообщений, которые должны одинаково пониматься сервером и всеми клиентами.

## Роль в общей архитектуре

Модуль устраняет дублирование контрактов между:
- `server`
- `dm-client`
- `player-client`
- `map-editor`

Это снижает риск рассинхронизации форматов данных и делает изменение протокола более контролируемым.

## Зависимости

- `com.fasterxml.jackson.core:jackson-databind`
- Java 17
- Других модулей репозитория этот модуль не требует

## Публичные интерфейсы

Основные типы, на которые опирается вся система:
- `GridConfig`
- `TokenDto`
- `MapObjectDto`
- `PlayerDto`
- `SessionStateDto`
- `MapLayoutUpdateDto`
- `VisibilityStateDto`
- `VisibilityShareSuggestionDto`
- `InitiativeStateDto`
- `JoinSessionRequestDto`
- `WsMessage<T>`
- `WsEventType`

## Конфигурация

У модуля нет runtime-конфигурации. Поведение определяется только кодом и сериализацией Jackson.

## Локальный запуск

`shared` не запускается как отдельное приложение. Его нужно собирать вместе с зависимыми модулями.

```bash
./gradlew :shared:build
```

## Тестирование

```bash
./gradlew :shared:test
```

Если тестов нет, команда выполнится быстро и покажет текущее состояние покрытия.

## Примеры использования

Пример типового сценария:

```java
SessionStateDto state = ...;
WsMessage<SessionStateDto> message = new WsMessage<>("SESSION_STATE", sessionId, version, state);
```

Пример обновления сетки:

```java
GridConfig grid = new GridConfig(64, 20, 20);
grid.setOffsetX(0);
grid.setOffsetY(0);
```

## Особенности реализации

- DTO реализованы как простые Java-bean классы.
- Часть полей допускает `null`, чтобы не ломать совместимость при расширении состояния.
- `SessionStateDto` хранит как боевые данные, так и редакторские метаданные: `terrainLayer`, `wallLayer`, `fogSettings`, `microLocations`, `assetPackIds`.
- `VisibilityStateDto` хранит серверно-вычисляемый snapshot видимости.
- `WsEventType` задаёт набор событий realtime-синхронизации.

## Ограничения и известные проблемы

- Нет строгой схемы версионирования DTO.
- Часть классов используется и как доменная модель, и как транспортный контракт.
- Изменения в полях нужно согласовывать одновременно между сервером и клиентами.

# server

## Назначение

`server` — backend Avalon на Spring Boot. Он отвечает за создание и восстановление сессий, синхронизацию состояния через WebSocket/STOMP, REST-операции для DM-клиента и вычисление боевого состояния, которое получают клиенты.

## Роль в общей архитектуре

Сервер является центром синхронизации:
- принимает команды от DM-клиента и player-client;
- хранит актуальное состояние сессии;
- рассылает realtime-события;
- формирует начальный `SessionStateDto` для подключающихся клиентов.

## Зависимости

Из `server/build.gradle`:
- `spring-boot-starter-web`
- `spring-boot-starter-websocket`
- `spring-boot-starter-data-jpa`
- `jackson-databind`
- `h2` для разработки
- Java 17
- общий модуль `shared`

## Публичные интерфейсы

### Известные REST-endpoints, используемые клиентами

- `POST /api/session/create`
- `POST /api/session/{sessionId}/save?name=...`
- `POST /api/session/{sessionId}/load`
- `DELETE /api/session/{sessionId}/saved`
- `POST /api/session/{sessionId}/import-map`

### WebSocket/STOMP

- WebSocket endpoint: `/ws`
- Join command: `/app/session.join`
- Session topic: `/topic/session/{sessionId}`
- Join response topic: `/topic/session/{sessionId}/join/{joinNonce}`
- Private topic: `/topic/session/{sessionId}/private/{playerId}`

### События realtime-синхронизации

Судя по контрактам `shared`, сервер работает с типами:
- `TOKEN_MOVED`
- `TOKEN_ADDED`
- `TOKEN_REMOVED`
- `TOKEN_ASSIGNED`
- `TOKEN_HP`
- `MAP_UPDATED`
- `MAP_OBJECT_ADDED`
- `MAP_OBJECT_REMOVED`
- `PLAYER_JOINED`
- `PLAYER_LEFT`
- `SESSION_STATE`
- `MAP_BACKGROUND_UPDATED`
- `INITIATIVE_UPDATED`

## Конфигурация

Сервер принимает только минимальную runtime-настройку, которая используется launcher'ом:
- `avalon.launcher.session`
- `AVALON_LAUNCHER_SESSION`

Для разработки используется H2, а PostgreSQL пока оставлен как комментарий.

## Локальный запуск

```bash
./gradlew :server:bootRun
```

Если нужен отдельный сборочный прогон:

```bash
./gradlew :server:build
```

## Тестирование

```bash
./gradlew :server:test
```

## Примеры использования

Типичный жизненный цикл:
1. `createSession`
2. подключение DM и игроков через STOMP
3. realtime-обновления карты и боевого состояния
4. `saveSession` / `loadSession` по запросу DM

## Особенности реализации

- Используется Spring Boot 3.5.x и Java 17.
- Для локальной разработки включён H2.
- Сервер интегрируется со `shared` через общий контракт DTO.
- Realtime-логика построена вокруг WebSocket/STOMP, а не polling.
- Сервер поддерживает состояние карты, объектов, токенов, инициативы и visibility.

## Ограничения и известные проблемы

- В проекте нет полноценной публичной авторизации.
- Это локальный инструмент, поэтому модель безопасности намеренно упрощена.
- Документация API пока отсутствует как отдельный файл и частично компенсируется README.

[TODO: ОПИСАТЬ точные контроллеры, сервисы и таблицы БД после окончательной стабилизации server-модуля.]

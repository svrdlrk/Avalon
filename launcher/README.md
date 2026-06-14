# launcher

## Назначение

`launcher` — dev-launcher Avalon. Он поднимает backend, DM-клиент и player-client как единую локальную связку, следит за процессами и автоматически закрывает цепочку, когда клиенты завершают работу.

## Роль в общей архитектуре

Это инфраструктурный orchestrator для разработки и демонстрации проекта. Он упрощает запуск всей связки одним действием.

## Зависимости

- Java 17
- `gradlew` / `gradlew.bat`
- Windows shell tooling для штатного сценария запуска
- `player-client` с npm scripts
- `server`
- `dm-client`

## Публичные интерфейсы

- `com.avalon.dnd.launcher.DevLauncherApplication`
- `com.avalon.dnd.launcher.DevLauncher`

## Конфигурация

Используются:
- `AVALON_PROJECT_ROOT`
- `AVALON_LAUNCHER_SESSION`
- `AVALON_LAUNCHER_CONTROL_URL`

Также в коде видны порты:
- `server` — `8080`
- `player-client` — `5173`

## Локальный запуск

```bash
./gradlew :launcher:run
```

### Важное ограничение

Текущая реализация launcher использует:
- `cmd`
- `gradlew.bat`
- `npm.cmd`

Поэтому штатный сценарий фактически Windows-first.

## Тестирование

```bash
./gradlew :launcher:test
```

## Примеры использования

- Поднять `server`.
- Поднять `dm-client`.
- Поднять `player-client`.
- Открыть браузер автоматически на `http://localhost:5173/`.
- Закрыть всё как единый набор процессов.

## Особенности реализации

- Launcher создаёт control server для heartbeat/close-notification.
- Есть watchdog-логика для очистки процессов.
- Launcher следит за жизненным циклом `player-client` и `dm-client`.
- Когда оба клиента закрыты, launcher сам завершает работу.
- Это удобная точка запуска для локальной демонстрации проекта из резюме.

## Ограничения и известные проблемы

- Ориентация на Windows.
- Не является полноценным production process manager.
- Не заменяет Docker/Compose или системный сервис менеджер.

[TODO: ОПИСАТЬ кроссплатформенную стратегию запуска, если launcher будет адаптирован под Linux/macOS.]

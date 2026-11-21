# VisitsRecording

Мобильное приложение для полевых сотрудников (фиксация проверок на объектах) + веб-админка + REST API. Заказчик: ООО «ПЛАЗМА ДИНАМИКА».

## Структура

- `backend/` — REST API: Kotlin + Ktor + Exposed + PostgreSQL
- `admin/` — веб-админка (стек уточняется)
- `mobile/` — Android-приложение: Kotlin + Jetpack Compose
- `docs/` — архитектура, спецификации, гайды для персонала
- `Требования/`, `Сущности для бека.md` — первоисточники (не удалять)

## Быстрый старт (бекенд)

```bash
docker compose up -d      # PostgreSQL + MinIO
cd backend
./gradlew run             # API на http://localhost:8080/health
```

## Быстрый старт (админка)

```bash
cd admin
npm install
npm run dev               # http://localhost:5173 (нужен запущенный бек)
```

Нужна Java 17+. Миграции БД применяются автоматически при старте.

Тестовые пользователи (создаются миграцией V2):
- `admin` / `admin123` — роль ADMIN
- `inspector` / `inspector123` — роль INSPECTOR

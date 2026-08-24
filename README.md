# cian-parser

Бот, который периодически опрашивает заданные поисковые URL на Cian.ru и присылает уведомления
о новых объявлениях в Telegram.

> **Важно:** на машине/сервере, где запущен контейнер, должен быть включён VPN — без него
> Telegram API (`api.telegram.org`) недоступен и бот не сможет отправлять уведомления.

## Быстрый старт (Docker)

1. Создайте Telegram-бота через [@BotFather](https://t.me/BotFather) (команда `/newbot`) и
   получите токен.
2. Узнайте свой chat id — напишите [@userinfobot](https://t.me/userinfobot), он ответит числом.
   Для группы: добавьте бота в группу и используйте chat id группы (отрицательное число).
3. Скачайте `docker-compose.yml` и `.env.example`, переименуйте второй в `.env` и заполните:
   ```bash
   curl -O https://raw.githubusercontent.com/Ukorp/cian-parser/main/docker-compose.yml
   curl -o .env https://raw.githubusercontent.com/Ukorp/cian-parser/main/.env.example
   # отредактируйте .env: впишите BOT_TOKEN, CHAT_ID и при желании свои SEARCH_URLS
   ```
4. Запустите:
   ```bash
   docker compose up -d
   ```

Либо без compose:
```bash
docker run -d --name cian-parser --restart unless-stopped --env-file .env ghcr.io/ukorp/cian-parser:latest
```

## Обновление

```bash
docker compose pull && docker compose up -d
```
(или `docker pull ghcr.io/ukorp/cian-parser:latest && docker restart cian-parser` для варианта без compose)

## Переменные окружения

| Переменная    | Обязательна | Описание                                                              |
|---------------|:-----------:|------------------------------------------------------------------------|
| `BOT_TOKEN`   |     да      | Токен Telegram-бота от @BotFather                                     |
| `CHAT_ID`     |     да      | Chat id, куда слать уведомления                                       |
| `SEARCH_URLS` |     нет     | Один или несколько поисковых URL Cian.ru через запятую (без пробелов) |
| `LOG_LEVEL`   |     нет     | Уровень логирования `ru.ukorp.parser.*` (по умолчанию `INFO`). `DEBUG` даёт сквозную трассировку каждого цикла опроса: HTTP-запросы (URL, прокси, размер ответа), id распарсенных и новых объявлений, обогащение фото, загрузка изображений, отправка в Telegram |

Остальные параметры (`parser.poll-interval`, `parser.proxy.*` и т.д.) можно переопределить через
переменные окружения в `docker-compose.yml` (секция `environment:`) — по умолчанию использовать
не обязательно.

## Известное ограничение

Список уже показанных объявлений хранится только в памяти процесса. При каждом перезапуске
контейнера (в т.ч. при обновлении образа) все текущие объявления по заданным фильтрам будут
разосланы заново, как будто они новые.

## Сборка локально (для разработки)

```bash
./gradlew bootJar
docker build -t cian-parser .
docker run --rm --env-file .env cian-parser
```

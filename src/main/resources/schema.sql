CREATE TABLE IF NOT EXISTS chat_search_url (
    chat_id BIGINT NOT NULL,
    search_url TEXT NOT NULL,
    PRIMARY KEY (chat_id, search_url)
);

CREATE TABLE IF NOT EXISTS seen_offer (
    chat_id BIGINT NOT NULL,
    search_url TEXT NOT NULL,
    offer_id TEXT NOT NULL,
    seen_order INTEGER NOT NULL,
    PRIMARY KEY (chat_id, search_url, offer_id)
);

-- 1. Создаем новую таблицу с нужным PRIMARY KEY
CREATE TABLE IF NOT EXISTS chat_search_url_new (
   chat_id BIGINT NOT NULL PRIMARY KEY,
   search_url TEXT NOT NULL
);

INSERT OR REPLACE INTO chat_search_url_new (chat_id, search_url)
SELECT chat_id, search_url FROM chat_search_url;

DROP TABLE chat_search_url;

ALTER TABLE chat_search_url_new RENAME TO chat_search_url;
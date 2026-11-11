-- TEMPORARY local copy so P1 can boot the app. P3 owns this file; replace with P3's version.
CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    type     VARCHAR(10)  NOT NULL,
    user_id  BIGINT NULL,
    CONSTRAINT fk_cat_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    category_id  BIGINT NULL,
    amount       DECIMAL(12,2) NOT NULL,
    type         VARCHAR(10) NOT NULL,
    description  VARCHAR(255),
    date         DATE NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
CREATE INDEX idx_tx_user_date     ON transactions (user_id, date);
CREATE INDEX idx_tx_user_cat_date ON transactions (user_id, category_id, date);

CREATE TABLE budgets (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    category_id  BIGINT NOT NULL,
    month        INT NOT NULL,
    year         INT NOT NULL,
    limit_amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_budget_user     FOREIGN KEY (user_id)     REFERENCES users(id),
    CONSTRAINT fk_budget_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT uk_budget_user_cat_month_year UNIQUE (user_id, category_id, month, year)
);

CREATE TABLE alerts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    budget_id  BIGINT NOT NULL,
    type       VARCHAR(20) NOT NULL,
    message    VARCHAR(255) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_user   FOREIGN KEY (user_id)   REFERENCES users(id),
    CONSTRAINT fk_alert_budget FOREIGN KEY (budget_id) REFERENCES budgets(id) ON DELETE CASCADE
);

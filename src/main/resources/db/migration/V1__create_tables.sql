-- V1: Create the tables for the Expense and Budget Management System.

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL,
    user_id BIGINT NULL,

        CONSTRAINT chk_categories_type
        CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    amount DECIMAL(12,2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    `date` DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transactions_type
        CHECK (type IN ('INCOME', 'EXPENSE')),

    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories(id),

    INDEX idx_tx_user_date (user_id, `date`),
    INDEX idx_tx_user_cat_date (user_id, category_id, `date`)
) ENGINE=InnoDB;

CREATE TABLE budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    month INT NOT NULL,
    year INT NOT NULL,
    limit_amount DECIMAL(12,2) NOT NULL,

    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_budgets_category
        FOREIGN KEY (category_id) REFERENCES categories(id),

    CONSTRAINT uq_budget_user_category_month_year
        UNIQUE (user_id, category_id, month, year)
) ENGINE=InnoDB;

CREATE TABLE alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    budget_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    message VARCHAR(255) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_alerts_type
        CHECK (type IN ('WARNING', 'EXCEEDED')),
    CONSTRAINT fk_alerts_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_alerts_budget
        FOREIGN KEY (budget_id) REFERENCES budgets(id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

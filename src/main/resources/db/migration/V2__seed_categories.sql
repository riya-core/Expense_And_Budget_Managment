-- V2: Insert the default income and expense categories.

INSERT INTO categories (name, type, user_id)
VALUES
    ('Salary', 'INCOME', NULL),
    ('Freelance', 'INCOME', NULL),
    ('Gift', 'INCOME', NULL),
    ('Other Income', 'INCOME', NULL),
    ('Food', 'EXPENSE', NULL),
    ('Rent', 'EXPENSE', NULL),
    ('Transport', 'EXPENSE', NULL),
    ('Utilities', 'EXPENSE', NULL),
    ('Entertainment', 'EXPENSE', NULL),
    ('Health', 'EXPENSE', NULL),
    ('Shopping', 'EXPENSE', NULL),
    ('Education', 'EXPENSE', NULL),
    ('Other', 'EXPENSE', NULL);

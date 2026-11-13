-- Removes everything created by scripts/smoke-test.ps1 (users smoke-a-*/smoke-b-*@example.com).
-- Run in MySQL Workbench against expense_db. Safe: it only touches those users' rows.
USE expense_db;
SET SQL_SAFE_UPDATES = 0;

DELETE a FROM alerts a       JOIN users u ON a.user_id = u.id WHERE u.email LIKE 'smoke-%@example.com';
DELETE b FROM budgets b      JOIN users u ON b.user_id = u.id WHERE u.email LIKE 'smoke-%@example.com';
DELETE t FROM transactions t JOIN users u ON t.user_id = u.id WHERE u.email LIKE 'smoke-%@example.com';
DELETE c FROM categories c   JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'smoke-%@example.com';
DELETE FROM users WHERE email LIKE 'smoke-%@example.com';

SET SQL_SAFE_UPDATES = 1;

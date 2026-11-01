-- =============================================================
-- Jovens Buzz — OPTIONAL local database initialisation (XAMPP / MySQL / MariaDB)
--
-- Flyway is the authoritative schema mechanism: the Spring Boot API creates and
-- migrates every table automatically on startup. This script only creates the
-- empty databases and (optionally) a dedicated least-privilege user.
--
-- Run in phpMyAdmin (SQL tab) or:
--   C:\xampp\mysql\bin\mysql.exe -u root -p < backend\scripts\init-database.sql
-- =============================================================

CREATE DATABASE IF NOT EXISTS jovens_buzz
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Separate schema used only by automated tests (never point tests at real data)
CREATE DATABASE IF NOT EXISTS jovens_buzz_test
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Recommended: a dedicated application user instead of root.
-- Replace the password, then set DB_USERNAME / DB_PASSWORD in backend/.env.
-- CREATE USER IF NOT EXISTS 'jovens_app'@'localhost' IDENTIFIED BY 'change-this-password';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES
--   ON jovens_buzz.* TO 'jovens_app'@'localhost';
-- GRANT ALL PRIVILEGES ON jovens_buzz_test.* TO 'jovens_app'@'localhost';
-- FLUSH PRIVILEGES;

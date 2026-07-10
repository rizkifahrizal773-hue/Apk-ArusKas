-- --------------------------------------------------------
-- SQL Database Schema for ArusKas Personal Finance Tracker
-- Database Target: MySQL / MariaDB (Multi-User Configuration)
-- --------------------------------------------------------

-- CREATE DATABASE IF NOT EXISTS `aruskas`;
-- USE `aruskas`;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL,
  `email` VARCHAR(100) NOT NULL UNIQUE,
  `password` VARCHAR(255) NOT NULL,
  `token` VARCHAR(255) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Categories Table (user_id NULL means global default categories)
CREATE TABLE IF NOT EXISTS `categories` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(50) NOT NULL,
  `icon` VARCHAR(50) NOT NULL,
  `color` VARCHAR(10) NOT NULL,
  `type` ENUM('income', 'expense') NOT NULL,
  `user_id` INT DEFAULT NULL,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Transactions Table
CREATE TABLE IF NOT EXISTS `transactions` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `type` ENUM('income', 'expense') NOT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `category_id` INT NOT NULL,
  `note` VARCHAR(255) DEFAULT NULL,
  `transaction_date` DATE NOT NULL,
  `receipt_url` VARCHAR(255) DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Budgets Table
CREATE TABLE IF NOT EXISTS `budgets` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `category_id` INT NOT NULL,
  `month` VARCHAR(7) NOT NULL, -- Format YYYY-MM
  `limit_amount` DECIMAL(15, 2) NOT NULL,
  UNIQUE KEY `unique_user_category_month` (`user_id`, `category_id`, `month`),
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Recurring Transactions Table
CREATE TABLE IF NOT EXISTS `recurring_transactions` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `user_id` INT NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `type` ENUM('income', 'expense') NOT NULL,
  `amount` DECIMAL(15, 2) NOT NULL,
  `category_id` INT NOT NULL,
  `due_day` INT NOT NULL, -- Day of month: 1 - 31
  `reminder_enabled` TINYINT(1) DEFAULT 1,
  `is_active` TINYINT(1) DEFAULT 1,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  FOREIGN KEY (`category_id`) REFERENCES `categories`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Device Tokens Table (FCM)
CREATE TABLE IF NOT EXISTS `device_tokens` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `token` VARCHAR(255) NOT NULL UNIQUE,
  `user_id` INT DEFAULT NULL,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- --------------------------------------------------------
-- Seed Data for Default Global Categories
-- --------------------------------------------------------
INSERT INTO `categories` (`name`, `icon`, `color`, `type`, `user_id`) VALUES
('Makanan & Minuman', 'food', '#E74C3C', 'expense', NULL),
('Transportasi', 'transport', '#3498DB', 'expense', NULL),
('Belanja Bulanan', 'shopping', '#9B59B6', 'expense', NULL),
('Tagihan & Listrik', 'bills', '#F1C40F', 'expense', NULL),
('Hiburan', 'entertainment', '#E67E22', 'expense', NULL),
('Kesehatan', 'health', '#1ABC9C', 'expense', NULL),
('Gaji Pokok', 'salary', '#2ECC71', 'income', NULL),
('Bonus Kerja', 'bonus', '#E84393', 'income', NULL),
('Hadiah', 'gift', '#6C5CE7', 'income', NULL),
('Lain-lain', 'other', '#2D3436', 'expense', NULL);

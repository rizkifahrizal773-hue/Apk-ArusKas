<?php
/**
 * Database Configurations & Settings
 * ArusKas REST API Backend
 */

define('DB_HOST', 'localhost');
define('DB_PORT', '3306');
define('DB_NAME', 'site4584_star');
define('DB_USER', 'site4584_apk');
define('DB_PASS', '+1&Q{{xjyipVQr@W');

// Dynamically resolve base URL for receipt image links based on requesting client
// (The emulator requests via '10.0.2.2:8000', while localhost requests via '127.0.0.1:8000')
$requestHost = isset($_SERVER['HTTP_HOST']) ? $_SERVER['HTTP_HOST'] : '127.0.0.1:8000';
define('BASE_URL', 'http://' . $requestHost . '/');

try {
    $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=utf8mb4";
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
} catch (PDOException $e) {
    // Send standard error envelope if DB connection fails
    header('Content-Type: application/json; charset=utf-8');
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Gagal terhubung ke database: ' . $e->getMessage(),
        'data' => null
    ]);
    exit();
}

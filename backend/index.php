<?php
/**
 * REST API Router & Controller for ArusKas
 * Implements Vanilla PHP connection logic with Multi-User Authentication
 */

// Enable CORS
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");
header("Content-Type: application/json; charset=utf-8");

// Register global exception handler to return clean JSON errors
set_exception_handler(function ($e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Server Exception: ' . $e->getMessage() . ' in ' . $e->getFile() . ' on line ' . $e->getLine(),
        'data' => null
    ]);
    exit();
});

// Convert PHP errors/warnings to exceptions so they are caught by the exception handler
set_error_handler(function ($severity, $message, $file, $line) {
    if (!(error_reporting() & $severity)) {
        return;
    }
    throw new ErrorException($message, 0, $severity, $file, $line);
});

// Handle preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

require_once __DIR__ . '/config.php';

// Helper to return consistent JSON envelopes as specified in PRD Section 6
function jsonResponse($success, $message, $data = null, $statusCode = 200) {
    http_response_code($statusCode);
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data' => $data
    ]);
    exit();
}

// Helper to parse JSON request bodies
function getRequestBody() {
    $json = file_get_contents('php://input');
    return json_decode($json, true) ?? [];
}

// Helper to fetch category by ID
function fetchCategoryById($pdo, $id) {
    $stmt = $pdo->prepare("SELECT * FROM categories WHERE id = ?");
    $stmt->execute([$id]);
    $cat = $stmt->fetch();
    if ($cat) {
        $cat['id'] = (int)$cat['id'];
        $cat['user_id'] = $cat['user_id'] ? (int)$cat['user_id'] : null;
    }
    return $cat;
}

// Authenticates current request using the Authorization header
function getCurrentUser($pdo) {
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    if (empty($authHeader) && isset($_SERVER['HTTP_AUTHORIZATION'])) {
        $authHeader = $_SERVER['HTTP_AUTHORIZATION'];
    }

    if (preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        $token = trim($matches[1]);
        if (!empty($token)) {
            $stmt = $pdo->prepare("SELECT * FROM users WHERE token = ?");
            $stmt->execute([$token]);
            $user = $stmt->fetch();
            if ($user) {
                $user['id'] = (int)$user['id'];
                return $user;
            }
        }
    }

    // Attempt token from query params or post body (fallback for debugging)
    $tokenParam = $_GET['token'] ?? $_POST['token'] ?? '';
    if (!empty($tokenParam)) {
        $stmt = $pdo->prepare("SELECT * FROM users WHERE token = ?");
        $stmt->execute([$tokenParam]);
        $user = $stmt->fetch();
        if ($user) {
            $user['id'] = (int)$user['id'];
            return $user;
        }
    }

    jsonResponse(false, "Sesi kedaluwarsa. Silakan login kembali.", null, 401);
}

// Parse request paths
$scriptName = dirname($_SERVER['SCRIPT_NAME']);
$requestUri = $_SERVER['REQUEST_URI'];
if (strpos($requestUri, $scriptName) === 0) {
    $requestUri = substr($requestUri, strlen($scriptName));
}
$path = parse_url($requestUri, PHP_URL_PATH);
$path = trim($path, '/');
$method = $_SERVER['REQUEST_METHOD'];

// --------------------------------------------------------
// ROUTER & CONTROLLER ACTIONS
// --------------------------------------------------------

// AUTHENTICATION ENDPOINTS (NO AUTH REQUIRED)
if (preg_match('#^api/auth/register$#', $path)) {
    if ($method === 'POST') {
        $body = getRequestBody();
        $name = $body['name'] ?? null;
        $email = $body['email'] ?? null;
        $password = $body['password'] ?? null;

        if (!$name || !$email || !$password) {
            jsonResponse(false, "Nama, email, dan password wajib diisi", null, 422);
        }

        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            jsonResponse(false, "Format email tidak valid", null, 422);
        }

        try {
            // Check if email already registered
            $stmt = $pdo->prepare("SELECT COUNT(*) FROM users WHERE email = ?");
            $stmt->execute([$email]);
            if ($stmt->fetchColumn() > 0) {
                jsonResponse(false, "Email sudah terdaftar", null, 422);
            }

            // Hash password and generate session token
            $hashedPassword = password_hash($password, PASSWORD_BCRYPT);
            $token = bin2hex(random_bytes(32));

            $stmt = $pdo->prepare("INSERT INTO users (name, email, password, token) VALUES (?, ?, ?, ?)");
            $stmt->execute([$name, $email, $hashedPassword, $token]);
            $userId = $pdo->lastInsertId();
        } catch (PDOException $e) {
            jsonResponse(false, "Database Error: " . $e->getMessage(), null, 500);
        } catch (Exception $e) {
            jsonResponse(false, "Server Error: " . $e->getMessage(), null, 500);
        }

        $user = [
            'id' => (int)$userId,
            'name' => $name,
            'email' => $email,
            'token' => $token
        ];

        jsonResponse(true, "Registrasi berhasil", $user, 201);
    }
}
elseif (preg_match('#^api/auth/login$#', $path)) {
    if ($method === 'POST') {
        $body = getRequestBody();
        $email = $body['email'] ?? null;
        $password = $body['password'] ?? null;

        if (!$email || !$password) {
            jsonResponse(false, "Email dan password wajib diisi", null, 422);
        }

        $stmt = $pdo->prepare("SELECT * FROM users WHERE email = ?");
        $stmt->execute([$email]);
        $userRow = $stmt->fetch();

        if (!$userRow || !password_verify($password, $userRow['password'])) {
            jsonResponse(false, "Email atau password salah", null, 401);
        }

        // Generate / Update new session token on successful login
        $token = bin2hex(random_bytes(32));
        $stmt = $pdo->prepare("UPDATE users SET token = ? WHERE id = ?");
        $stmt->execute([$token, $userRow['id']]);

        $user = [
            'id' => (int)$userRow['id'],
            'name' => $userRow['name'],
            'email' => $userRow['email'],
            'token' => $token
        ];

        jsonResponse(true, "Login berhasil", $user);
    }
}

// --------------------------------------------------------
// PROTECTED ENDPOINTS (REQUIRES AUTHORIZATION HEADER)
// --------------------------------------------------------

// 6.1 CATEGORIES
elseif (preg_match('#^api/categories$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $type = $_GET['type'] ?? null;
        if ($type) {
            // Merges global default categories (user_id IS NULL) and the user's custom categories
            $stmt = $pdo->prepare("SELECT * FROM categories WHERE type = ? AND (user_id IS NULL OR user_id = ?)");
            $stmt->execute([$type, $userId]);
        } else {
            $stmt = $pdo->prepare("SELECT * FROM categories WHERE user_id IS NULL OR user_id = ?");
            $stmt->execute([$userId]);
        }
        $categories = $stmt->fetchAll();
        // Convert IDs to correct data types
        foreach ($categories as &$c) {
            $c['id'] = (int)$c['id'];
            $c['user_id'] = $c['user_id'] ? (int)$c['user_id'] : null;
        }
        jsonResponse(true, "Kategori berhasil diambil", $categories);
        
    } elseif ($method === 'POST') {
        $body = getRequestBody();
        $name = $body['name'] ?? null;
        $icon = $body['icon'] ?? null;
        $color = $body['color'] ?? null;
        $type = $body['type'] ?? null;

        if (!$name || !$icon || !$color || !$type) {
            jsonResponse(false, "Nama, ikon, warna, dan tipe kategori wajib diisi", null, 422);
        }

        // Validate uniqueness of name per type scoped to global categories or the current user's categories
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE name = ? AND type = ? AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$name, $type, $userId]);
        if ($stmt->fetchColumn() > 0) {
            jsonResponse(false, "Nama kategori sudah digunakan untuk tipe ini", null, 422);
        }

        $stmt = $pdo->prepare("INSERT INTO categories (name, icon, color, type, user_id) VALUES (?, ?, ?, ?, ?)");
        $stmt->execute([$name, $icon, $color, $type, $userId]);
        $id = $pdo->lastInsertId();

        $category = fetchCategoryById($pdo, $id);
        jsonResponse(true, "Kategori berhasil dibuat", $category, 201);
    }
} 
elseif (preg_match('#^api/categories/(\d+)$#', $path, $matches)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];
    $id = (int)$matches[1];

    // Ensure category exists and belongs to the user (global categories cannot be modified or deleted)
    $stmt = $pdo->prepare("SELECT * FROM categories WHERE id = ?");
    $stmt->execute([$id]);
    $existingCat = $stmt->fetch();
    if (!$existingCat) {
        jsonResponse(false, "Kategori tidak ditemukan", null, 404);
    }
    if ($existingCat['user_id'] === null || (int)$existingCat['user_id'] !== $userId) {
        jsonResponse(false, "Kategori bawaan/global tidak dapat diubah atau dihapus", null, 403);
    }
    
    if ($method === 'PUT') {
        $body = getRequestBody();
        $name = $body['name'] ?? null;
        $icon = $body['icon'] ?? null;
        $color = $body['color'] ?? null;
        $type = $body['type'] ?? null;

        if (!$name || !$icon || !$color || !$type) {
            jsonResponse(false, "Nama, ikon, warna, dan tipe kategori wajib diisi", null, 422);
        }

        // Validate unique name per type scoped globally or to this user (ignoring current category ID)
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE name = ? AND type = ? AND (user_id IS NULL OR user_id = ?) AND id != ?");
        $stmt->execute([$name, $type, $userId, $id]);
        if ($stmt->fetchColumn() > 0) {
            jsonResponse(false, "Nama kategori sudah digunakan untuk tipe ini", null, 422);
        }

        $stmt = $pdo->prepare("UPDATE categories SET name = ?, icon = ?, color = ?, type = ? WHERE id = ? AND user_id = ?");
        $stmt->execute([$name, $icon, $color, $type, $id, $userId]);

        $category = fetchCategoryById($pdo, $id);
        jsonResponse(true, "Kategori berhasil diperbarui", $category);

    } elseif ($method === 'DELETE') {
        // Surface error messages if category is linked to existing transactions/budgets/recurring
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM transactions WHERE category_id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        if ($stmt->fetchColumn() > 0) {
            jsonResponse(false, "Kategori tidak dapat dihapus karena sedang digunakan dalam catatan transaksi.", null, 422);
        }

        $stmt = $pdo->prepare("SELECT COUNT(*) FROM budgets WHERE category_id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        if ($stmt->fetchColumn() > 0) {
            jsonResponse(false, "Kategori tidak dapat dihapus karena sedang digunakan dalam alokasi anggaran.", null, 422);
        }

        $stmt = $pdo->prepare("SELECT COUNT(*) FROM recurring_transactions WHERE category_id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        if ($stmt->fetchColumn() > 0) {
            jsonResponse(false, "Kategori tidak dapat dihapus karena digunakan pada pengingat tagihan berulang.", null, 422);
        }

        $stmt = $pdo->prepare("DELETE FROM categories WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        jsonResponse(true, "Kategori berhasil dihapus", null);
    }
}

// 6.2 TRANSACTIONS
elseif (preg_match('#^api/transactions$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $month = $_GET['month'] ?? null;
        $categoryId = $_GET['category_id'] ?? null;
        $type = $_GET['type'] ?? null;
        $keyword = $_GET['keyword'] ?? null;

        $sql = "SELECT t.*, c.name AS cat_name, c.icon AS cat_icon, c.color AS cat_color, c.type AS cat_type 
                FROM transactions t 
                LEFT JOIN categories c ON t.category_id = c.id 
                WHERE t.user_id = ?";
        $params = [$userId];

        if ($month) {
            $sql .= " AND t.transaction_date LIKE ?";
            $params[] = $month . '%';
        }
        if ($categoryId) {
            $sql .= " AND t.category_id = ?";
            $params[] = $categoryId;
        }
        if ($type) {
            $sql .= " AND t.type = ?";
            $params[] = $type;
        }
        if ($keyword) {
            $sql .= " AND (t.note LIKE ? OR c.name LIKE ?)";
            $params[] = '%' . $keyword . '%';
            $params[] = '%' . $keyword . '%';
        }

        $sql .= " ORDER BY t.transaction_date DESC, t.id DESC";

        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $results = $stmt->fetchAll();

        $transactions = [];
        foreach ($results as $r) {
            $transactions[] = [
                'id' => (int)$r['id'],
                'type' => $r['type'],
                'amount' => (double)$r['amount'],
                'category_id' => (int)$r['category_id'],
                'category' => $r['category_id'] ? [
                    'id' => (int)$r['category_id'],
                    'name' => $r['cat_name'],
                    'icon' => $r['cat_icon'],
                    'color' => $r['cat_color'],
                    'type' => $r['cat_type']
                ] : null,
                'note' => $r['note'],
                'transaction_date' => $r['transaction_date'],
                'receipt_url' => $r['receipt_url'],
                'created_at' => $r['created_at'],
                'updated_at' => $r['updated_at']
            ];
        }
        jsonResponse(true, "Daftar transaksi berhasil diambil", $transactions);

    } elseif ($method === 'POST') {
        // Parse multipart/form-data or fallback to JSON
        $type = $_POST['type'] ?? null;
        $amount = $_POST['amount'] ?? null;
        $categoryId = $_POST['category_id'] ?? null;
        $note = $_POST['note'] ?? null;
        $transactionDate = $_POST['transaction_date'] ?? null;

        if (!$type && !$amount) {
            $body = getRequestBody();
            $type = $body['type'] ?? null;
            $amount = $body['amount'] ?? null;
            $categoryId = $body['category_id'] ?? null;
            $note = $body['note'] ?? null;
            $transactionDate = $body['transaction_date'] ?? null;
        }

        if (!$type || !$amount || !$categoryId || !$transactionDate) {
            jsonResponse(false, "Tipe, nominal, kategori, dan tanggal transaksi wajib diisi", null, 422);
        }

        // Check if category is valid & accessible by this user
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE id = ? AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$categoryId, $userId]);
        if ($stmt->fetchColumn() == 0) {
            jsonResponse(false, "Kategori tidak valid atau tidak ditemukan", null, 422);
        }

        // Process optional receipt photo upload (multipart)
        $receiptUrl = null;
        if (isset($_FILES['receipt']) && $_FILES['receipt']['error'] === UPLOAD_ERR_OK) {
            $tmpPath = $_FILES['receipt']['tmp_name'];
            $fileName = $_FILES['receipt']['name'];
            $extension = strtolower(pathinfo($fileName, PATHINFO_EXTENSION));
            $newFileName = md5(time() . $fileName) . '.' . $extension;

            $uploadDir = __DIR__ . '/uploads/';
            if (!is_dir($uploadDir)) {
                mkdir($uploadDir, 0777, true);
            }

            if (move_uploaded_file($tmpPath, $uploadDir . $newFileName)) {
                $receiptUrl = BASE_URL . 'uploads/' . $newFileName;
            }
        }

        $stmt = $pdo->prepare("INSERT INTO transactions (user_id, type, amount, category_id, note, transaction_date, receipt_url) VALUES (?, ?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $type, $amount, $categoryId, $note, $transactionDate, $receiptUrl]);
        $id = $pdo->lastInsertId();

        // Return new transaction details
        $stmt = $pdo->prepare("SELECT t.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM transactions t JOIN categories c ON t.category_id = c.id WHERE t.id = ? AND t.user_id = ?");
        $stmt->execute([$id, $userId]);
        $r = $stmt->fetch();

        $transaction = [
            'id' => (int)$r['id'],
            'type' => $r['type'],
            'amount' => (double)$r['amount'],
            'category_id' => (int)$r['category_id'],
            'category' => [
                'id' => (int)$r['category_id'],
                'name' => $r['cat_name'],
                'icon' => $r['cat_icon'],
                'color' => $r['cat_color'],
                'type' => $r['cat_type']
            ],
            'note' => $r['note'],
            'transaction_date' => $r['transaction_date'],
            'receipt_url' => $r['receipt_url'],
            'created_at' => $r['created_at'],
            'updated_at' => $r['updated_at']
        ];
        jsonResponse(true, "Transaksi berhasil dicatat", $transaction, 201);
    }
}
elseif (preg_match('#^api/transactions/summary$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $month = $_GET['month'] ?? null;
        if (!$month) {
            jsonResponse(false, "Parameter bulan (month) wajib diisi", null, 422);
        }

        // 1. Current Month Totals scoped by user_id
        $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE type = 'income' AND user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$userId, $month . '%']);
        $totalIncome = (double)$stmt->fetchColumn();

        $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE type = 'expense' AND user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$userId, $month . '%']);
        $totalExpense = (double)$stmt->fetchColumn();

        $balance = $totalIncome - $totalExpense;

        // 2. Previous Month Balance scoped by user_id
        $dateObj = DateTime::createFromFormat('Y-m', $month);
        $dateObj->modify('-1 month');
        $prevMonth = $dateObj->format('Y-m');

        $stmt = $pdo->prepare("SELECT SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END) FROM transactions WHERE user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$userId, $prevMonth . '%']);
        $prevBalance = (double)$stmt->fetchColumn();

        $summary = [
            'total_income' => $totalIncome,
            'total_expense' => $totalExpense,
            'balance' => $balance,
            'previous_month_balance' => $prevBalance
        ];
        jsonResponse(true, "Ringkasan keuangan berhasil diambil", $summary);
    }
}
elseif (preg_match('#^api/transactions/(\d+)$#', $path, $matches)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];
    $id = (int)$matches[1];

    // Ensure transaction belongs to user
    $stmt = $pdo->prepare("SELECT * FROM transactions WHERE id = ?");
    $stmt->execute([$id]);
    $txRow = $stmt->fetch();
    if (!$txRow || (int)$txRow['user_id'] !== $userId) {
        jsonResponse(false, "Transaksi tidak ditemukan", null, 404);
    }

    if ($method === 'GET') {
        $stmt = $pdo->prepare("SELECT t.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM transactions t JOIN categories c ON t.category_id = c.id WHERE t.id = ? AND t.user_id = ?");
        $stmt->execute([$id, $userId]);
        $r = $stmt->fetch();

        $transaction = [
            'id' => (int)$r['id'],
            'type' => $r['type'],
            'amount' => (double)$r['amount'],
            'category_id' => (int)$r['category_id'],
            'category' => [
                'id' => (int)$r['category_id'],
                'name' => $r['cat_name'],
                'icon' => $r['cat_icon'],
                'color' => $r['cat_color'],
                'type' => $r['cat_type']
            ],
            'note' => $r['note'],
            'transaction_date' => $r['transaction_date'],
            'receipt_url' => $r['receipt_url'],
            'created_at' => $r['created_at'],
            'updated_at' => $r['updated_at']
        ];
        jsonResponse(true, "Rincian transaksi berhasil diambil", $transaction);

    } elseif ($method === 'POST' || $method === 'PUT') {
        $type = $_POST['type'] ?? null;
        $amount = $_POST['amount'] ?? null;
        $categoryId = $_POST['category_id'] ?? null;
        $note = $_POST['note'] ?? null;
        $transactionDate = $_POST['transaction_date'] ?? null;

        if (!$type && !$amount) {
            $body = getRequestBody();
            $type = $body['type'] ?? null;
            $amount = $body['amount'] ?? null;
            $categoryId = $body['category_id'] ?? null;
            $note = $body['note'] ?? null;
            $transactionDate = $body['transaction_date'] ?? null;
        }

        if (!$type || !$amount || !$categoryId || !$transactionDate) {
            jsonResponse(false, "Tipe, nominal, kategori, dan tanggal transaksi wajib diisi", null, 422);
        }

        // Verify valid category
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE id = ? AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$categoryId, $userId]);
        if ($stmt->fetchColumn() == 0) {
            jsonResponse(false, "Kategori tidak valid", null, 422);
        }

        $receiptUrl = $txRow['receipt_url'];

        // Process upload if present
        if (isset($_FILES['receipt']) && $_FILES['receipt']['error'] === UPLOAD_ERR_OK) {
            $tmpPath = $_FILES['receipt']['tmp_name'];
            $fileName = $_FILES['receipt']['name'];
            $extension = strtolower(pathinfo($fileName, PATHINFO_EXTENSION));
            $newFileName = md5(time() . $fileName) . '.' . $extension;

            $uploadDir = __DIR__ . '/uploads/';
            if (!is_dir($uploadDir)) {
                mkdir($uploadDir, 0777, true);
            }

            if (move_uploaded_file($tmpPath, $uploadDir . $newFileName)) {
                // Delete old image file locally if it exists
                if ($receiptUrl) {
                    $oldFilePath = str_replace(BASE_URL, __DIR__ . '/', $receiptUrl);
                    if (file_exists($oldFilePath)) {
                        @unlink($oldFilePath);
                    }
                }
                $receiptUrl = BASE_URL . 'uploads/' . $newFileName;
            }
        }

        $stmt = $pdo->prepare("UPDATE transactions SET type = ?, amount = ?, category_id = ?, note = ?, transaction_date = ?, receipt_url = ? WHERE id = ? AND user_id = ?");
        $stmt->execute([$type, $amount, $categoryId, $note, $transactionDate, $receiptUrl, $id, $userId]);

        // Return updated transaction
        $stmt = $pdo->prepare("SELECT t.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM transactions t JOIN categories c ON t.category_id = c.id WHERE t.id = ? AND t.user_id = ?");
        $stmt->execute([$id, $userId]);
        $r = $stmt->fetch();

        $transaction = [
            'id' => (int)$r['id'],
            'type' => $r['type'],
            'amount' => (double)$r['amount'],
            'category_id' => (int)$r['category_id'],
            'category' => [
                'id' => (int)$r['category_id'],
                'name' => $r['cat_name'],
                'icon' => $r['cat_icon'],
                'color' => $r['cat_color'],
                'type' => $r['cat_type']
            ],
            'note' => $r['note'],
            'transaction_date' => $r['transaction_date'],
            'receipt_url' => $r['receipt_url'],
            'created_at' => $r['created_at'],
            'updated_at' => $r['updated_at']
        ];
        jsonResponse(true, "Transaksi berhasil diperbarui", $transaction);

    } elseif ($method === 'DELETE') {
        if ($txRow['receipt_url']) {
            $filePath = str_replace(BASE_URL, __DIR__ . '/', $txRow['receipt_url']);
            if (file_exists($filePath)) {
                @unlink($filePath);
            }
        }

        $stmt = $pdo->prepare("DELETE FROM transactions WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        jsonResponse(true, "Transaksi berhasil dihapus", null);
    }
}

// 6.3 BUDGETS
elseif (preg_match('#^api/budgets$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $month = $_GET['month'] ?? null;
        if (!$month) {
            jsonResponse(false, "Parameter bulan (month) wajib diisi", null, 422);
        }

        // Fetch categories of type expense (global or user kustom)
        $stmt = $pdo->prepare("SELECT * FROM categories WHERE type = 'expense' AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$userId]);
        $expenseCategories = $stmt->fetchAll();

        // Fetch budgets mapped for the month for this user
        $stmt = $pdo->prepare("SELECT * FROM budgets WHERE month = ? AND user_id = ?");
        $stmt->execute([$month, $userId]);
        $monthBudgets = $stmt->fetchAll();

        $budgetsMap = [];
        foreach ($monthBudgets as $b) {
            $budgetsMap[$b['category_id']] = $b;
        }

        // Calculate spent amount per category for this month from this user's transactions
        $stmt = $pdo->prepare("SELECT category_id, SUM(amount) as spent FROM transactions WHERE type = 'expense' AND user_id = ? AND transaction_date LIKE ? GROUP BY category_id");
        $stmt->execute([$userId, $month . '%']);
        $spentResults = $stmt->fetchAll();
        
        $spentMap = [];
        foreach ($spentResults as $s) {
            $spentMap[$s['category_id']] = (double)$s['spent'];
        }

        // Merge list to return all expense categories with/without budgets set
        $outputList = [];
        foreach ($expenseCategories as $cat) {
            $catId = (int)$cat['id'];
            $hasBudget = isset($budgetsMap[$catId]);
            
            $limit = $hasBudget ? (double)$budgetsMap[$catId]['limit_amount'] : 0.0;
            $budgetId = $hasBudget ? (int)$budgetsMap[$catId]['id'] : 0;
            $spent = isset($spentMap[$catId]) ? $spentMap[$catId] : 0.0;

            $outputList[] = [
                'id' => $budgetId,
                'category_id' => $catId,
                'category' => [
                    'id' => $catId,
                    'name' => $cat['name'],
                    'icon' => $cat['icon'],
                    'color' => $cat['color'],
                    'type' => $cat['type']
                ],
                'month' => $month,
                'limit_amount' => $limit,
                'spent_amount' => $spent
            ];
        }

        jsonResponse(true, "Daftar anggaran berhasil diambil", $outputList);

    } elseif ($method === 'POST') {
        $body = getRequestBody();
        $categoryId = $body['category_id'] ?? null;
        $month = $body['month'] ?? null;
        $limitAmount = $body['limit_amount'] ?? null;

        if (!$categoryId || !$month || !$limitAmount) {
            jsonResponse(false, "Kategori, bulan, dan batas anggaran wajib diisi", null, 422);
        }

        // Verify valid category
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE id = ? AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$categoryId, $userId]);
        if ($stmt->fetchColumn() == 0) {
            jsonResponse(false, "Kategori tidak valid", null, 422);
        }

        // Check if already exists for this user + category + month
        $stmt = $pdo->prepare("SELECT id FROM budgets WHERE category_id = ? AND month = ? AND user_id = ?");
        $stmt->execute([$categoryId, $month, $userId]);
        $existingId = $stmt->fetchColumn();

        if ($existingId) {
            $stmt = $pdo->prepare("UPDATE budgets SET limit_amount = ? WHERE id = ? AND user_id = ?");
            $stmt->execute([$limitAmount, $existingId, $userId]);
            $budgetId = $existingId;
        } else {
            $stmt = $pdo->prepare("INSERT INTO budgets (user_id, category_id, month, limit_amount) VALUES (?, ?, ?, ?)");
            $stmt->execute([$userId, $categoryId, $month, $limitAmount]);
            $budgetId = $pdo->lastInsertId();
        }

        // Calculate spent amount
        $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE category_id = ? AND user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$categoryId, $userId, $month . '%']);
        $spent = (double)$stmt->fetchColumn();

        $budget = [
            'id' => (int)$budgetId,
            'category_id' => (int)$categoryId,
            'category' => fetchCategoryById($pdo, $categoryId),
            'month' => $month,
            'limit_amount' => (double)$limitAmount,
            'spent_amount' => $spent
        ];
        jsonResponse(true, "Anggaran berhasil ditetapkan", $budget, 201);
    }
}
elseif (preg_match('#^api/budgets/(\d+)$#', $path, $matches)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];
    $id = (int)$matches[1];

    // Ensure budget belongs to user
    $stmt = $pdo->prepare("SELECT * FROM budgets WHERE id = ?");
    $stmt->execute([$id]);
    $budgetRow = $stmt->fetch();
    if (!$budgetRow || (int)$budgetRow['user_id'] !== $userId) {
        jsonResponse(false, "Anggaran tidak ditemukan", null, 404);
    }

    if ($method === 'PUT') {
        $body = getRequestBody();
        $limitAmount = $body['limit_amount'] ?? null;

        if (!$limitAmount) {
            jsonResponse(false, "Batas anggaran wajib diisi", null, 422);
        }

        $stmt = $pdo->prepare("UPDATE budgets SET limit_amount = ? WHERE id = ? AND user_id = ?");
        $stmt->execute([$limitAmount, $id, $userId]);

        // Calculate spent amount
        $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE category_id = ? AND user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$budgetRow['category_id'], $userId, $budgetRow['month'] . '%']);
        $spent = (double)$stmt->fetchColumn();

        $budget = [
            'id' => (int)$id,
            'category_id' => (int)$budgetRow['category_id'],
            'category' => fetchCategoryById($pdo, $budgetRow['category_id']),
            'month' => $budgetRow['month'],
            'limit_amount' => (double)$limitAmount,
            'spent_amount' => $spent
        ];
        jsonResponse(true, "Anggaran berhasil diperbarui", $budget);

    } elseif ($method === 'DELETE') {
        $stmt = $pdo->prepare("DELETE FROM budgets WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        jsonResponse(true, "Anggaran berhasil dihapus", null);
    }
}

// 6.4 RECURRING TRANSACTIONS
elseif (preg_match('#^api/recurring-transactions$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $stmt = $pdo->prepare("SELECT r.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM recurring_transactions r JOIN categories c ON r.category_id = c.id WHERE r.user_id = ?");
        $stmt->execute([$userId]);
        $results = $stmt->fetchAll();

        $recurrings = [];
        foreach ($results as $r) {
            $recurrings[] = [
                'id' => (int)$r['id'],
                'title' => $r['title'],
                'type' => $r['type'],
                'amount' => (double)$r['amount'],
                'category_id' => (int)$r['category_id'],
                'category' => [
                    'id' => (int)$r['category_id'],
                    'name' => $r['cat_name'],
                    'icon' => $r['cat_icon'],
                    'color' => $r['cat_color'],
                    'type' => $r['cat_type']
                ],
                'due_day' => (int)$r['due_day'],
                'reminder_enabled' => (bool)$r['reminder_enabled'],
                'is_active' => (bool)$r['is_active']
            ];
        }
        jsonResponse(true, "Daftar tagihan berulang berhasil diambil", $recurrings);

    } elseif ($method === 'POST') {
        $body = getRequestBody();
        $title = $body['title'] ?? null;
        $type = $body['type'] ?? null;
        $amount = $body['amount'] ?? null;
        $categoryId = $body['category_id'] ?? null;
        $dueDay = $body['due_day'] ?? null;
        $reminderEnabled = isset($body['reminder_enabled']) ? (int)$body['reminder_enabled'] : 1;

        if (!$title || !$type || !$amount || !$categoryId || !$dueDay) {
            jsonResponse(false, "Judul, tipe, nominal, kategori, dan tanggal jatuh tempo wajib diisi", null, 422);
        }

        // Verify valid category
        $stmt = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE id = ? AND (user_id IS NULL OR user_id = ?)");
        $stmt->execute([$categoryId, $userId]);
        if ($stmt->fetchColumn() == 0) {
            jsonResponse(false, "Kategori tidak valid", null, 422);
        }

        $stmt = $pdo->prepare("INSERT INTO recurring_transactions (user_id, title, type, amount, category_id, due_day, reminder_enabled) VALUES (?, ?, ?, ?, ?, ?, ?)");
        $stmt->execute([$userId, $title, $type, $amount, $categoryId, $dueDay, $reminderEnabled]);
        $id = $pdo->lastInsertId();

        $stmt = $pdo->prepare("SELECT r.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM recurring_transactions r JOIN categories c ON r.category_id = c.id WHERE r.id = ? AND r.user_id = ?");
        $stmt->execute([$id, $userId]);
        $r = $stmt->fetch();

        $recurring = [
            'id' => (int)$r['id'],
            'title' => $r['title'],
            'type' => $r['type'],
            'amount' => (double)$r['amount'],
            'category_id' => (int)$r['category_id'],
            'category' => [
                'id' => (int)$r['category_id'],
                'name' => $r['cat_name'],
                'icon' => $r['cat_icon'],
                'color' => $r['cat_color'],
                'type' => $r['cat_type']
            ],
            'due_day' => (int)$r['due_day'],
            'reminder_enabled' => (bool)$r['reminder_enabled'],
            'is_active' => (bool)$r['is_active']
        ];
        jsonResponse(true, "Tagihan berulang berhasil didaftarkan", $recurring, 201);
    }
}
elseif (preg_match('#^api/recurring-transactions/(\d+)$#', $path, $matches)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];
    $id = (int)$matches[1];

    // Verify ownership
    $stmt = $pdo->prepare("SELECT * FROM recurring_transactions WHERE id = ?");
    $stmt->execute([$id]);
    $recurringRow = $stmt->fetch();
    if (!$recurringRow || (int)$recurringRow['user_id'] !== $userId) {
        jsonResponse(false, "Tagihan berulang tidak ditemukan", null, 404);
    }

    if ($method === 'PUT') {
        $body = getRequestBody();
        $title = $body['title'] ?? null;
        $type = $body['type'] ?? null;
        $amount = $body['amount'] ?? null;
        $categoryId = $body['category_id'] ?? null;
        $dueDay = $body['due_day'] ?? null;
        $reminderEnabled = isset($body['reminder_enabled']) ? (int)$body['reminder_enabled'] : null;
        $isActive = isset($body['is_active']) ? (int)$body['is_active'] : null;

        $sql = "UPDATE recurring_transactions SET ";
        $params = [];
        if ($title) { $sql .= "title = ?, "; $params[] = $title; }
        if ($type) { $sql .= "type = ?, "; $params[] = $type; }
        if ($amount) { $sql .= "amount = ?, "; $params[] = $amount; }
        if ($categoryId) {
            // Verify category
            $stmtCat = $pdo->prepare("SELECT COUNT(*) FROM categories WHERE id = ? AND (user_id IS NULL OR user_id = ?)");
            $stmtCat->execute([$categoryId, $userId]);
            if ($stmtCat->fetchColumn() == 0) {
                jsonResponse(false, "Kategori tidak valid", null, 422);
            }
            $sql .= "category_id = ?, ";
            $params[] = $categoryId;
        }
        if ($dueDay) { $sql .= "due_day = ?, "; $params[] = $dueDay; }
        if ($reminderEnabled !== null) { $sql .= "reminder_enabled = ?, "; $params[] = $reminderEnabled; }
        if ($isActive !== null) { $sql .= "is_active = ?, "; $params[] = $isActive; }

        $sql = rtrim($sql, ", ") . " WHERE id = ? AND user_id = ?";
        $params[] = $id;
        $params[] = $userId;

        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);

        $stmt = $pdo->prepare("SELECT r.*, c.name as cat_name, c.icon as cat_icon, c.color as cat_color, c.type as cat_type FROM recurring_transactions r JOIN categories c ON r.category_id = c.id WHERE r.id = ? AND r.user_id = ?");
        $stmt->execute([$id, $userId]);
        $r = $stmt->fetch();

        $recurring = [
            'id' => (int)$r['id'],
            'title' => $r['title'],
            'type' => $r['type'],
            'amount' => (double)$r['amount'],
            'category_id' => (int)$r['category_id'],
            'category' => [
                'id' => (int)$r['category_id'],
                'name' => $r['cat_name'],
                'icon' => $r['cat_icon'],
                'color' => $r['cat_color'],
                'type' => $r['cat_type']
            ],
            'due_day' => (int)$r['due_day'],
            'reminder_enabled' => (bool)$r['reminder_enabled'],
            'is_active' => (bool)$r['is_active']
        ];
        jsonResponse(true, "Tagihan berulang berhasil diperbarui", $recurring);

    } elseif ($method === 'DELETE') {
        $stmt = $pdo->prepare("DELETE FROM recurring_transactions WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $userId]);
        jsonResponse(true, "Tagihan berulang berhasil dihapus", null);
    }
}

// 6.5 REPORTS
elseif (preg_match('#^api/reports/monthly-trend$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $monthsLimit = (int)($_GET['months'] ?? 6);
        if ($monthsLimit <= 0) $monthsLimit = 6;

        $trendList = [];
        for ($i = $monthsLimit - 1; $i >= 0; $i--) {
            $d = new DateTime();
            $d->modify("-$i month");
            $m = $d->format('Y-m');

            // Sum Income for month for this user
            $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE type='income' AND user_id = ? AND transaction_date LIKE ?");
            $stmt->execute([$userId, $m . '%']);
            $inc = (double)$stmt->fetchColumn();

            // Sum Expense for month for this user
            $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE type='expense' AND user_id = ? AND transaction_date LIKE ?");
            $stmt->execute([$userId, $m . '%']);
            $exp = (double)$stmt->fetchColumn();

            $trendList[] = [
                'month' => $m,
                'total_income' => $inc,
                'total_expense' => $exp
            ];
        }
        jsonResponse(true, "Tren bulanan berhasil diambil", $trendList);
    }
}
elseif (preg_match('#^api/reports/category-breakdown$#', $path)) {
    $currentUser = getCurrentUser($pdo);
    $userId = $currentUser['id'];

    if ($method === 'GET') {
        $month = $_GET['month'] ?? null;
        $type = $_GET['type'] ?? 'expense';

        if (!$month) {
            jsonResponse(false, "Parameter bulan (month) wajib diisi", null, 422);
        }

        // Calculate total amount in that month to compute percentage distributions for this user
        $stmt = $pdo->prepare("SELECT SUM(amount) FROM transactions WHERE type = ? AND user_id = ? AND transaction_date LIKE ?");
        $stmt->execute([$type, $userId, $month . '%']);
        $totalAmount = (double)$stmt->fetchColumn();

        if ($totalAmount == 0) {
            jsonResponse(true, "Belum ada transaksi untuk bulan ini", []);
        }

        // Group by categories
        $stmt = $pdo->prepare("SELECT c.name as category, c.color, SUM(t.amount) as amount 
                               FROM transactions t 
                               JOIN categories c ON t.category_id = c.id 
                               WHERE t.type = ? AND t.user_id = ? AND t.transaction_date LIKE ? 
                               GROUP BY t.category_id");
        $stmt->execute([$type, $userId, $month . '%']);
        $results = $stmt->fetchAll();

        $breakdown = [];
        foreach ($results as $r) {
            $amt = (double)$r['amount'];
            $pct = ($amt / $totalAmount) * 100;
            $breakdown[] = [
                'category' => $r['category'],
                'color' => $r['color'],
                'amount' => $amt,
                'percentage' => round($pct, 2)
            ];
        }
        jsonResponse(true, "Distribusi kategori berhasil diambil", $breakdown);
    }
}

// 6.6 DEVICE TOKEN (FCM)
elseif (preg_match('#^api/device-token$#', $path)) {
    // Allows user authorization to map device token to account optionally
    $userId = null;
    $headers = getallheaders();
    $authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
    if (empty($authHeader) && isset($_SERVER['HTTP_AUTHORIZATION'])) {
        $authHeader = $_SERVER['HTTP_AUTHORIZATION'];
    }
    if (preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
        $token = trim($matches[1]);
        $stmt = $pdo->prepare("SELECT id FROM users WHERE token = ?");
        $stmt->execute([$token]);
        $userId = $stmt->fetchColumn() ?: null;
    }

    if ($method === 'POST') {
        $body = getRequestBody();
        $token = $body['token'] ?? null;

        if (!$token) {
            jsonResponse(false, "Token perangkat wajib diisi", null, 422);
        }

        // Save token to DB, update user_id if already exists
        $stmt = $pdo->prepare("INSERT INTO device_tokens (token, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE user_id = ?");
        $stmt->execute([$token, $userId, $userId]);

        jsonResponse(true, "Token perangkat berhasil disimpan", null);
    }
}

// 404 Fallback if path doesn't match any route
else {
    jsonResponse(false, "Endpoint tidak ditemukan", null, 404);
}

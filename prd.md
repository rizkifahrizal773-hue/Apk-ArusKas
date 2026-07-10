# Product Requirements Document (PRD)

**App Name:** ArusKas
**Platform:** Android (Native Java)
**IDE:** Android Studio
**Backend:** REST API (PHP/Laravel) & MySQL/MariaDB

> **Catatan perubahan arah produk:** Versi PRD ini menggantikan versi sebelumnya. Fitur catatan
> suara/teks (Speech-to-Text, Audio Record, Calendar Notes, Deadline Reminder) **DIBATALKAN
> SEPENUHNYA**. Aplikasi sekarang 100% fokus sebagai **aplikasi pencatatan keuangan pribadi**
> (income & expense tracker) dengan fitur lengkap dan pengalaman premium. Nama aplikasi diganti
> menjadi **"ArusKas"**. Logo yang sudah dibuat sebelumnya (mic + jam, era nama "STAR") sudah
> tidak relevan lagi dan sebaiknya diganti — beri tahu jika ingin dibuatkan logo baru untuk ArusKas.

---

## 1. Core Constraints (CRITICAL - DO NOT VIOLATE)

- **Language:** STRICTLY Java (Do NOT use Kotlin).
- **Local Database:** STRICTLY PROHIBITED. Do NOT use SQLite, Room, or any local DB for storing
  transactions/categories/budgets. All CRUD operations MUST go through the REST API.
  - **Exception (allowed use of SharedPreferences):** ONLY for storing non-data app state such as
    auth token (if login exists) and FCM device token. Never store financial data there.
- **UI Architecture:** Explicit Intents for navigation between Activities. Project has 7
  Activities — see §7.
- **Lists:** Multiple RecyclerViews for dynamic data fetched from the API (transactions,
  categories, budgets, recurring bills).
- **Input Validation:** Strict validation required on all forms before sending data to the API
  (e.g., amount must be > 0, category must be selected).
- **Networking Library:** STRICTLY Retrofit2 + OkHttp3. Do NOT mix with Volley,
  HttpURLConnection, or AsyncTask.
- **Charting Library:** STRICTLY MPAndroidChart for all charts (pie, bar, line). Do NOT introduce
  a second charting library.
- **No local caching layer.** Every screen open/refresh re-fetches from API (except in-memory
  state during a single Activity lifecycle).

---

## 2. App Concept & Features

ArusKas is a premium personal finance tracker that makes it fast and effortless to record income
and expenses, understand spending habits, stay within budget, and never miss a recurring bill.

**Key Features:**

1. **Dashboard/Overview:** At-a-glance current balance, this month's income/expense, a mini trend
   chart, and the most recent transactions — the first thing the user sees on open.
2. **Transaction Recording:** Quickly add income or expense transactions with amount, category,
   date, optional note, and an optional receipt photo.
3. **Custom Categories:** Users create and manage their own categories (name, icon, color, type),
   not a fixed hardcoded list — this is what makes tracking feel personal.
4. **Budgeting:** Set a monthly spending limit per category and track progress with a visual
   indicator; get warned as it approaches or exceeds the limit.
5. **Reports & Analytics:** Visual breakdown of spending by category (pie chart) and income vs.
   expense trend over the last several months (line/bar chart).
6. **Recurring Transactions & Bill Reminders:** Define recurring bills (e.g. "Listrik", monthly,
   due on the 5th); the app reminds the user via push notification before the due date and lets
   them log the actual transaction with one tap.
7. **Search & Filter:** Find past transactions quickly by month, category, type, or keyword.

---

## 3. Tech Stack & Dependencies (Fixed Versions)

To keep vibe-coding output consistent, always use these exact dependencies in `app/build.gradle`:

```gradle
dependencies {
    // Networking
    implementation 'com.squareup.retrofit2:retrofit:2.11.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.11.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // Image loading (receipt photos, category icons)
    implementation 'com.github.bumptech.glide:glide:4.16.0'

    // Firebase (Cloud Messaging for bill/budget reminders)
    implementation platform('com.google.firebase:firebase-bom:33.1.2')
    implementation 'com.google.firebase:firebase-messaging'

    // Charts
    implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

    // UI / Material
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'

    // Lifecycle (avoid memory leaks on async callback)
    implementation 'androidx.lifecycle:lifecycle-viewmodel:2.8.4'
}
```

**Min SDK:** 24 (Android 7.0).
**Target SDK:** 34.
**JDK:** 17 (Android Studio Koala+ default).

---

## 4. Project Package Structure

Always generate code following this exact package layout — do NOT invent alternate structures
mid-project:

```
com.aruskas.app
 ├── activity/
 │    ├── DashboardActivity.java
 │    ├── TransactionEditorActivity.java
 │    ├── TransactionListActivity.java
 │    ├── CategoryActivity.java
 │    ├── BudgetActivity.java
 │    ├── ReportActivity.java
 │    └── RecurringTransactionActivity.java
 ├── adapter/
 │    ├── TransactionAdapter.java
 │    ├── CategoryAdapter.java
 │    ├── BudgetAdapter.java
 │    └── RecurringTransactionAdapter.java
 ├── model/
 │    ├── Transaction.java
 │    ├── Category.java
 │    ├── Budget.java
 │    ├── RecurringTransaction.java
 │    ├── FinanceSummary.java
 │    ├── MonthlyTrend.java
 │    ├── CategoryBreakdown.java
 │    └── ApiResponse.java
 ├── network/
 │    ├── ApiClient.java        // Retrofit singleton
 │    ├── ApiService.java       // Retrofit interface (all endpoints)
 │    └── RetrofitCallback.java // optional generic callback wrapper
 ├── util/
 │    ├── DateTimeUtil.java     // date/time formatting, ISO8601 <-> display
 │    ├── ValidationUtil.java   // form validation rules
 │    ├── CurrencyUtil.java     // Rupiah formatting (NumberFormat, locale "id-ID")
 │    └── ImageUtil.java        // pick/compress receipt photo before upload
 ├── fcm/
 │    └── ArusKasFirebaseMessagingService.java
 └── ArusKasApplication.java       // Application class (init Firebase, etc.)
```

---

## 5. Data Models

### 5.1 `Category` Entity

User-defined categories (no longer a static list) — created via the app itself.

| Field | Type   | JSON key | Notes                                                    |
| ----- | ------ | -------- | -------------------------------------------------------- |
| id    | int    | `id`     | server-generated                                         |
| name  | String | `name`   | required, max 50 chars                                   |
| icon  | String | `icon`   | identifier from a fixed icon set (e.g. "food", "salary") |
| color | String | `color`  | hex color string (e.g. "#2ECC71")                        |
| type  | String | `type`   | enum: `income`, `expense` — required                     |

```java
public class Category {
    private int id;
    private String name;
    private String icon;
    private String color;
    private String type; // "income" or "expense"
    // getters & setters only
}
```

### 5.2 `Transaction` Entity

| Field           | Type     | JSON key           | Notes                                                         |
| --------------- | -------- | ------------------ | ------------------------------------------------------------- |
| id              | int      | `id`               | server-generated                                              |
| type            | String   | `type`             | enum: `income`, `expense` — required                          |
| amount          | double   | `amount`           | required, must be > 0                                         |
| categoryId      | int      | `category_id`      | required, FK to Category                                      |
| category        | Category | `category`         | nested object, returned by API on GET (read-only convenience) |
| note            | String   | `note`             | nullable, max 255 chars                                       |
| transactionDate | String   | `transaction_date` | format `YYYY-MM-DD`, required                                 |
| receiptUrl      | String   | `receipt_url`      | nullable, full URL to uploaded receipt photo                  |
| createdAt       | String   | `created_at`       | ISO 8601, server-generated                                    |
| updatedAt       | String   | `updated_at`       | ISO 8601, server-generated                                    |

```java
public class Transaction {
    private int id;
    private String type;
    private double amount;
    @SerializedName("category_id")
    private int categoryId;
    private Category category;
    private String note;
    @SerializedName("transaction_date")
    private String transactionDate;
    @SerializedName("receipt_url")
    private String receiptUrl;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    // getters & setters only
}
```

### 5.3 `Budget` Entity

| Field       | Type     | JSON key       | Notes                                             |
| ----------- | -------- | -------------- | ------------------------------------------------- |
| id          | int      | `id`           | server-generated                                  |
| categoryId  | int      | `category_id`  | required, FK to Category (expense type only)      |
| category    | Category | `category`     | nested object, returned by API on GET             |
| month       | String   | `month`        | format `YYYY-MM`, required                        |
| limitAmount | double   | `limit_amount` | required, must be > 0                             |
| spentAmount | double   | `spent_amount` | read-only, computed server-side from transactions |

```java
public class Budget {
    private int id;
    @SerializedName("category_id")
    private int categoryId;
    private Category category;
    private String month;
    @SerializedName("limit_amount")
    private double limitAmount;
    @SerializedName("spent_amount")
    private double spentAmount;
    // getters & setters only
}
```

### 5.4 `RecurringTransaction` Entity (Bill Reminders)

| Field           | Type     | JSON key           | Notes                                                |
| --------------- | -------- | ------------------ | ---------------------------------------------------- |
| id              | int      | `id`               | server-generated                                     |
| title           | String   | `title`            | required, max 100 chars (e.g. "Listrik", "Internet") |
| type            | String   | `type`             | enum: `income`, `expense` — required                 |
| amount          | double   | `amount`           | required, must be > 0                                |
| categoryId      | int      | `category_id`      | required, FK to Category                             |
| category        | Category | `category`         | nested object, returned by API on GET                |
| dueDay          | int      | `due_day`          | 1-31, day of month it's due                          |
| reminderEnabled | boolean  | `reminder_enabled` | default true                                         |
| isActive        | boolean  | `is_active`        | user can pause a recurring bill without deleting it  |

```java
public class RecurringTransaction {
    private int id;
    private String title;
    private String type;
    private double amount;
    @SerializedName("category_id")
    private int categoryId;
    private Category category;
    @SerializedName("due_day")
    private int dueDay;
    @SerializedName("reminder_enabled")
    private boolean reminderEnabled;
    @SerializedName("is_active")
    private boolean isActive;
    // getters & setters only
}
```

### 5.5 Report / Response-only Models

```java
public class FinanceSummary {
    @SerializedName("total_income")
    private double totalIncome;
    @SerializedName("total_expense")
    private double totalExpense;
    private double balance;
    @SerializedName("previous_month_balance")
    private double previousMonthBalance; // for trend indicator on Dashboard
    // getters & setters only
}

public class MonthlyTrend {
    private String month; // "YYYY-MM"
    @SerializedName("total_income")
    private double totalIncome;
    @SerializedName("total_expense")
    private double totalExpense;
    // getters & setters only
}

public class CategoryBreakdown {
    private String category;
    private String color;
    private double amount;
    private double percentage;
    // getters & setters only
}
```

**Fixed icon set** (for Category `icon` field — static array in `res/values/arrays.xml`, the
value stored is just the identifier string, mapped client-side to a drawable):
`food, transport, shopping, bills, entertainment, health, salary, bonus, gift, other`

---

## 6. REST API Contract (Request & Response Shapes)

All endpoints return a **consistent envelope**:

```json
{
  "success": true,
  "message": "Transactions fetched successfully",
  "data": [
    /* object or array */
  ]
}
```

On error:

```json
{
  "success": false,
  "message": "Amount must be greater than 0",
  "data": null
}
```

Java side: always parse into a generic `ApiResponse<T>` wrapper class, never parse raw
arrays/objects directly.

### 6.1 Categories

| Method | Endpoint                | Body / Params                          | Response `data`      |
| ------ | ----------------------- | -------------------------------------- | -------------------- |
| GET    | `/api/categories?type=` | optional query `type` (income/expense) | `List<Category>`     |
| POST   | `/api/categories`       | JSON: `name`, `icon`, `color`, `type`  | `Category` (created) |
| PUT    | `/api/categories/{id}`  | JSON (same fields as POST)             | `Category` (updated) |
| DELETE | `/api/categories/{id}`  | path param                             | `null`               |

### 6.2 Transactions

| Method | Endpoint                                                      | Body / Params                                                                                                | Response `data`         |
| ------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ----------------------- |
| GET    | `/api/transactions?month=YYYY-MM&category_id=&type=&keyword=` | all query params optional except one date scope                                                              | `List<Transaction>`     |
| GET    | `/api/transactions/summary?month=YYYY-MM`                     | query param `month`                                                                                          | `FinanceSummary`        |
| POST   | `/api/transactions`                                           | multipart/form-data: `type`, `amount`, `category_id`, `note`, `transaction_date`, `receipt` (file, optional) | `Transaction` (created) |
| GET    | `/api/transactions/{id}`                                      | path param                                                                                                   | `Transaction`           |
| PUT    | `/api/transactions/{id}`                                      | multipart/form-data (same fields as POST), or JSON if no new receipt file                                    | `Transaction` (updated) |
| DELETE | `/api/transactions/{id}`                                      | path param                                                                                                   | `null`                  |

### 6.3 Budgets

| Method | Endpoint                     | Body / Params                                | Response `data`    |
| ------ | ---------------------------- | -------------------------------------------- | ------------------ |
| GET    | `/api/budgets?month=YYYY-MM` | query param `month`                          | `List<Budget>`     |
| POST   | `/api/budgets`               | JSON: `category_id`, `month`, `limit_amount` | `Budget` (created) |
| PUT    | `/api/budgets/{id}`          | JSON: `limit_amount`                         | `Budget` (updated) |
| DELETE | `/api/budgets/{id}`          | path param                                   | `null`             |

### 6.4 Recurring Transactions (Bill Reminders)

| Method | Endpoint                           | Body / Params                                                                 | Response `data`                  |
| ------ | ---------------------------------- | ----------------------------------------------------------------------------- | -------------------------------- |
| GET    | `/api/recurring-transactions`      | none                                                                          | `List<RecurringTransaction>`     |
| POST   | `/api/recurring-transactions`      | JSON: `title`, `type`, `amount`, `category_id`, `due_day`, `reminder_enabled` | `RecurringTransaction` (created) |
| PUT    | `/api/recurring-transactions/{id}` | JSON (same fields as POST, plus `is_active`)                                  | `RecurringTransaction` (updated) |
| DELETE | `/api/recurring-transactions/{id}` | path param                                                                    | `null`                           |

### 6.5 Reports

| Method | Endpoint                                                     | Body / Params                    | Response `data`           |
| ------ | ------------------------------------------------------------ | -------------------------------- | ------------------------- |
| GET    | `/api/reports/monthly-trend?months=6`                        | query param `months` (default 6) | `List<MonthlyTrend>`      |
| GET    | `/api/reports/category-breakdown?month=YYYY-MM&type=expense` | query params `month`, `type`     | `List<CategoryBreakdown>` |

### 6.6 Device Token (FCM)

| Method | Endpoint            | Body / Params | Response `data` |
| ------ | ------------------- | ------------- | --------------- |
| POST   | `/api/device-token` | JSON: `token` | `null`          |

**Base URL:** stored as a constant `BuildConfig.BASE_URL`, configured per build type (dev/
staging/prod) in `build.gradle`. Never hardcode base URL inside Activities.

**Timeout policy:** OkHttp client timeouts fixed at 30s connect / 30s read / 30s write (receipt
photo upload needs a generous timeout).

**HTTP status handling:**

- `200/201` → success envelope parsed normally.
- `422` → validation error, show `message` field in a Toast/Snackbar.
- `404` → "Data not found", navigate back.
- `500` / no internet → generic error Snackbar with "Retry" action.

---

## 7. Activity Structure & Detailed Behavior

### 7.1 DashboardActivity (Main Screen)

- Top: balance overview card — current balance (large, prominent), this month's total income
  and expense side by side, plus a small trend indicator (up/down vs. previous month using
  `FinanceSummary.previousMonthBalance`).
- Mini trend chart (MPAndroidChart line/bar, last 6 months) using `GET /api/reports/monthly-trend`.
- "Recent Transactions" section: top 5 most recent, each tappable → `TransactionEditorActivity`
  (edit mode). "See all" link → `TransactionListActivity`.
- Shortcut cards/buttons to: `BudgetActivity`, `ReportActivity`, `RecurringTransactionActivity`,
  `CategoryActivity`.
- FAB → explicit Intent to `TransactionEditorActivity` (no extras = create mode).
- Logic: on create/resume, trigger `GET /api/transactions/summary?month={current}`,
  `GET /api/reports/monthly-trend?months=6`, and `GET /api/transactions?month={current}` (limited
  client-side to first 5 for the "recent" list).

### 7.2 TransactionEditorActivity (Create/Update/Delete Form)

- Mode determined by intent extra `transaction_id` (if present = edit, fetch via
  `GET /api/transactions/{id}` and prefill; if absent = create). Can also receive optional extras
  `prefill_title`, `prefill_amount`, `prefill_category_id` when opened from a recurring-bill
  reminder notification (see §10).
- Fields:
  - Type: segmented toggle "Pemasukan" / "Pengeluaran" — no default, must be chosen explicitly.
  - Amount: `EditText` (`inputType="numberDecimal"`), formatted via `CurrencyUtil`.
  - Category: selector showing categories filtered by the chosen type (fetched via
    `GET /api/categories?type=`), with a "+ Kategori baru" shortcut → `CategoryActivity`.
  - Note: optional `EditText`, multiline.
  - Date: button triggering `DatePickerDialog`, defaults to today.
  - Receipt photo: optional button to pick from camera or gallery, preview thumbnail shown,
    uploaded as part of the multipart request.
- Save button → run `ValidationUtil` checks (§8.1) → build `MultipartBody` (always, since receipt
  is optional but the same request shape is reused) → POST (create) or PUT (update).
- If editing an existing transaction, show a **Delete** button → confirmation `AlertDialog` →
  `DELETE /api/transactions/{id}`.
- On success (save or delete) → `finish()` with `RESULT_OK` so the caller (Dashboard or
  TransactionList) refreshes.

### 7.3 TransactionListActivity (Full History)

- `RecyclerView` + `SwipeRefreshLayout` of all transactions, with a filter bar: month selector,
  category filter, type filter (income/expense/all), and a search field (keyword on `note`).
- Empty state: "Belum ada transaksi yang cocok" when filtered list is empty.
- Tapping an item → `TransactionEditorActivity` (edit mode).
- Logic: any filter change triggers `GET /api/transactions` with the corresponding query params
  from §6.2.

### 7.4 CategoryActivity (Manage Categories)

- Two tabs or a segmented toggle: "Pengeluaran" / "Pemasukan" categories.
- `RecyclerView` listing categories (icon, color swatch, name) for the selected type.
- FAB or "+ Kategori" button → opens a `BottomSheetDialogFragment` with a small form (name, icon
  picker from the fixed set in §5, color picker from a fixed swatch palette) — a full Activity is
  not required for this since the form is short.
- Tapping a category → same bottom sheet, prefilled, with a Delete option (confirm before
  deleting; if the category is in use by existing transactions, surface the API's error message
  rather than assuming it's safe to delete).
- Logic: `GET /api/categories?type=`, `POST`/`PUT`/`DELETE /api/categories`.

### 7.5 BudgetActivity (Set & Track Budgets)

- Month selector (prev/next).
- `RecyclerView` of expense categories for the selected month, each row showing: category name +
  icon, a progress bar (`spent_amount` / `limit_amount`), and the remaining amount. Progress bar
  color shifts from brand color → amber (≥80%) → red (≥100%, over budget).
- Categories without a budget set yet show a "Set budget" action instead of a progress bar.
- Tapping a row (or "Set budget") → small dialog/bottom sheet to input `limit_amount` →
  `POST` (new) or `PUT` (existing) `/api/budgets`.
- Logic: `GET /api/budgets?month={selected}` joined client-side with
  `GET /api/categories?type=expense` to also show categories with no budget yet.

### 7.6 ReportActivity (Analytics)

- Month selector.
- Pie chart (MPAndroidChart `PieChart`) of expense breakdown by category for the selected month,
  via `GET /api/reports/category-breakdown?month={selected}&type=expense`. Toggle to switch the
  same chart to income breakdown (`type=income`).
- Line or bar chart (MPAndroidChart) of the last 6 months' income vs. expense trend, via
  `GET /api/reports/monthly-trend?months=6`.
- Legend list below the pie chart showing each category with its amount and percentage (matches
  chart colors from `CategoryBreakdown.color`).

### 7.7 RecurringTransactionActivity (Bill Reminders)

- `RecyclerView` listing all recurring bills (title, amount, category, due day, active/paused
  toggle switch).
- FAB → `BottomSheetDialogFragment` form: title, type, amount, category, due day (1-31),
  reminder enabled toggle → `POST` (create) or `PUT` (update, if editing an existing item).
- Toggling the active switch on a row → `PUT /api/recurring-transactions/{id}` with updated
  `is_active`, no need to open the form for this.
- Swipe-to-delete or a delete option in the form → `DELETE /api/recurring-transactions/{id}`.

---

## 8. Input Validation Rules (ValidationUtil)

### 8.1 Transactions (`TransactionEditorActivity`)

1. `type` — required, must be explicitly selected (income or expense).
2. `amount` — required, must parse to a number **> 0**.
3. `category_id` — required, must be selected from the list (filtered by the chosen type).
4. `transaction_date` — required (defaults to today).
5. `note` — optional, max 255 characters if filled.
6. Receipt photo — optional, but if attached, max file size 5 MB (check before upload).

### 8.2 Categories (`CategoryActivity`)

1. `name` — required, not blank, max 50 characters, must be unique per type (surface the API's
   error message if the backend rejects a duplicate).
2. `icon` — required, must be one of the fixed set in §5.
3. `color` — required, must be a valid hex value from the fixed swatch palette.
4. `type` — required, determined by which tab/segment the user was in when creating it.

### 8.3 Budgets (`BudgetActivity`)

1. `limit_amount` — required, must parse to a number **> 0**.
2. `category_id` and `month` together must be unique — if a budget already exists for that
   category/month, this becomes an update (`PUT`), not a duplicate create.

### 8.4 Recurring Transactions (`RecurringTransactionActivity`)

1. `title` — required, not blank, max 100 characters.
2. `type` — required, must be explicitly selected.
3. `amount` — required, must parse to a number **> 0**.
4. `category_id` — required.
5. `due_day` — required, integer between 1 and 31.

All forms show field-level errors using `TextInputLayout.setError(...)` (or inline text for
toggles/selectors), never a bare Toast as the only feedback.

---

## 9. Permissions Required (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->
<uses-permission android:name="android.permission.CAMERA" /> <!-- optional receipt photo -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" /> <!-- Android 13+, gallery pick -->
```

Runtime permission requests: `CAMERA` only when the user taps "Ambil foto" (not on app launch);
`READ_MEDIA_IMAGES` (or legacy `READ_EXTERNAL_STORAGE` below API 33) only when the user taps
"Pilih dari galeri"; `POST_NOTIFICATIONS` on app first launch. Use
`ActivityResultContracts.RequestPermission()`, not deprecated callbacks.

---

## 10. Push Notifications (FCM Flow)

1. On app first launch, get FCM token and send it to backend via `POST /api/device-token`.
2. Backend Cron Job runs daily and checks:
   - **Bill reminders:** any active `RecurringTransaction` with `reminder_enabled=true` whose
     `due_day` is approaching (e.g. 1 day before) → push a notification like "Tagihan Listrik
     (Rp 250.000) jatuh tempo besok."
   - **Budget alerts:** any `Budget` whose `spent_amount` crosses 80% or 100% of `limit_amount`
     for the current month → push a notification like "Pengeluaran kategori Makanan sudah 85%
     dari budget bulan ini."
3. `ArusKasFirebaseMessagingService.onMessageReceived()`:
   - For bill reminders → tapping the notification opens `TransactionEditorActivity` in create
     mode with `prefill_title`, `prefill_amount`, `prefill_category_id` extras from the FCM data
     payload, so the user just needs to confirm the date and save.
   - For budget alerts → tapping opens `BudgetActivity`.
4. Notification channels: `"bill_reminders"` and `"budget_alerts"`, both importance `HIGH`.

---

## 11. UI/Design Guidelines

See the separate **UI/UX Premium Prompt** document for the full visual design system (colors,
typography, spacing, motion). Summary of what must stay consistent:

- Material Design 3 components.
- Income amounts: `colorIncome` (green). Expense amounts: `colorExpense` (red). Budget warning:
  `colorWarning` (amber). Budget exceeded: reuse `colorExpense` (red).
- `TextInputLayout` + `TextInputEditText` for all form fields.
- All strings in `res/values/strings.xml`.
- All currency values formatted via `CurrencyUtil` (locale `"id","ID"`, symbol "Rp").
- `ViewBinding` instead of `findViewById` everywhere.

---

## 12. Non-Functional Requirements

- **No offline mode.** No internet → blocking error state with "Retry", never silently fail or
  queue actions locally.
- All network calls run off the main thread via Retrofit's async `enqueue()`.
- Guard callbacks with `isFinishing()`/`isDestroyed()` checks to avoid crashes on
  rotated/destroyed Activities.
- Log network requests/responses only in debug builds.
- Chart data (trend, breakdown) is always fetched fresh from `/api/reports/*` — never computed
  client-side from a locally-accumulated transaction list, to avoid drift from server totals.
- Receipt photos are compressed client-side (`ImageUtil`, target max ~1MB) before upload to keep
  requests fast on mobile networks.

---

## 13. Out of Scope (for this version)

- User authentication/multi-user accounts (assume single-user or token pre-provisioned).
- Multi-currency support (Rupiah only).
- OCR/auto-parsing of receipt photos (photo is stored as an attachment only, not read).
- Automatic bank/e-wallet account linking or transaction import.
- Exporting reports to PDF/Excel (may be considered in a future version).
- Multi-device sync conflict resolution beyond "server is always the source of truth."

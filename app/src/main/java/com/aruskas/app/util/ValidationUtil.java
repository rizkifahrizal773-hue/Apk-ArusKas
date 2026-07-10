package com.aruskas.app.util;

import java.util.Arrays;
import java.util.List;

public class ValidationUtil {

    // Fixed icons according to PRD.md Section 5.5
    private static final List<String> FIXED_ICONS = Arrays.asList(
            "food", "transport", "shopping", "bills", "entertainment", "health", "salary", "bonus", "gift", "other"
    );

    // 8.1 Transactions Validation
    public static String validateTransactionType(String type) {
        if (type == null || (!"income".equals(type) && !"expense".equals(type))) {
            return "Tipe transaksi wajib dipilih (Pemasukan atau Pengeluaran)";
        }
        return null;
    }

    public static String validateTransactionAmount(double amount) {
        if (amount <= 0) {
            return "Nominal harus lebih besar dari 0";
        }
        return null;
    }

    public static String validateTransactionCategory(int categoryId) {
        if (categoryId <= 0) {
            return "Kategori harus dipilih";
        }
        return null;
    }

    public static String validateTransactionDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return "Tanggal transaksi wajib diisi";
        }
        return null;
    }

    public static String validateTransactionNote(String note) {
        if (note != null && note.length() > 255) {
            return "Catatan maksimal 255 karakter";
        }
        return null;
    }

    public static String validateReceiptSize(long sizeInBytes) {
        // PRD.md 8.1.6: max file size 5 MB
        long maxSizeBytes = 5 * 1024 * 1024;
        if (sizeInBytes > maxSizeBytes) {
            return "Ukuran file bukti transaksi maksimal adalah 5 MB";
        }
        return null;
    }

    // 8.2 Categories Validation
    public static String validateCategoryName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Nama kategori tidak boleh kosong";
        }
        if (name.length() > 50) {
            return "Nama kategori maksimal 50 karakter";
        }
        return null;
    }

    public static String validateCategoryIcon(String icon) {
        if (icon == null || icon.trim().isEmpty()) {
            return "Ikon kategori harus dipilih";
        }
        if (!FIXED_ICONS.contains(icon)) {
            return "Ikon tidak valid";
        }
        return null;
    }

    public static String validateCategoryColor(String color) {
        if (color == null || color.trim().isEmpty()) {
            return "Warna kategori harus dipilih";
        }
        if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
            return "Warna harus berupa kode HEX valid (contoh: #2ECC71)";
        }
        return null;
    }

    public static String validateCategoryType(String type) {
        if (type == null || (!"income".equals(type) && !"expense".equals(type))) {
            return "Tipe kategori wajib dipilih";
        }
        return null;
    }

    // 8.3 Budgets Validation
    public static String validateBudgetLimitAmount(double limitAmount) {
        if (limitAmount <= 0) {
            return "Batas anggaran nominal harus lebih besar dari 0";
        }
        return null;
    }

    public static String validateBudgetCategory(int categoryId) {
        if (categoryId <= 0) {
            return "Kategori anggaran harus dipilih";
        }
        return null;
    }

    public static String validateBudgetMonth(String month) {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            return "Format bulan anggaran tidak valid (harus YYYY-MM)";
        }
        return null;
    }

    // 8.4 Recurring Transactions Validation
    public static String validateRecurringTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Judul pengingat tidak boleh kosong";
        }
        if (title.length() > 100) {
            return "Judul pengingat maksimal 100 karakter";
        }
        return null;
    }

    public static String validateRecurringType(String type) {
        if (type == null || (!"income".equals(type) && !"expense".equals(type))) {
            return "Tipe pengingat wajib dipilih";
        }
        return null;
    }

    public static String validateRecurringAmount(double amount) {
        if (amount <= 0) {
            return "Nominal pengingat harus lebih besar dari 0";
        }
        return null;
    }

    public static String validateRecurringCategory(int categoryId) {
        if (categoryId <= 0) {
            return "Kategori pengingat harus dipilih";
        }
        return null;
    }

    public static String validateRecurringDueDay(int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            return "Hari jatuh tempo harus di antara tanggal 1 dan 31";
        }
        return null;
    }
}

package com.aruskas.app.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class CurrencyUtil {
    
    private static final Locale LOCALE_ID = new Locale("id", "ID");

    /**
     * Formats a double amount into a neat Rupiah currency string (e.g. "Rp 150.000").
     */
    public static String formatRupiah(double amount) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(LOCALE_ID);
        currencyFormat.setMaximumFractionDigits(0);
        
        // standard format might return "Rp150.000" (without space) or "Rp 150.000" depending on SDK level.
        // We will make sure there is a space: "Rp " for consistent look.
        String formatted = currencyFormat.format(amount);
        if (formatted.startsWith("Rp")) {
            if (!formatted.startsWith("Rp ")) {
                formatted = formatted.replace("Rp", "Rp ");
            }
        }
        return formatted;
    }

    /**
     * Formats a double amount into a simple dot-separated number string for input fields (e.g. "150.000").
     */
    public static String formatNumberOnly(double amount) {
        DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(LOCALE_ID);
        DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setMonetaryDecimalSeparator(',');
        formatter.setDecimalFormatSymbols(symbols);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount);
    }
    
    /**
     * Parses a formatted string back to a double value, removing symbols and formatting characters.
     */
    public static double parseRupiah(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        // Remove currency prefix, space, dots, and trailing decimals if any
        String cleanString = value.replaceAll("[Rp\\s.]", "")
                                 .replaceAll(",\\d+", "");
        try {
            return Double.parseDouble(cleanString);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

package com.aruskas.app.notification;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Utility class for checking budget limits and sending real-time alerts
 * when spending approaches (≥80%) or exceeds (≥100%) the budget limit.
 * Called after a transaction is created or updated.
 */
public class BudgetAlertHelper {

    private static final String TAG = "BudgetAlertHelper";
    private static final double WARNING_THRESHOLD = 0.80; // 80%
    private static final double EXCEEDED_THRESHOLD = 1.00; // 100%

    /**
     * Checks if the spent amount has reached a threshold relative to the budget limit,
     * and fires a local notification accordingly.
     *
     * @param context      Application context
     * @param budgetId     The budget record ID
     * @param categoryName The display name of the budget category
     * @param spentAmount  Total amount spent in this category for the month
     * @param limitAmount  The budget limit set by the user
     */
    public static void checkAndNotify(Context context, int budgetId, String categoryName,
                                       double spentAmount, double limitAmount) {
        if (limitAmount <= 0) return;

        double ratio = spentAmount / limitAmount;

        if (ratio >= EXCEEDED_THRESHOLD) {
            Log.d(TAG, "Budget EXCEEDED for category: " + categoryName);
            fireBudgetAlert(context, budgetId, categoryName, spentAmount, limitAmount, "exceeded");
        } else if (ratio >= WARNING_THRESHOLD) {
            Log.d(TAG, "Budget WARNING for category: " + categoryName);
            fireBudgetAlert(context, budgetId, categoryName, spentAmount, limitAmount, "warning");
        }
    }

    private static void fireBudgetAlert(Context context, int budgetId, String categoryName,
                                         double spentAmount, double limitAmount, String alertType) {
        Intent intent = new Intent(context, BudgetAlertReceiver.class);
        intent.putExtra(BudgetAlertReceiver.EXTRA_BUDGET_ID, budgetId);
        intent.putExtra(BudgetAlertReceiver.EXTRA_CATEGORY_NAME, categoryName);
        intent.putExtra(BudgetAlertReceiver.EXTRA_SPENT_AMOUNT, spentAmount);
        intent.putExtra(BudgetAlertReceiver.EXTRA_LIMIT_AMOUNT, limitAmount);
        intent.putExtra(BudgetAlertReceiver.EXTRA_ALERT_TYPE, alertType);

        // Fire the BroadcastReceiver immediately (real-time alert)
        context.sendBroadcast(intent);
    }
}

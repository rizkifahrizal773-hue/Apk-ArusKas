package com.aruskas.app.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.aruskas.app.ArusKasApplication;
import com.aruskas.app.activity.BudgetActivity;

/**
 * BroadcastReceiver that fires local notifications for budget overspending alerts.
 * Triggered after a transaction is created/updated when spending exceeds/approaches the limit.
 */
public class BudgetAlertReceiver extends BroadcastReceiver {

    private static final String TAG = "BudgetAlertReceiver";

    public static final String EXTRA_CATEGORY_NAME = "category_name";
    public static final String EXTRA_SPENT_AMOUNT = "spent_amount";
    public static final String EXTRA_LIMIT_AMOUNT = "limit_amount";
    public static final String EXTRA_BUDGET_ID = "budget_id";
    public static final String EXTRA_ALERT_TYPE = "alert_type"; // "warning" or "exceeded"

    @Override
    public void onReceive(Context context, Intent intent) {
        String categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME);
        double spentAmount = intent.getDoubleExtra(EXTRA_SPENT_AMOUNT, 0);
        double limitAmount = intent.getDoubleExtra(EXTRA_LIMIT_AMOUNT, 0);
        int budgetId = intent.getIntExtra(EXTRA_BUDGET_ID, 0);
        String alertType = intent.getStringExtra(EXTRA_ALERT_TYPE);

        Log.d(TAG, "Budget alert triggered for category: " + categoryName);

        String formattedSpent = formatCurrency(spentAmount);
        String formattedLimit = formatCurrency(limitAmount);

        String title;
        String bodyText;

        if ("exceeded".equals(alertType)) {
            title = "⚠️ Anggaran Terlampaui!";
            bodyText = "Pengeluaran kategori \"" + categoryName + "\" telah melampaui batas anggaran! "
                    + formattedSpent + " dari " + formattedLimit + ".";
        } else {
            title = "\uD83D\uDCA1 Peringatan Anggaran";
            double pct = (spentAmount / limitAmount) * 100;
            bodyText = "Pengeluaran kategori \"" + categoryName + "\" telah mencapai "
                    + String.format("%.0f", pct) + "% dari batas anggaran "
                    + formattedLimit + ".";
        }

        Intent tapIntent = new Intent(context, BudgetActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                20000 + budgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ArusKasApplication.CHANNEL_BUDGET_ALERTS)
                .setSmallIcon(com.aruskas.app.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(bodyText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bodyText))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(20000 + budgetId, builder.build());
        }
    }

    private String formatCurrency(double amount) {
        return "Rp " + String.format("%,.0f", amount).replace(',', '.');
    }
}

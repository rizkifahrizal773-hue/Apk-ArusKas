package com.aruskas.app.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.core.app.NotificationCompat;

import com.aruskas.app.ArusKasApplication;
import com.aruskas.app.activity.DashboardActivity;
import com.aruskas.app.util.CurrencyUtil;

/**
 * Central helper for creating and showing local push notifications.
 * Provides convenience methods for welcome, confirmation, and test notifications.
 */
public class NotificationHelper {

    private static final String PREF_NAME = "aruskas_notification_pref";
    private static final String KEY_WELCOME_SHOWN = "welcome_notification_shown";

    /**
     * Shows a one-time welcome notification after the user's first login.
     * Returns true if the notification was shown, false if it was already shown before.
     */
    public static boolean showWelcomeNotificationOnce(Context context, String userName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_WELCOME_SHOWN, false)) {
            return false; // Already shown
        }

        String title = "👋 Selamat Datang, " + userName + "!";
        String body = "ArusKas siap membantu mengelola keuangan Anda. "
                + "Catat transaksi, atur anggaran, dan aktifkan pengingat tagihan untuk kontrol finansial yang lebih baik.";

        showNotification(context, 1, ArusKasApplication.CHANNEL_BILL_REMINDERS,
                title, body, DashboardActivity.class);

        prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply();
        return true;
    }

    /**
     * Shows a confirmation notification when a new bill reminder is successfully scheduled.
     */
    public static void showBillScheduledConfirmation(Context context, String billTitle, int dueDay) {
        int reminderDay = dueDay - 1;
        if (reminderDay < 1) reminderDay = 1;

        String title = "✅ Pengingat Tagihan Aktif";
        String body = "Tagihan \"" + billTitle + "\" akan diingatkan setiap tanggal "
                + reminderDay + " (1 hari sebelum jatuh tempo tanggal " + dueDay + ").";

        showNotification(context, (int) System.currentTimeMillis(),
                ArusKasApplication.CHANNEL_BILL_REMINDERS, title, body, null);
    }

    /**
     * Shows a push notification detailing a newly created/updated transaction
     * and the updated total balance.
     */
    public static void showTransactionNotification(Context context, String transactionType, 
                                                   double amount, double currentBalance) {
        String typeLabel = "expense".equals(transactionType) ? "Pengeluaran" : "Pemasukan";
        String emoji = "expense".equals(transactionType) ? "📉" : "📈";
        
        String title = emoji + " " + typeLabel + " Tercatat";
        String body = typeLabel + " sebesar " + CurrencyUtil.formatRupiah(amount) 
                + " berhasil disimpan. Saldo saat ini: " + CurrencyUtil.formatRupiah(currentBalance) + ".";

        showNotification(context, (int) System.currentTimeMillis(), 
                ArusKasApplication.CHANNEL_BILL_REMINDERS, title, body, DashboardActivity.class);
    }

    /**
     * Shows a generic notification with the given title and body.
     */
    public static void showNotification(Context context, int notificationId, String channelId,
                                         String title, String body, Class<?> targetActivity) {
        Intent intent;
        if (targetActivity != null) {
            intent = new Intent(context, targetActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            intent = new Intent(context, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.aruskas.app.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    /**
     * Resets the welcome notification flag (useful for debugging/testing).
     */
    public static void resetWelcomeFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_WELCOME_SHOWN, false).apply();
    }
}

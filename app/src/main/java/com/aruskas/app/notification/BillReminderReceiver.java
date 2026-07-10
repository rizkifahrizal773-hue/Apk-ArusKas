package com.aruskas.app.notification;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.aruskas.app.ArusKasApplication;
import com.aruskas.app.R;
import com.aruskas.app.activity.RecurringTransactionActivity;
import com.aruskas.app.activity.TransactionEditorActivity;

/**
 * BroadcastReceiver that fires local notifications for recurring bill reminders.
 * Scheduled via AlarmManager from BillReminderScheduler.
 */
public class BillReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "BillReminderReceiver";

    public static final String EXTRA_BILL_ID = "bill_id";
    public static final String EXTRA_BILL_TITLE = "bill_title";
    public static final String EXTRA_BILL_AMOUNT = "bill_amount";
    public static final String EXTRA_BILL_DUE_DAY = "bill_due_day";
    public static final String EXTRA_BILL_CATEGORY_ID = "bill_category_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra(EXTRA_BILL_TITLE);
        double amount = intent.getDoubleExtra(EXTRA_BILL_AMOUNT, 0);
        int dueDay = intent.getIntExtra(EXTRA_BILL_DUE_DAY, 1);
        int billId = intent.getIntExtra(EXTRA_BILL_ID, 0);
        int categoryId = intent.getIntExtra(EXTRA_BILL_CATEGORY_ID, -1);

        Log.d(TAG, "Bill reminder triggered for: " + title);

        String formattedAmount = formatCurrency(amount);
        String bodyText = "Tagihan \"" + title + "\" sebesar " + formattedAmount
                + " jatuh tempo tanggal " + dueDay + " bulan ini.";

        // Intent to open TransactionEditorActivity with prefilled form details
        Intent tapIntent = new Intent(context, TransactionEditorActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        tapIntent.putExtra("prefill_title", title);
        tapIntent.putExtra("prefill_amount", amount);
        tapIntent.putExtra("prefill_category_id", categoryId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                billId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ArusKasApplication.CHANNEL_BILL_REMINDERS)
                .setSmallIcon(com.aruskas.app.R.drawable.ic_notification)
                .setContentTitle("📋 Pengingat Tagihan")
                .setContentText(bodyText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bodyText))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(10000 + billId, builder.build());
        }
    }

    private String formatCurrency(double amount) {
        return "Rp " + String.format("%,.0f", amount).replace(',', '.');
    }
}

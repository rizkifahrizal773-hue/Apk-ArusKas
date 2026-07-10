package com.aruskas.app.fcm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.aruskas.app.ArusKasApplication;
import com.aruskas.app.R;
import com.aruskas.app.activity.BudgetActivity;
import com.aruskas.app.activity.DashboardActivity;
import com.aruskas.app.activity.TransactionEditorActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class ArusKasFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "ArusKasFCM";
    private static final String PREFS_NAME = "aruskas_prefs";
    private static final String KEY_FCM_TOKEN = "fcm_token";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed token: " + token);
        
        // Save token to SharedPreferences as permitted by PRD.md Section 1
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_FCM_TOKEN, token).apply();
        
        // In real app, we also try to send this token directly to server if user is logged in
        // or trigger it from the next app launch/session activity.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains data payload
        Map<String, String> data = remoteMessage.getData();
        
        String title = null;
        String body = null;
        
        // If message has notification payload, use it as fallback
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // Custom notification handling based on payload type
        if (!data.isEmpty()) {
            String type = data.get("type"); // e.g. "bill_reminder" or "budget_alert"
            String payloadTitle = data.get("title");
            String payloadBody = data.get("body");
            
            if (payloadTitle != null) title = payloadTitle;
            if (payloadBody != null) body = payloadBody;

            if ("bill_reminder".equals(type)) {
                sendBillReminderNotification(title, body, data);
            } else if ("budget_alert".equals(type)) {
                sendBudgetAlertNotification(title, body);
            } else {
                sendGenericNotification(title, body);
            }
        } else {
            sendGenericNotification(title, body);
        }
    }

    private void sendBillReminderNotification(String title, String body, Map<String, String> data) {
        Intent intent = new Intent(this, TransactionEditorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // Prefill values from FCM data payload
        if (data.containsKey("prefill_title")) {
            intent.putExtra("prefill_title", data.get("prefill_title"));
        }
        if (data.containsKey("prefill_amount")) {
            try {
                double amount = Double.parseDouble(data.get("prefill_amount"));
                intent.putExtra("prefill_amount", amount);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        if (data.containsKey("prefill_category_id")) {
            try {
                int categoryId = Integer.parseInt(data.get("prefill_category_id"));
                intent.putExtra("prefill_category_id", categoryId);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                (int) System.currentTimeMillis(), 
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, ArusKasApplication.CHANNEL_BILL_REMINDERS)
                        .setSmallIcon(com.aruskas.app.R.drawable.ic_notification) // custom icon
                        .setContentTitle(title != null ? title : "Tagihan Mendekati Jatuh Tempo")
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    private void sendBudgetAlertNotification(String title, String body) {
        Intent intent = new Intent(this, BudgetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                (int) System.currentTimeMillis(), 
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, ArusKasApplication.CHANNEL_BUDGET_ALERTS)
                        .setSmallIcon(com.aruskas.app.R.drawable.ic_notification) // custom icon
                        .setContentTitle(title != null ? title : "Peringatan Anggaran")
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
        }
    }

    private void sendGenericNotification(String title, String body) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        String channelId = ArusKasApplication.CHANNEL_BILL_REMINDERS; // default channel
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(com.aruskas.app.R.drawable.ic_notification)
                        .setContentTitle(title != null ? title : "ArusKas")
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.notify(0, notificationBuilder.build());
        }
    }
}

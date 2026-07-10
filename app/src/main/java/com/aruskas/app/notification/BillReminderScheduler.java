package com.aruskas.app.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.aruskas.app.model.RecurringTransaction;

import java.util.Calendar;
import java.util.List;

/**
 * Utility class to schedule/cancel recurring bill reminder alarms via AlarmManager.
 * Each bill gets a monthly repeating alarm 1 day before its due_day at 09:00 AM.
 */
public class BillReminderScheduler {

    private static final String TAG = "BillReminderScheduler";

    /**
     * Schedule monthly reminder alarms for all active recurring transactions that have reminders enabled.
     */
    public static void scheduleAll(Context context, List<RecurringTransaction> recurrings) {
        for (RecurringTransaction recurring : recurrings) {
            if (recurring.isActive() && recurring.isReminderEnabled()) {
                schedule(context, recurring);
            } else {
                cancel(context, recurring.getId());
            }
        }
    }

    /**
     * Schedule a single bill reminder alarm.
     * Fires 1 day before due_day at 09:00 AM local time.
     * If that time has already passed this month, schedule for next month.
     */
    public static void schedule(Context context, RecurringTransaction recurring) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, BillReminderReceiver.class);
        intent.putExtra(BillReminderReceiver.EXTRA_BILL_ID, recurring.getId());
        intent.putExtra(BillReminderReceiver.EXTRA_BILL_TITLE, recurring.getTitle());
        intent.putExtra(BillReminderReceiver.EXTRA_BILL_AMOUNT, recurring.getAmount());
        intent.putExtra(BillReminderReceiver.EXTRA_BILL_DUE_DAY, recurring.getDueDay());
        intent.putExtra(BillReminderReceiver.EXTRA_BILL_CATEGORY_ID, recurring.getCategoryId());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                recurring.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Calculate reminder time: 1 day before due_day at 09:00
        Calendar calendar = Calendar.getInstance();
        int reminderDay = recurring.getDueDay() - 1;
        if (reminderDay < 1) reminderDay = 1;

        calendar.set(Calendar.DAY_OF_MONTH, reminderDay);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the reminder time has already passed this month, schedule for next month
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.MONTH, 1);
        }

        Log.d(TAG, "Scheduling bill reminder for \"" + recurring.getTitle() 
                + "\" at " + calendar.getTime());

        try {
            // Use setExactAndAllowWhileIdle for reliable delivery on Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } catch (SecurityException e) {
            // On Android 12+, SCHEDULE_EXACT_ALARM may not be granted
            Log.w(TAG, "Cannot schedule exact alarm, falling back to inexact", e);
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    /**
     * Cancel a previously scheduled reminder alarm for a given bill ID.
     */
    public static void cancel(Context context, int billId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, BillReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                billId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Cancelled bill reminder for ID: " + billId);
    }
}

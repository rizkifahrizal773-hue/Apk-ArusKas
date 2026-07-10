package com.aruskas.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ArusKasApplication extends Application {

    public static final String CHANNEL_BILL_REMINDERS = "bill_reminders";
    public static final String CHANNEL_BUDGET_ALERTS = "budget_alerts";

    private static ArusKasApplication instance;

    public static ArusKasApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Bill Reminders Channel
            CharSequence billName = "Pengingat Tagihan";
            String billDescription = "Notifikasi pengingat tagihan berulang yang akan segera jatuh tempo";
            int importanceBill = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel billChannel = new NotificationChannel(CHANNEL_BILL_REMINDERS, billName, importanceBill);
            billChannel.setDescription(billDescription);

            // Budget Alerts Channel
            CharSequence budgetName = "Peringatan Anggaran";
            String budgetDescription = "Notifikasi batas pengeluaran anggaran bulanan kategori";
            int importanceBudget = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel budgetChannel = new NotificationChannel(CHANNEL_BUDGET_ALERTS, budgetName, importanceBudget);
            budgetChannel.setDescription(budgetDescription);

            // Register channels with system
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(billChannel);
                manager.createNotificationChannel(budgetChannel);
            }
        }
    }
}

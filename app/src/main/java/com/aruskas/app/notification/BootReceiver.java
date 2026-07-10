package com.aruskas.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.RecurringTransaction;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BroadcastReceiver triggered on BOOT_COMPLETED.
 * Re-schedules all bill reminder alarms because AlarmManager alarms are lost on reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device booted. Re-scheduling bill reminders...");

            SessionManager sessionManager = new SessionManager(context);
            if (!sessionManager.isLoggedIn()) {
                Log.d(TAG, "User not logged in. Skipping alarm reschedule.");
                return;
            }

            // Fetch recurring transactions from server and reschedule alarms
            ApiClient.getApiService().getRecurringTransactions().enqueue(
                    new Callback<ApiResponse<List<RecurringTransaction>>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<List<RecurringTransaction>>> call,
                                              Response<ApiResponse<List<RecurringTransaction>>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getData() != null) {
                                List<RecurringTransaction> recurrings = response.body().getData();
                                BillReminderScheduler.scheduleAll(context, recurrings);
                                Log.d(TAG, "Re-scheduled " + recurrings.size() + " bill reminders.");
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<List<RecurringTransaction>>> call, Throwable t) {
                            Log.e(TAG, "Failed to fetch recurrings for reschedule: " + t.getMessage());
                        }
                    }
            );
        }
    }
}

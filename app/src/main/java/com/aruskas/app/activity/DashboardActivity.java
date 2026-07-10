package com.aruskas.app.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aruskas.app.R;
import com.aruskas.app.adapter.TransactionAdapter;
import com.aruskas.app.databinding.ActivityDashboardBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.FinanceSummary;
import com.aruskas.app.model.MonthlyTrend;
import com.aruskas.app.model.Transaction;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.DateTimeUtil;
import com.aruskas.app.util.SessionManager;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private ActivityDashboardBinding binding;
    private TransactionAdapter transactionAdapter;
    private boolean isChartAnimated = false;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogout.setOnClickListener(v -> {
            sessionManager.clearSessionAndLogout();
            com.aruskas.app.notification.NotificationHelper.resetWelcomeFlag(this);
            Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        setupNavigationShortcuts();
        setupRecentTransactionsList();
        setupFAB();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Request notification permission on Android 13+ (API 33)
        requestNotificationPermission();
        // Refresh dashboard statistics, recent transactions and charts on resume
        fetchDashboardData();
        // Re-schedule bill reminder alarms in the background
        scheduleBillReminders();
        // Show welcome notification if permitted
        showWelcomeNotificationIfPermitted();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void showWelcomeNotificationIfPermitted() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        com.aruskas.app.model.User currentUser = sessionManager.getUserDetails();
        if (currentUser != null) {
            com.aruskas.app.notification.NotificationHelper.showWelcomeNotificationOnce(
                    this, currentUser.getName());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                showWelcomeNotificationIfPermitted();
            }
        }
    }

    private void scheduleBillReminders() {
        ApiClient.getApiService().getRecurringTransactions().enqueue(new Callback<ApiResponse<java.util.List<com.aruskas.app.model.RecurringTransaction>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<java.util.List<com.aruskas.app.model.RecurringTransaction>>> call,
                                   @NonNull Response<ApiResponse<java.util.List<com.aruskas.app.model.RecurringTransaction>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    com.aruskas.app.notification.BillReminderScheduler.scheduleAll(
                            DashboardActivity.this, response.body().getData());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<java.util.List<com.aruskas.app.model.RecurringTransaction>>> call, @NonNull Throwable t) {
                // Silently ignore - alarm scheduling is best-effort
            }
        });
    }

    private void setupNavigationShortcuts() {
        binding.btnShortcutCategory.setOnClickListener(v -> 
                startActivity(new Intent(this, CategoryActivity.class)));
        
        binding.btnShortcutBudget.setOnClickListener(v -> 
                startActivity(new Intent(this, BudgetActivity.class)));
        
        binding.btnShortcutReport.setOnClickListener(v -> 
                startActivity(new Intent(this, ReportActivity.class)));
        
        binding.btnShortcutRecurring.setOnClickListener(v -> 
                startActivity(new Intent(this, RecurringTransactionActivity.class)));

        binding.btnSeeAllTransactions.setOnClickListener(v -> 
                startActivity(new Intent(this, TransactionListActivity.class)));
    }

    private void setupRecentTransactionsList() {
        // Tapping recent item opens editor in edit mode
        transactionAdapter = new TransactionAdapter(transaction -> {
            Intent intent = new Intent(this, TransactionEditorActivity.class);
            intent.putExtra("transaction_id", transaction.getId());
            startActivity(intent);
        });
        binding.recyclerRecentTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecentTransactions.setAdapter(transactionAdapter);
    }

    private void setupFAB() {
        binding.fabAddTransaction.setOnClickListener(v -> 
                startActivity(new Intent(this, TransactionEditorActivity.class)));
    }

    private void fetchDashboardData() {
        String currentMonth = DateTimeUtil.getCurrentMonthIso();

        // 1. Fetch Finance Summary
        ApiClient.getApiService().getTransactionSummary(currentMonth).enqueue(new Callback<ApiResponse<FinanceSummary>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<FinanceSummary>> call, @NonNull Response<ApiResponse<FinanceSummary>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<FinanceSummary> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        bindFinanceSummary(apiResponse.getData());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<FinanceSummary>> call, @NonNull Throwable t) {
                // Keep default values if network fails, or show silent warnings
            }
        });

        // 2. Fetch Recent Transactions (for the current month)
        ApiClient.getApiService().getTransactions(currentMonth, null, null, null).enqueue(new Callback<ApiResponse<List<Transaction>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Transaction>>> call, @NonNull Response<ApiResponse<List<Transaction>>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Transaction>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<Transaction> transactions = apiResponse.getData();
                        bindRecentTransactions(transactions);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Transaction>>> call, @NonNull Throwable t) {
                binding.recyclerRecentTransactions.setVisibility(View.GONE);
                binding.textEmptyTransactions.setVisibility(View.VISIBLE);
                binding.textEmptyTransactions.setText("Gagal memuat transaksi terbaru.");
            }
        });

        // 3. Fetch 6 Months Trend Reports
        ApiClient.getApiService().getMonthlyTrend(6).enqueue(new Callback<ApiResponse<List<MonthlyTrend>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<MonthlyTrend>>> call, @NonNull Response<ApiResponse<List<MonthlyTrend>>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<MonthlyTrend>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        setupTrendChart(apiResponse.getData());
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<MonthlyTrend>>> call, @NonNull Throwable t) {
                // Silent error
            }
        });
    }

    private void bindFinanceSummary(FinanceSummary summary) {
        binding.textTotalBalance.setText(CurrencyUtil.formatRupiah(summary.getBalance()));
        binding.textMonthIncome.setText(CurrencyUtil.formatRupiah(summary.getTotalIncome()));
        binding.textMonthExpense.setText(CurrencyUtil.formatRupiah(summary.getTotalExpense()));

        // Calculate MoM trend change
        double previousBalance = summary.getPreviousMonthBalance();
        double currentBalance = summary.getBalance();

        if (previousBalance != 0) {
            double pctChange = ((currentBalance - previousBalance) / previousBalance) * 100;
            binding.layoutTrend.setVisibility(View.VISIBLE);
            
            boolean isPositive = pctChange >= 0;
            binding.imgTrendArrow.setImageResource(isPositive ? R.drawable.ic_trend_up : R.drawable.ic_trend_down);
            binding.imgTrendArrow.setImageTintList(ColorStateList.valueOf(
                    getColor(isPositive ? R.color.colorIncome : R.color.colorExpense)
            ));
            
            String pctString = String.format(Locale.getDefault(), "%s%.1f%% vs bulan lalu", 
                    isPositive ? "+" : "", pctChange);
            binding.textTrendPercentage.setText(pctString);
        } else {
            binding.layoutTrend.setVisibility(View.GONE);
        }
    }

    private void bindRecentTransactions(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            binding.recyclerRecentTransactions.setVisibility(View.GONE);
            binding.textEmptyTransactions.setVisibility(View.VISIBLE);
        } else {
            binding.textEmptyTransactions.setVisibility(View.GONE);
            binding.recyclerRecentTransactions.setVisibility(View.VISIBLE);
            
            // Limit to top 5 recent items client-side as per PRD 7.1
            List<Transaction> recent5 = new ArrayList<>();
            for (int i = 0; i < Math.min(5, transactions.size()); i++) {
                recent5.add(transactions.get(i));
            }
            transactionAdapter.setTransactions(recent5);
        }
    }

    private void setupTrendChart(List<MonthlyTrend> trendList) {
        if (trendList == null || trendList.isEmpty()) return;

        BarChart barChart = binding.barChartTrend;
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);

        // Customize X-Axis values
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(trendList.size());
        xAxis.setTextColor(getColor(R.color.colorTextSecondary));

        final List<String> monthLabels = new ArrayList<>();
        for (MonthlyTrend trend : trendList) {
            monthLabels.add(DateTimeUtil.formatIsoToDisplayMonth(trend.getMonth()));
        }

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < monthLabels.size()) {
                    // Try to display short month, e.g. "Juli 2026" -> "Juli"
                    String fullLabel = monthLabels.get(index);
                    if (fullLabel.contains(" ")) {
                        return fullLabel.substring(0, fullLabel.indexOf(" "));
                    }
                    return fullLabel;
                }
                return "";
            }
        });

        // Customize Y-Axis
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getColor(R.color.colorBorder));
        leftAxis.setTextColor(getColor(R.color.colorTextSecondary));
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Short currency representation to save space (e.g. "Rp 10Jt" or "Rp 500Rb")
                if (value >= 1_000_000) {
                    return String.format(Locale.US, "Rp %.1fJt", value / 1_000_000.0);
                } else if (value >= 1_000) {
                    return String.format(Locale.US, "Rp %.0fRb", value / 1_000.0);
                } else {
                    return String.format(Locale.US, "Rp %.0f", value);
                }
            }
        });

        // Disable right side axis
        barChart.getAxisRight().setEnabled(false);

        // Construct datasets
        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();

        for (int i = 0; i < trendList.size(); i++) {
            MonthlyTrend trend = trendList.get(i);
            incomeEntries.add(new BarEntry(i, (float) trend.getTotalIncome()));
            expenseEntries.add(new BarEntry(i, (float) trend.getTotalExpense()));
        }

        BarDataSet setIncome = new BarDataSet(incomeEntries, "Pemasukan");
        setIncome.setColor(getColor(R.color.colorIncome));
        setIncome.setDrawValues(false);

        BarDataSet setExpense = new BarDataSet(expenseEntries, "Pengeluaran");
        setExpense.setColor(getColor(R.color.colorExpense));
        setExpense.setDrawValues(false);

        BarData data = new BarData(setIncome, setExpense);
        
        float groupSpace = 0.34f;
        float barSpace = 0.03f;
        float barWidth = 0.3f;
        // Calculation: (barWidth + barSpace) * 2 + groupSpace = (0.30 + 0.03) * 2 + 0.34 = 1.0 (must sum up to 1.0)
        data.setBarWidth(barWidth);
        
        barChart.setData(data);
        barChart.groupBars(0f, groupSpace, barSpace);
        
        // Centering xAxis labels on grouped bars
        barChart.getXAxis().setAxisMinimum(0f);
        barChart.getXAxis().setAxisMaximum(data.getGroupWidth(groupSpace, barSpace) * trendList.size());
        barChart.getXAxis().setCenterAxisLabels(true);
        barChart.setFitBars(true);

        // Styling Legend
        Legend legend = barChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(true);
        legend.setYOffset(0f);
        legend.setXOffset(0f);
        legend.setYEntrySpace(0f);
        legend.setTextSize(10f);
        legend.setTextColor(getColor(R.color.colorTextPrimary));

        // Animate only once on first load
        if (!isChartAnimated) {
            barChart.animateY(1200);
            isChartAnimated = true;
        } else {
            barChart.invalidate();
        }
    }
}

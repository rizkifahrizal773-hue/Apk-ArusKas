package com.aruskas.app.activity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aruskas.app.R;
import com.aruskas.app.adapter.TransactionAdapter;
import com.aruskas.app.databinding.ActivityTransactionListBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.Transaction;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.DateTimeUtil;

import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionListActivity extends AppCompatActivity {

    private ActivityTransactionListBinding binding;
    private TransactionAdapter transactionAdapter;

    // Filter states
    private String selectedMonthIso = ""; // YYYY-MM
    private String selectedType = null;   // null for all, "income", "expense"
    private String searchKeyword = "";

    // Debounce search handler
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Refresh list launcher when returning from Edit Mode
    private final ActivityResultLauncher<Intent> editTransactionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    fetchTransactions();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize filters
        selectedMonthIso = DateTimeUtil.getCurrentMonthIso();

        setupToolbar();
        setupFilters();
        setupRecyclerView();

        fetchTransactions();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        // Month button
        binding.btnMonthFilter.setText(DateTimeUtil.formatIsoToDisplayMonth(selectedMonthIso));
        binding.btnMonthFilter.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(DateTimeUtil.parseIsoMonth(selectedMonthIso));
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        selectedMonthIso = String.format(java.util.Locale.US, "%d-%02d", selectedYear, selectedMonth + 1);
                        binding.btnMonthFilter.setText(DateTimeUtil.formatIsoToDisplayMonth(selectedMonthIso));
                        fetchTransactions();
                    }, year, month, 1);
            
            // Hide standard day picker headers to simulate month-only picker
            datePickerDialog.setTitle("Pilih Bulan");
            datePickerDialog.show();
        });
    }

    private void setupFilters() {
        // 1. Text Search Input with Debounce
        binding.inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchKeyword = s.toString().trim();
                
                // Debounce to prevent server flooding
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> fetchTransactions();
                searchHandler.postDelayed(searchRunnable, 400);
            }
        });

        // 2. Type Filter Selection
        binding.toggleTypeFilter.check(R.id.btn_filter_all);
        binding.toggleTypeFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_filter_all) {
                    selectedType = null;
                } else if (checkedId == R.id.btn_filter_income) {
                    selectedType = "income";
                } else if (checkedId == R.id.btn_filter_expense) {
                    selectedType = "expense";
                }
                fetchTransactions();
            }
        });

        // 3. Swipe To Refresh
        binding.swipeRefresh.setOnRefreshListener(this::fetchTransactions);
        binding.swipeRefresh.setColorSchemeColors(getColor(R.color.colorPrimary));
    }

    private void setupRecyclerView() {
        transactionAdapter = new TransactionAdapter(transaction -> {
            // Launch editor in edit mode
            Intent intent = new Intent(this, TransactionEditorActivity.class);
            intent.putExtra("transaction_id", transaction.getId());
            editTransactionLauncher.launch(intent);
        });

        binding.recyclerTransactions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTransactions.setAdapter(transactionAdapter);
    }

    private void fetchTransactions() {
        binding.swipeRefresh.setRefreshing(true);
        
        ApiClient.getApiService().getTransactions(
                selectedMonthIso,
                null, // fetch all categories client-side filters
                selectedType,
                searchKeyword.isEmpty() ? null : searchKeyword
        ).enqueue(new Callback<ApiResponse<List<Transaction>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Transaction>>> call, @NonNull Response<ApiResponse<List<Transaction>>> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Transaction>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        bindTransactionsList(apiResponse.getData());
                    } else {
                        showEmptyState(true, apiResponse.getMessage());
                    }
                } else {
                    showEmptyState(true, "Gagal memuat daftar riwayat.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Transaction>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.swipeRefresh.setRefreshing(false);
                showEmptyState(true, "Koneksi gagal. Tidak dapat memuat data riwayat.");
            }
        });
    }

    private void bindTransactionsList(List<Transaction> list) {
        if (list.isEmpty()) {
            showEmptyState(true, "Tidak ada transaksi yang cocok.");
        } else {
            showEmptyState(false, null);
            transactionAdapter.setTransactions(list);
        }
    }

    private void showEmptyState(boolean isEmpty, String message) {
        if (isEmpty) {
            binding.recyclerTransactions.setVisibility(View.GONE);
            binding.textEmptyTransactions.setVisibility(View.VISIBLE);
            if (message != null) {
                binding.textEmptyTransactions.setText(message);
            }
        } else {
            binding.recyclerTransactions.setVisibility(View.VISIBLE);
            binding.textEmptyTransactions.setVisibility(View.GONE);
        }
    }
}

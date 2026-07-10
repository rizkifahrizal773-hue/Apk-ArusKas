package com.aruskas.app.activity;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aruskas.app.R;
import com.aruskas.app.adapter.BudgetAdapter;
import com.aruskas.app.databinding.ActivityBudgetBinding;
import com.aruskas.app.databinding.DialogAddEditBudgetBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.Budget;
import com.aruskas.app.model.Category;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.DateTimeUtil;
import com.aruskas.app.util.ValidationUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BudgetActivity extends AppCompatActivity {

    private ActivityBudgetBinding binding;
    private BudgetAdapter budgetAdapter;
    private final List<Budget> budgetsList = new ArrayList<>();

    // Selected Month (YYYY-MM)
    private String selectedMonthIso = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBudgetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        selectedMonthIso = DateTimeUtil.getCurrentMonthIso();

        setupToolbar();
        setupRecyclerView();
        setupFAB();

        fetchBudgets();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        
        binding.btnMonthFilter.setText(DateTimeUtil.formatIsoToDisplayMonth(selectedMonthIso));
        binding.btnMonthFilter.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(DateTimeUtil.parseIsoMonth(selectedMonthIso));
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        selectedMonthIso = String.format(Locale.US, "%d-%02d", selectedYear, selectedMonth + 1);
                        binding.btnMonthFilter.setText(DateTimeUtil.formatIsoToDisplayMonth(selectedMonthIso));
                        fetchBudgets();
                    }, year, month, 1);
            
            datePickerDialog.setTitle("Pilih Bulan");
            datePickerDialog.show();
        });
    }

    private void setupRecyclerView() {
        budgetAdapter = new BudgetAdapter(this::showBudgetFormDialog);
        binding.recyclerBudgets.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerBudgets.setAdapter(budgetAdapter);
    }

    private void setupFAB() {
        binding.fabAddBudget.setOnClickListener(v -> showBudgetFormDialog(null));
    }

    private void fetchBudgets() {
        setLoading(true);
        ApiClient.getApiService().getBudgets(selectedMonthIso).enqueue(new Callback<ApiResponse<List<Budget>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Budget>>> call, @NonNull Response<ApiResponse<List<Budget>>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Budget>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        budgetsList.clear();
                        budgetsList.addAll(apiResponse.getData());
                        bindBudgetData();
                    } else {
                        showEmptyState(true, apiResponse.getMessage());
                    }
                } else {
                    showEmptyState(true, "Gagal memuat anggaran belanja.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Budget>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showEmptyState(true, "Koneksi gagal. Tidak dapat memuat anggaran.");
            }
        });
    }

    private void bindBudgetData() {
        if (budgetsList.isEmpty()) {
            showEmptyState(true, null);
            binding.cardSummary.setVisibility(View.GONE);
        } else {
            showEmptyState(false, null);
            binding.cardSummary.setVisibility(View.VISIBLE);
            budgetAdapter.setBudgets(budgetsList);

            // Compute cumulative totals
            double totalSpent = 0.0;
            double totalLimit = 0.0;

            for (Budget budget : budgetsList) {
                totalSpent += budget.getSpentAmount();
                if (budget.getId() > 0) {
                    totalLimit += budget.getLimitAmount();
                }
            }

            String summaryText = String.format("Rp %s terpakai dari Rp %s",
                    CurrencyUtil.formatNumberOnly(totalSpent),
                    CurrencyUtil.formatNumberOnly(totalLimit));
            binding.textTotalLimitSummary.setText(summaryText);

            int percentage = totalLimit > 0 ? (int) ((totalSpent / totalLimit) * 100) : 0;
            binding.progressTotalBudget.setProgress(Math.min(percentage, 100));
        }
    }

    private void showBudgetFormDialog(Budget budget) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ArusKas_BottomSheetDialog);
        DialogAddEditBudgetBinding dialogBinding = DialogAddEditBudgetBinding.inflate(LayoutInflater.from(this));
        dialog.setContentView(dialogBinding.getRoot());
        
        dialogBinding.btnSaveBudget.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary)));

        boolean isEdit = budget != null && budget.getId() > 0;
        
        // 1. Dynamic TextWatcher for amount formatting
        dialogBinding.inputLimit.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(current)) {
                    dialogBinding.inputLimit.removeTextChangedListener(this);
                    String cleanString = s.toString().replaceAll("[Rp\\s.]", "");
                    if (!cleanString.isEmpty()) {
                        try {
                            double parsed = Double.parseDouble(cleanString);
                            String formatted = CurrencyUtil.formatNumberOnly(parsed);
                            current = formatted;
                            dialogBinding.inputLimit.setText(formatted);
                            dialogBinding.inputLimit.setSelection(formatted.length());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    } else {
                        current = "";
                    }
                    dialogBinding.inputLimit.addTextChangedListener(this);
                }
            }
        });

        // Setup layouts based on mode
        final List<Category> availableCategories = new ArrayList<>();
        if (isEdit) {
            dialogBinding.textDialogTitle.setText("Ubah Anggaran");
            dialogBinding.inputCategoryLayout.setVisibility(View.GONE);
            dialogBinding.textCategoryStatic.setVisibility(View.VISIBLE);
            dialogBinding.textCategoryStatic.setText("Kategori: " + budget.getCategory().getName());
            dialogBinding.inputLimit.setText(CurrencyUtil.formatNumberOnly(budget.getLimitAmount()));
            dialogBinding.btnDeleteBudget.setVisibility(View.VISIBLE);
        } else {
            dialogBinding.textDialogTitle.setText("Atur Anggaran Kategori");
            dialogBinding.inputCategoryLayout.setVisibility(View.VISIBLE);
            dialogBinding.textCategoryStatic.setVisibility(View.GONE);
            dialogBinding.btnDeleteBudget.setVisibility(View.GONE);

            // Populate category selector
            // Only list expense categories that do not have a budget yet
            List<String> names = new ArrayList<>();
            for (Budget b : budgetsList) {
                if (b.getId() == 0 && b.getCategory() != null) {
                    availableCategories.add(b.getCategory());
                    names.add(b.getCategory().getName());
                }
            }

            // Pre-select category if tapping an item that has 0 id (budget hasn't been set yet)
            int preselectedCatIndex = -1;
            if (budget != null && budget.getCategory() != null) {
                for (int i = 0; i < availableCategories.size(); i++) {
                    if (availableCategories.get(i).getId() == budget.getCategory().getId()) {
                        preselectedCatIndex = i;
                        break;
                    }
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, names);
            dialogBinding.inputCategory.setAdapter(adapter);

            if (preselectedCatIndex != -1) {
                dialogBinding.inputCategory.setText(availableCategories.get(preselectedCatIndex).getName(), false);
            }
        }

        // Save Click
        dialogBinding.btnSaveBudget.setOnClickListener(v -> {
            String limitStr = dialogBinding.inputLimit.getText().toString().trim();
            double limitVal = CurrencyUtil.parseRupiah(limitStr);

            String limitError = ValidationUtil.validateBudgetLimitAmount(limitVal);
            if (limitError != null) {
                dialogBinding.inputLimitLayout.setError(limitError);
                return;
            } else {
                dialogBinding.inputLimitLayout.setError(null);
            }

            int catId = -1;
            if (isEdit) {
                catId = budget.getCategoryId();
            } else {
                String chosenCatName = dialogBinding.inputCategory.getText().toString().trim();
                for (Category cat : availableCategories) {
                    if (cat.getName().equals(chosenCatName)) {
                        catId = cat.getId();
                        break;
                    }
                }
            }

            String catError = ValidationUtil.validateBudgetCategory(catId);
            if (catError != null) {
                dialogBinding.inputCategoryLayout.setError(catError);
                return;
            } else {
                dialogBinding.inputCategoryLayout.setError(null);
            }

            setLoading(true);
            dialog.dismiss();

            if (isEdit) {
                // Update
                Budget updated = new Budget();
                updated.setId(budget.getId());
                updated.setLimitAmount(limitVal);
                
                ApiClient.getApiService().updateBudget(budget.getId(), updated).enqueue(new Callback<ApiResponse<Budget>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Budget>> call, @NonNull Response<ApiResponse<Budget>> response) {
                        fetchBudgets();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Budget>> call, @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(BudgetActivity.this, "Gagal memperbarui anggaran", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                // Create
                Budget created = new Budget();
                created.setCategoryId(catId);
                created.setMonth(selectedMonthIso);
                created.setLimitAmount(limitVal);

                ApiClient.getApiService().createBudget(created).enqueue(new Callback<ApiResponse<Budget>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Budget>> call, @NonNull Response<ApiResponse<Budget>> response) {
                        fetchBudgets();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Budget>> call, @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(BudgetActivity.this, "Gagal menambahkan anggaran", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Delete Click
        dialogBinding.btnDeleteBudget.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Hapus Anggaran")
                    .setMessage("Apakah Anda yakin ingin menghapus anggaran kategori ini?")
                    .setPositiveButton("Hapus", (d, which) -> {
                        dialog.dismiss();
                        setLoading(true);
                        ApiClient.getApiService().deleteBudget(budget.getId()).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> response) {
                                fetchBudgets();
                            }

                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                                setLoading(false);
                                Toast.makeText(BudgetActivity.this, "Gagal menghapus anggaran", Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        dialog.show();
    }

    private void showEmptyState(boolean isEmpty, String message) {
        if (isEmpty) {
            binding.recyclerBudgets.setVisibility(View.GONE);
            binding.textEmptyBudgets.setVisibility(View.VISIBLE);
            if (message != null) {
                binding.textEmptyBudgets.setText(message);
            }
        } else {
            binding.recyclerBudgets.setVisibility(View.VISIBLE);
            binding.textEmptyBudgets.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean isLoading) {
        binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}

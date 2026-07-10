package com.aruskas.app.activity;

import android.app.AlertDialog;
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
import com.aruskas.app.adapter.RecurringAdapter;
import com.aruskas.app.databinding.ActivityRecurringTransactionBinding;
import com.aruskas.app.databinding.DialogAddEditRecurringBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.Category;
import com.aruskas.app.model.RecurringTransaction;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.ValidationUtil;
import com.aruskas.app.notification.BillReminderScheduler;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RecurringTransactionActivity extends AppCompatActivity {

    private ActivityRecurringTransactionBinding binding;
    private RecurringAdapter recurringAdapter;
    private final List<RecurringTransaction> recurringsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecurringTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupRecyclerView();
        setupFAB();

        fetchRecurrings();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        // Handle item clicks and switch toggles
        recurringAdapter = new RecurringAdapter(
                this::showRecurringFormDialog,
                this::toggleReminderEnabled
        );
        binding.recyclerRecurrings.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecurrings.setAdapter(recurringAdapter);
    }

    private void setupFAB() {
        binding.fabAddRecurring.setOnClickListener(v -> showRecurringFormDialog(null));
    }

    private void fetchRecurrings() {
        setLoading(true);
        ApiClient.getApiService().getRecurringTransactions().enqueue(new Callback<ApiResponse<List<RecurringTransaction>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<RecurringTransaction>>> call, @NonNull Response<ApiResponse<List<RecurringTransaction>>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<RecurringTransaction>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        recurringsList.clear();
                        recurringsList.addAll(apiResponse.getData());
                        bindRecurringData();
                    } else {
                        showEmptyState(true, apiResponse.getMessage());
                    }
                } else {
                    showEmptyState(true, "Gagal memuat daftar tagihan.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<RecurringTransaction>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showEmptyState(true, "Koneksi gagal. Tidak dapat memuat tagihan.");
            }
        });
    }

    private void bindRecurringData() {
        if (recurringsList.isEmpty()) {
            showEmptyState(true, null);
        } else {
            showEmptyState(false, null);
            recurringAdapter.setRecurrings(recurringsList);
        }
        // Schedule/cancel all bill reminder alarms based on latest data
        BillReminderScheduler.scheduleAll(this, recurringsList);
    }

    private void toggleReminderEnabled(RecurringTransaction recurring, boolean isChecked) {
        setLoading(true);
        
        RecurringTransaction updatePayload = new RecurringTransaction();
        updatePayload.setId(recurring.getId());
        updatePayload.setReminderEnabled(isChecked);

        ApiClient.getApiService().updateRecurringTransaction(recurring.getId(), updatePayload).enqueue(new Callback<ApiResponse<RecurringTransaction>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Response<ApiResponse<RecurringTransaction>> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(RecurringTransactionActivity.this, "Status pengingat disimpan", Toast.LENGTH_SHORT).show();
                    // Update state in local list quietly
                    recurring.setReminderEnabled(isChecked);
                    // Schedule or cancel alarm based on new state
                    if (isChecked && recurring.isActive()) {
                        BillReminderScheduler.schedule(RecurringTransactionActivity.this, recurring);
                    } else {
                        BillReminderScheduler.cancel(RecurringTransactionActivity.this, recurring.getId());
                    }
                } else {
                    // Revert UI check if server failed
                    fetchRecurrings();
                    Toast.makeText(RecurringTransactionActivity.this, "Gagal memperbarui status pengingat", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Throwable t) {
                setLoading(false);
                fetchRecurrings();
                Toast.makeText(RecurringTransactionActivity.this, "Koneksi gagal. Perubahan tidak tersimpan.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRecurringFormDialog(RecurringTransaction recurring) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ArusKas_BottomSheetDialog);
        DialogAddEditRecurringBinding dialogBinding = DialogAddEditRecurringBinding.inflate(LayoutInflater.from(this));
        dialog.setContentView(dialogBinding.getRoot());

        boolean isEdit = recurring != null;
        final String[] activeType = {"expense"};
        final int[] selectedCatId = {-1};
        final List<Category> categoriesList = new ArrayList<>();
        
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        dialogBinding.inputCategory.setAdapter(categoryAdapter);

        // Helper to load categories for spinner
        Runnable loadCategories = () -> {
            ApiClient.getApiService().getCategories(activeType[0]).enqueue(new Callback<ApiResponse<List<Category>>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Response<ApiResponse<List<Category>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        categoriesList.clear();
                        categoriesList.addAll(response.body().getData());

                        List<String> names = new ArrayList<>();
                        int selectedIndex = -1;

                        for (int i = 0; i < categoriesList.size(); i++) {
                            Category c = categoriesList.get(i);
                            names.add(c.getName());
                            if (c.getId() == selectedCatId[0]) {
                                selectedIndex = i;
                            }
                        }

                        categoryAdapter.clear();
                        categoryAdapter.addAll(names);
                        categoryAdapter.notifyDataSetChanged();

                        if (selectedIndex != -1) {
                            dialogBinding.inputCategory.setText(categoriesList.get(selectedIndex).getName(), false);
                        } else {
                            dialogBinding.inputCategory.setText("", false);
                            selectedCatId[0] = -1;
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Throwable t) {}
            });
        };

        // Real-time dot formatter for limit amount input
        dialogBinding.inputAmount.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(current)) {
                    dialogBinding.inputAmount.removeTextChangedListener(this);
                    String cleanString = s.toString().replaceAll("[Rp\\s.]", "");
                    if (!cleanString.isEmpty()) {
                        try {
                            double parsed = Double.parseDouble(cleanString);
                            String formatted = CurrencyUtil.formatNumberOnly(parsed);
                            current = formatted;
                            dialogBinding.inputAmount.setText(formatted);
                            dialogBinding.inputAmount.setSelection(formatted.length());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    } else {
                        current = "";
                    }
                    dialogBinding.inputAmount.addTextChangedListener(this);
                }
            }
        });

        // Set Category selection listener
        dialogBinding.inputCategory.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < categoriesList.size()) {
                selectedCatId[0] = categoriesList.get(position).getId();
            }
        });

        // Handle Type toggle selections
        dialogBinding.toggleRecurringType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_expense) {
                    activeType[0] = "expense";
                    dialogBinding.btnSaveRecurring.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary)));
                } else if (checkedId == R.id.btn_type_income) {
                    activeType[0] = "income";
                    dialogBinding.btnSaveRecurring.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorIncome)));
                }
                loadCategories.run();
            }
        });

        // Configure Dialog properties by mode
        if (isEdit) {
            dialogBinding.textDialogTitle.setText("Ubah Tagihan");
            dialogBinding.inputTitle.setText(recurring.getTitle());
            dialogBinding.inputAmount.setText(CurrencyUtil.formatNumberOnly(recurring.getAmount()));
            dialogBinding.inputDueDay.setText(String.valueOf(recurring.getDueDay()));
            dialogBinding.switchDialogReminder.setChecked(recurring.isReminderEnabled());

            activeType[0] = recurring.getType();
            selectedCatId[0] = recurring.getCategoryId();
            
            if ("expense".equals(activeType[0])) {
                dialogBinding.toggleRecurringType.check(R.id.btn_type_expense);
                dialogBinding.btnSaveRecurring.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary)));
            } else {
                dialogBinding.toggleRecurringType.check(R.id.btn_type_income);
                dialogBinding.btnSaveRecurring.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorIncome)));
            }

            dialogBinding.btnDeleteRecurring.setVisibility(View.VISIBLE);
        } else {
            dialogBinding.textDialogTitle.setText("Atur Pengingat Tagihan");
            dialogBinding.toggleRecurringType.check(R.id.btn_type_expense);
            dialogBinding.btnSaveRecurring.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorPrimary)));
            dialogBinding.btnDeleteRecurring.setVisibility(View.GONE);
        }

        loadCategories.run();

        // Save bill details
        dialogBinding.btnSaveRecurring.setOnClickListener(v -> {
            String titleVal = dialogBinding.inputTitle.getText().toString().trim();
            double amountVal = CurrencyUtil.parseRupiah(dialogBinding.inputAmount.getText().toString().trim());
            String dueDayStr = dialogBinding.inputDueDay.getText().toString().trim();
            int dueDayVal = dueDayStr.isEmpty() ? -1 : Integer.parseInt(dueDayStr);
            boolean reminderVal = dialogBinding.switchDialogReminder.isChecked();

            // Run form validations via ValidationUtil
            String titleError = ValidationUtil.validateRecurringTitle(titleVal);
            String amountError = ValidationUtil.validateRecurringAmount(amountVal);
            String catError = ValidationUtil.validateRecurringCategory(selectedCatId[0]);
            String dueDayError = ValidationUtil.validateRecurringDueDay(dueDayVal);

            if (titleError != null) {
                dialogBinding.inputTitleLayout.setError(titleError);
                return;
            } else {
                dialogBinding.inputTitleLayout.setError(null);
            }

            if (amountError != null) {
                dialogBinding.inputAmountLayout.setError(amountError);
                return;
            } else {
                dialogBinding.inputAmountLayout.setError(null);
            }

            if (catError != null) {
                dialogBinding.inputCategoryLayout.setError(catError);
                return;
            } else {
                dialogBinding.inputCategoryLayout.setError(null);
            }

            if (dueDayError != null) {
                dialogBinding.inputDueDayLayout.setError(dueDayError);
                return;
            } else {
                dialogBinding.inputDueDayLayout.setError(null);
            }

            setLoading(true);
            dialog.dismiss();

            RecurringTransaction payload = new RecurringTransaction();
            payload.setTitle(titleVal);
            payload.setType(activeType[0]);
            payload.setAmount(amountVal);
            payload.setCategoryId(selectedCatId[0]);
            payload.setDueDay(dueDayVal);
            payload.setReminderEnabled(reminderVal);

            if (isEdit) {
                payload.setId(recurring.getId());
                ApiClient.getApiService().updateRecurringTransaction(recurring.getId(), payload).enqueue(new Callback<ApiResponse<RecurringTransaction>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Response<ApiResponse<RecurringTransaction>> response) {
                        fetchRecurrings();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(RecurringTransactionActivity.this, "Gagal memperbarui tagihan", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                ApiClient.getApiService().createRecurringTransaction(payload).enqueue(new Callback<ApiResponse<RecurringTransaction>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Response<ApiResponse<RecurringTransaction>> response) {
                        fetchRecurrings();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<RecurringTransaction>> call, @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(RecurringTransactionActivity.this, "Gagal menyimpan tagihan baru", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Delete bill details
        dialogBinding.btnDeleteRecurring.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Hapus Pengingat Tagihan")
                    .setMessage("Apakah Anda yakin ingin menghapus tagihan berulang ini?")
                    .setPositiveButton("Hapus", (d, which) -> {
                        dialog.dismiss();
                        setLoading(true);
                        ApiClient.getApiService().deleteRecurringTransaction(recurring.getId()).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> response) {
                                fetchRecurrings();
                            }

                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                                setLoading(false);
                                Toast.makeText(RecurringTransactionActivity.this, "Gagal menghapus tagihan", Toast.LENGTH_SHORT).show();
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
            binding.recyclerRecurrings.setVisibility(View.GONE);
            binding.textEmptyRecurrings.setVisibility(View.VISIBLE);
            if (message != null) {
                binding.textEmptyRecurrings.setText(message);
            }
        } else {
            binding.recyclerRecurrings.setVisibility(View.VISIBLE);
            binding.textEmptyRecurrings.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean isLoading) {
        binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}

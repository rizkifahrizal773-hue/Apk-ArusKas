package com.aruskas.app.activity;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.aruskas.app.R;
import com.aruskas.app.databinding.ActivityTransactionEditorBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.Category;
import com.aruskas.app.model.Transaction;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.DateTimeUtil;
import com.aruskas.app.util.ImageUtil;
import com.aruskas.app.util.ValidationUtil;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionEditorActivity extends AppCompatActivity {

    private ActivityTransactionEditorBinding binding;

    private int transactionId = -1;
    private boolean isEditMode = false;

    // Form values
    private String selectedType = "expense"; // default
    private String selectedDateIso = "";
    private int selectedCategoryId = -1;
    private Uri selectedImageUri = null;
    private String existingReceiptUrl = null;

    // Categories list state
    private final List<Category> categoriesList = new ArrayList<>();
    private ArrayAdapter<String> categorySpinnerAdapter;

    // Dynamic camera capture photo file URI
    private Uri cameraImageUri = null;

    // Activity launchers
    private ActivityResultLauncher<String> galleryPickerLauncher;
    private ActivityResultLauncher<Uri> cameraCaptureLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransactionEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        transactionId = getIntent().getIntExtra("transaction_id", -1);
        isEditMode = transactionId != -1;

        setupToolbar();
        setupInputFormatters();
        setupDatePicker();
        setupTypeToggle();
        setupCategoryDropdown();
        setupReceiptPickers();

        // Check if there are prefilled notification parameters
        handlePrefillsFromIntent();

        // Setup save/delete button clicks
        binding.btnSaveTransaction.setOnClickListener(v -> saveTransaction());
        binding.btnDeleteTransaction.setOnClickListener(v -> confirmDeleteTransaction());

        if (isEditMode) {
            loadTransactionDetails();
        } else {
            // Set default date to today
            selectedDateIso = DateTimeUtil.getCurrentDateIso();
            binding.inputDate.setText(DateTimeUtil.formatIsoToDisplayDate(selectedDateIso));
            loadCategoriesForType(selectedType, -1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh categories list on resume (in case they added a new category via shortcut)
        if (selectedCategoryId != -1) {
            loadCategoriesForType(selectedType, selectedCategoryId);
        } else {
            loadCategoriesForType(selectedType, -1);
        }
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.textTitle.setText(isEditMode ? "Ubah Transaksi" : "Transaksi Baru");
    }

    private void setupInputFormatters() {
        // Real-time currency dot separator formatter
        binding.inputAmount.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(current)) {
                    binding.inputAmount.removeTextChangedListener(this);

                    // Parse formatted value to clean numbers and format back with dots
                    String cleanString = s.toString().replaceAll("[Rp\\s.]", "");
                    if (!cleanString.isEmpty()) {
                        try {
                            double parsed = Double.parseDouble(cleanString);
                            String formatted = CurrencyUtil.formatNumberOnly(parsed);
                            current = formatted;
                            binding.inputAmount.setText(formatted);
                            binding.inputAmount.setSelection(formatted.length());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    } else {
                        current = "";
                    }

                    binding.inputAmount.addTextChangedListener(this);
                }
            }
        });
    }

    private void setupDatePicker() {
        binding.inputDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (!selectedDateIso.isEmpty()) {
                calendar.setTime(DateTimeUtil.parseIsoDate(selectedDateIso));
            }
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        selectedDateIso = DateTimeUtil.formatDisplayDateToIso(selectedYear, selectedMonth, selectedDay);
                        binding.inputDate.setText(DateTimeUtil.formatIsoToDisplayDate(selectedDateIso));
                    }, year, month, day);
            datePickerDialog.show();
        });
    }

    private void setupTypeToggle() {
        // Default check Expense & Dynamic Tint
        binding.toggleTransactionType.check(R.id.btn_type_expense);
        binding.btnSaveTransaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorExpense)));
        
        binding.toggleTransactionType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_expense) {
                    selectedType = "expense";
                    binding.btnSaveTransaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorExpense)));
                } else if (checkedId == R.id.btn_type_income) {
                    selectedType = "income";
                    binding.btnSaveTransaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorIncome)));
                }
                // Reload categories filtering by chosen type
                loadCategoriesForType(selectedType, -1);
            }
        });
    }

    private void setupCategoryDropdown() {
        categorySpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.inputCategory.setAdapter(categorySpinnerAdapter);

        binding.inputCategory.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < categoriesList.size()) {
                selectedCategoryId = categoriesList.get(position).getId();
            }
        });

        // "+ Kategori baru" shortcut click listener as per PRD 7.2
        binding.btnNewCategoryShortcut.setOnClickListener(v -> {
            startActivity(new Intent(this, CategoryActivity.class));
        });
    }

    private void setupReceiptPickers() {
        // Gallery Pick Contract
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        displayReceiptPreview(uri);
                    }
                }
        );

        // Camera Pick Contract
        cameraCaptureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && cameraImageUri != null) {
                        displayReceiptPreview(cameraImageUri);
                    }
                }
        );

        binding.btnPickGallery.setOnClickListener(v -> galleryPickerLauncher.launch("image/*"));

        binding.btnPickCamera.setOnClickListener(v -> {
            File photoFile = new File(getCacheDir(), "receipt_camera_" + System.currentTimeMillis() + ".jpg");
            try {
                if (photoFile.exists()) {
                    photoFile.delete();
                }
                photoFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            cameraImageUri = FileProvider.getUriForFile(this, "com.aruskas.app.fileprovider", photoFile);
            cameraCaptureLauncher.launch(cameraImageUri);
        });

        binding.btnRemoveReceipt.setOnClickListener(v -> removeReceiptPreview());
    }

    private void displayReceiptPreview(Uri uri) {
        selectedImageUri = uri;
        existingReceiptUrl = null; // Clear existing if we set a new one
        binding.layoutReceiptPreview.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).into(binding.imgReceiptPreview);
    }

    private void removeReceiptPreview() {
        selectedImageUri = null;
        existingReceiptUrl = null;
        binding.layoutReceiptPreview.setVisibility(View.GONE);
        binding.imgReceiptPreview.setImageDrawable(null);
    }

    private void handlePrefillsFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        // Prefill title (maps to note)
        if (intent.hasExtra("prefill_title")) {
            binding.inputNote.setText(intent.getStringExtra("prefill_title"));
        }

        // Prefill amount
        if (intent.hasExtra("prefill_amount")) {
            double prefillAmount = intent.getDoubleExtra("prefill_amount", 0);
            if (prefillAmount > 0) {
                binding.inputAmount.setText(CurrencyUtil.formatNumberOnly(prefillAmount));
            }
        }

        // Prefill category id
        if (intent.hasExtra("prefill_category_id")) {
            selectedCategoryId = intent.getIntExtra("prefill_category_id", -1);
        }
    }

    private void loadCategoriesForType(String type, int preselectedId) {
        ApiClient.getApiService().getCategories(type).enqueue(new Callback<ApiResponse<List<Category>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Response<ApiResponse<List<Category>>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Category>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        categoriesList.clear();
                        categoriesList.addAll(apiResponse.getData());

                        List<String> names = new ArrayList<>();
                        int selectionIndex = -1;

                        for (int i = 0; i < categoriesList.size(); i++) {
                            Category cat = categoriesList.get(i);
                            names.add(cat.getName());
                            if (cat.getId() == preselectedId) {
                                selectionIndex = i;
                            }
                        }

                        categorySpinnerAdapter.clear();
                        categorySpinnerAdapter.addAll(names);
                        categorySpinnerAdapter.notifyDataSetChanged();

                        // Set selection text and Id
                        if (selectionIndex != -1) {
                            binding.inputCategory.setText(categoriesList.get(selectionIndex).getName(), false);
                            selectedCategoryId = preselectedId;
                        } else if (!categoriesList.isEmpty() && preselectedId == -1) {
                            // Leave it empty or select first as default
                            binding.inputCategory.setText("", false);
                            selectedCategoryId = -1;
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Throwable t) {
                // Ignore silent failure
            }
        });
    }

    private void loadTransactionDetails() {
        setLoading(true);
        ApiClient.getApiService().getTransactionById(transactionId).enqueue(new Callback<ApiResponse<Transaction>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Transaction>> call, @NonNull Response<ApiResponse<Transaction>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Transaction> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        bindTransactionData(apiResponse.getData());
                    } else {
                        Toast.makeText(TransactionEditorActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                } else {
                    Toast.makeText(TransactionEditorActivity.this, "Gagal memuat transaksi", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Transaction>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(TransactionEditorActivity.this, "Koneksi gagal. Tidak dapat memuat transaksi.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void bindTransactionData(Transaction transaction) {
        selectedType = transaction.getType();
        selectedDateIso = transaction.getTransactionDate();
        selectedCategoryId = transaction.getCategoryId();
        existingReceiptUrl = transaction.getReceiptUrl();

        // 1. Bind Type & Save Button Dynamic Tint
        if ("expense".equals(selectedType)) {
            binding.toggleTransactionType.check(R.id.btn_type_expense);
            binding.btnSaveTransaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorExpense)));
        } else {
            binding.toggleTransactionType.check(R.id.btn_type_income);
            binding.btnSaveTransaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.colorIncome)));
        }

        // 2. Bind Amount
        binding.inputAmount.setText(CurrencyUtil.formatNumberOnly(transaction.getAmount()));

        // 3. Bind Note
        binding.inputNote.setText(transaction.getNote());

        // 4. Bind Date
        binding.inputDate.setText(DateTimeUtil.formatIsoToDisplayDate(selectedDateIso));

        // 5. Load Categories and preselect the category id
        loadCategoriesForType(selectedType, selectedCategoryId);

        // 6. Bind Receipt Preview
        if (existingReceiptUrl != null && !existingReceiptUrl.trim().isEmpty()) {
            binding.layoutReceiptPreview.setVisibility(View.VISIBLE);
            Glide.with(this).load(existingReceiptUrl).into(binding.imgReceiptPreview);
        } else {
            binding.layoutReceiptPreview.setVisibility(View.GONE);
        }

        // Show Delete option
        binding.btnDeleteTransaction.setVisibility(View.VISIBLE);
    }

    private void saveTransaction() {
        String amountStr = binding.inputAmount.getText().toString().trim();
        double amountVal = CurrencyUtil.parseRupiah(amountStr);
        String noteVal = binding.inputNote.getText().toString().trim();

        // 1. Validations via ValidationUtil
        String typeError = ValidationUtil.validateTransactionType(selectedType);
        String amountError = ValidationUtil.validateTransactionAmount(amountVal);
        String categoryError = ValidationUtil.validateTransactionCategory(selectedCategoryId);
        String dateError = ValidationUtil.validateTransactionDate(selectedDateIso);
        String noteError = ValidationUtil.validateTransactionNote(noteVal);

        if (amountError != null) {
            binding.inputAmountLayout.setError(amountError);
            return;
        } else {
            binding.inputAmountLayout.setError(null);
        }

        if (categoryError != null) {
            binding.inputCategoryLayout.setError(categoryError);
            return;
        } else {
            binding.inputCategoryLayout.setError(null);
        }

        if (dateError != null) {
            binding.inputDateLayout.setError(dateError);
            return;
        } else {
            binding.inputDateLayout.setError(null);
        }

        if (noteError != null) {
            binding.inputNoteLayout.setError(noteError);
            return;
        } else {
            binding.inputNoteLayout.setError(null);
        }

        if (typeError != null) {
            Toast.makeText(this, typeError, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Check if there is a new image attached
        MultipartBody.Part receiptFilePart = null;
        if (selectedImageUri != null) {
            File compressedFile = ImageUtil.compressImage(this, selectedImageUri, "receipt_temp.jpg");
            if (compressedFile != null) {
                // Verify compressed file is under 5MB as required by PRD 8.1.6
                String sizeError = ValidationUtil.validateReceiptSize(compressedFile.length());
                if (sizeError != null) {
                    setLoading(false);
                    Toast.makeText(this, sizeError, Toast.LENGTH_LONG).show();
                    return;
                }

                RequestBody requestFile = RequestBody.create(compressedFile, MediaType.parse("image/*"));
                receiptFilePart = MultipartBody.Part.createFormData("receipt", compressedFile.getName(), requestFile);
            }
        }

        // Convert string parts to RequestBody for Multipart
        RequestBody typeBody = RequestBody.create(selectedType, MediaType.parse("text/plain"));
        RequestBody amountBody = RequestBody.create(String.valueOf(amountVal), MediaType.parse("text/plain"));
        RequestBody categoryBody = RequestBody.create(String.valueOf(selectedCategoryId), MediaType.parse("text/plain"));
        RequestBody noteBody = RequestBody.create(noteVal, MediaType.parse("text/plain"));
        RequestBody dateBody = RequestBody.create(selectedDateIso, MediaType.parse("text/plain"));

        if (isEditMode) {
            // Edit Mode Save
            if (receiptFilePart != null) {
                // 1. Multipart Update with receipt
                ApiClient.getApiService().updateTransactionMultipart(
                        transactionId, typeBody, amountBody, categoryBody, noteBody, dateBody, receiptFilePart
                ).enqueue(saveCallback);
            } else {
                // 2. JSON Update if no new receipt is uploaded (PRD 6.2 Contract)
                Transaction updateObj = new Transaction();
                updateObj.setId(transactionId);
                updateObj.setType(selectedType);
                updateObj.setAmount(amountVal);
                updateObj.setCategoryId(selectedCategoryId);
                updateObj.setNote(noteVal);
                updateObj.setTransactionDate(selectedDateIso);
                updateObj.setReceiptUrl(existingReceiptUrl); // retain existing image if any

                ApiClient.getApiService().updateTransactionJson(transactionId, updateObj).enqueue(saveCallback);
            }
        } else {
            // Create Mode Save
            if (receiptFilePart != null) {
                ApiClient.getApiService().createTransactionMultipart(
                        typeBody, amountBody, categoryBody, noteBody, dateBody, receiptFilePart
                ).enqueue(saveCallback);
            } else {
                ApiClient.getApiService().createTransactionMultipartWithoutReceipt(
                        typeBody, amountBody, categoryBody, noteBody, dateBody
                ).enqueue(saveCallback);
            }
        }
    }

    private final Callback<ApiResponse<Transaction>> saveCallback = new Callback<ApiResponse<Transaction>>() {
        @Override
        public void onResponse(@NonNull Call<ApiResponse<Transaction>> call, @NonNull Response<ApiResponse<Transaction>> response) {
            if (isFinishing() || isDestroyed()) return;
            setLoading(false);

            if (response.isSuccessful() && response.body() != null) {
                ApiResponse<Transaction> apiResponse = response.body();
                if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                    Transaction savedTx = apiResponse.getData();
                    Toast.makeText(TransactionEditorActivity.this, "Transaksi berhasil disimpan", Toast.LENGTH_SHORT).show();

                    // Check budget alert for expense transactions
                    if ("expense".equals(savedTx.getType())) {
                        checkBudgetAlertAfterSave();
                    }

                    // Show balance change notification
                    fetchSummaryAndShowNotification(savedTx.getType(), savedTx.getAmount(), savedTx.getTransactionDate());

                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(TransactionEditorActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(TransactionEditorActivity.this, "Gagal menyimpan transaksi (" + response.code() + ")", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onFailure(@NonNull Call<ApiResponse<Transaction>> call, @NonNull Throwable t) {
            if (isFinishing() || isDestroyed()) return;
            setLoading(false);
            Toast.makeText(TransactionEditorActivity.this, "Koneksi gagal. Transaksi tidak tersimpan.", Toast.LENGTH_LONG).show();
        }
    };

    private void fetchSummaryAndShowNotification(String type, double amount, String dateIso) {
        String month = dateIso != null && dateIso.length() >= 7 ? dateIso.substring(0, 7) : DateTimeUtil.getCurrentMonthIso();
        ApiClient.getApiService().getTransactionSummary(month).enqueue(new Callback<ApiResponse<com.aruskas.app.model.FinanceSummary>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<com.aruskas.app.model.FinanceSummary>> call, 
                                   @NonNull Response<ApiResponse<com.aruskas.app.model.FinanceSummary>> response) {
                double currentBalance = 0;
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    currentBalance = response.body().getData().getBalance();
                }
                com.aruskas.app.notification.NotificationHelper.showTransactionNotification(
                        getApplicationContext(), type, amount, currentBalance
                );
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<com.aruskas.app.model.FinanceSummary>> call, @NonNull Throwable t) {
                com.aruskas.app.notification.NotificationHelper.showTransactionNotification(
                        getApplicationContext(), type, amount, 0
                );
            }
        });
    }

    /**
     * After saving an expense transaction, check if the associated category's budget
     * has been approached or exceeded, and fire a local push notification alert.
     */
    private void checkBudgetAlertAfterSave() {
        // Get month from selected date (format: yyyy-MM)
        String month = selectedDateIso.length() >= 7 ? selectedDateIso.substring(0, 7) : null;
        if (month == null) return;

        ApiClient.getApiService().getBudgets(month).enqueue(new Callback<ApiResponse<java.util.List<com.aruskas.app.model.Budget>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<java.util.List<com.aruskas.app.model.Budget>>> call,
                                   @NonNull Response<ApiResponse<java.util.List<com.aruskas.app.model.Budget>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    for (com.aruskas.app.model.Budget budget : response.body().getData()) {
                        if (budget.getCategoryId() == selectedCategoryId && budget.getLimitAmount() > 0) {
                            String categoryName = budget.getCategory() != null ? budget.getCategory().getName() : "Kategori";
                            com.aruskas.app.notification.BudgetAlertHelper.checkAndNotify(
                                    getApplicationContext(),
                                    budget.getId(),
                                    categoryName,
                                    budget.getSpentAmount(),
                                    budget.getLimitAmount()
                            );
                            break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<java.util.List<com.aruskas.app.model.Budget>>> call, @NonNull Throwable t) {
                // Silently ignore - budget alert is a best-effort feature
            }
        });
    }

    private void confirmDeleteTransaction() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Transaksi")
                .setMessage("Apakah Anda yakin ingin menghapus transaksi ini?")
                .setPositiveButton("Hapus", (dialog, which) -> deleteTransaction())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void deleteTransaction() {
        setLoading(true);
        ApiClient.getApiService().deleteTransaction(transactionId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        Toast.makeText(TransactionEditorActivity.this, "Transaksi berhasil dihapus", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(TransactionEditorActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(TransactionEditorActivity.this, "Gagal menghapus transaksi", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(TransactionEditorActivity.this, "Koneksi gagal. Transaksi tidak terhapus.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}

package com.aruskas.app.activity;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aruskas.app.R;
import com.aruskas.app.adapter.CategoryAdapter;
import com.aruskas.app.databinding.ActivityCategoryBinding;
import com.aruskas.app.databinding.DialogAddEditCategoryBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.Category;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.ValidationUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CategoryActivity extends AppCompatActivity {

    private ActivityCategoryBinding binding;
    private CategoryAdapter adapter;

    // Form selection states inside Bottom Sheet Dialog
    private String selectedIcon = "other";
    private String selectedColor = "#6C5CE7"; // Default brand color
    private String selectedType = "expense";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCategoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupRecyclerView();
        setupTabLayout();
        setupSwipeRefresh();
        setupFAB();

        loadCategories();
    }

    private void setupToolbar() {
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter(this::showAddEditDialog);
        binding.recyclerCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCategories.setAdapter(adapter);
    }

    private void setupTabLayout() {
        binding.tabLayoutType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedType = tab.getPosition() == 0 ? "expense" : "income";
                loadCategories();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        binding.swipeRefresh.setOnRefreshListener(this::loadCategories);
    }

    private void setupFAB() {
        binding.fabAddCategory.setOnClickListener(v -> showAddEditDialog(null));
    }

    private String getSelectedTypeFromTab() {
        return binding.tabLayoutType.getSelectedTabPosition() == 0 ? "expense" : "income";
    }

    private void loadCategories() {
        adapter.showLoading();
        hideOverlays();

        String type = getSelectedTypeFromTab();

        ApiClient.getApiService().getCategories(type).enqueue(new Callback<ApiResponse<List<Category>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Response<ApiResponse<List<Category>>> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Category>> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        List<Category> categories = apiResponse.getData();
                        if (categories == null || categories.isEmpty()) {
                            showEmptyState();
                        } else {
                            adapter.setCategories(categories);
                        }
                    } else {
                        showErrorState(apiResponse.getMessage());
                    }
                } else {
                    handleErrorResponse(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.swipeRefresh.setRefreshing(false);
                showErrorState("Gagal menghubungi server. Periksa koneksi internet Anda.");
            }
        });
    }

    private void handleErrorResponse(Response<?> response) {
        String errorMsg = "Terjadi kesalahan sistem (" + response.code() + ")";
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                // Simple attempt to parse error body if formatted as ApiResponse JSON
                if (errorBodyStr.contains("\"message\"")) {
                    errorMsg = errorBodyStr.substring(errorBodyStr.indexOf("\"message\"") + 10);
                    errorMsg = errorMsg.substring(1, errorMsg.indexOf("\""));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        showErrorState(errorMsg);
    }

    private void hideOverlays() {
        binding.recyclerCategories.setVisibility(View.VISIBLE);
        binding.viewEmptyState.layoutEmpty.setVisibility(View.GONE);
        binding.viewErrorState.layoutError.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        binding.recyclerCategories.setVisibility(View.GONE);
        binding.viewEmptyState.layoutEmpty.setVisibility(View.VISIBLE);
        binding.viewErrorState.layoutError.setVisibility(View.GONE);

        // Customize empty state texts
        binding.viewEmptyState.textEmptyTitle.setText("Belum Ada Kategori");
        binding.viewEmptyState.textEmptySubtitle.setText("Kategori kustom diperlukan untuk melacak transaksi Anda secara terperinci.");
        binding.viewEmptyState.btnEmptyAction.setText("Buat Kategori Pertama");
        binding.viewEmptyState.btnEmptyAction.setOnClickListener(v -> showAddEditDialog(null));
    }

    private void showErrorState(String message) {
        binding.recyclerCategories.setVisibility(View.GONE);
        binding.viewEmptyState.layoutEmpty.setVisibility(View.GONE);
        binding.viewErrorState.layoutError.setVisibility(View.VISIBLE);

        binding.viewErrorState.textErrorMessage.setText(message);
        binding.viewErrorState.btnErrorRetry.setOnClickListener(v -> loadCategories());
    }

    /**
     * Show Category Bottom Sheet Form for creation (category = null) or edit (category != null)
     */
    private void showAddEditDialog(Category category) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_ArusKas_BottomSheetDialog);
        DialogAddEditCategoryBinding dialogBinding = DialogAddEditCategoryBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());

        boolean isEditMode = category != null;

        // Prefill form states
        if (isEditMode) {
            dialogBinding.textDialogTitle.setText("Edit Kategori");
            dialogBinding.inputCategoryName.setText(category.getName());
            selectedIcon = category.getIcon();
            selectedColor = category.getColor();
            selectedType = category.getType();
            dialogBinding.btnDeleteCategory.setVisibility(View.VISIBLE);
        } else {
            dialogBinding.textDialogTitle.setText("Kategori Baru");
            dialogBinding.inputCategoryName.setText("");
            selectedIcon = "other";
            selectedColor = "#6C5CE7"; // brand primary default
            selectedType = getSelectedTypeFromTab();
            dialogBinding.btnDeleteCategory.setVisibility(View.GONE);
        }

        // Toggle Buttons configuration & Save Button Dynamic Tinting
        if ("expense".equals(selectedType)) {
            dialogBinding.toggleCategoryType.check(R.id.btn_type_expense);
            dialogBinding.btnSaveCategory.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorPrimary)));
        } else {
            dialogBinding.toggleCategoryType.check(R.id.btn_type_income);
            dialogBinding.btnSaveCategory.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorIncome)));
        }
 
        dialogBinding.toggleCategoryType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_type_expense) {
                    selectedType = "expense";
                    dialogBinding.btnSaveCategory.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorPrimary)));
                } else if (checkedId == R.id.btn_type_income) {
                    selectedType = "income";
                    dialogBinding.btnSaveCategory.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.colorIncome)));
                }
            }
        });

        // Set up dynamic layout lists for Icon Picker & Color Swatches
        setupIconPicker(dialogBinding);
        setupColorPicker(dialogBinding);

        // Click delete button inside form
        dialogBinding.btnDeleteCategory.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Hapus Kategori")
                    .setMessage("Apakah Anda yakin ingin menghapus kategori '" + category.getName() + "'?\n\nKategori yang sedang digunakan dalam data transaksi mungkin tidak bisa dihapus.")
                    .setPositiveButton("Hapus", (dialogInterface, i) -> {
                        dialog.dismiss();
                        deleteCategory(category.getId());
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });

        // Click save button inside form
        dialogBinding.btnSaveCategory.setOnClickListener(v -> {
            String name = dialogBinding.inputCategoryName.getText().toString().trim();
            
            // Perform input validations via ValidationUtil
            String nameError = ValidationUtil.validateCategoryName(name);
            String typeError = ValidationUtil.validateCategoryType(selectedType);
            String iconError = ValidationUtil.validateCategoryIcon(selectedIcon);
            String colorError = ValidationUtil.validateCategoryColor(selectedColor);

            if (nameError != null) {
                dialogBinding.inputNameLayout.setError(nameError);
                return;
            } else {
                dialogBinding.inputNameLayout.setError(null);
            }

            if (typeError != null || iconError != null || colorError != null) {
                Toast.makeText(this, typeError != null ? typeError : (iconError != null ? iconError : colorError), Toast.LENGTH_SHORT).show();
                return;
            }

            // Construct entity
            Category saveObj = new Category();
            saveObj.setName(name);
            saveObj.setType(selectedType);
            saveObj.setIcon(selectedIcon);
            saveObj.setColor(selectedColor);

            dialogBinding.btnSaveCategory.setEnabled(false);

            if (isEditMode) {
                saveObj.setId(category.getId());
                updateCategory(saveObj, dialog);
            } else {
                createCategory(saveObj, dialog);
            }
        });

        // Soft keyboard adjust resize
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    private void setupIconPicker(DialogAddEditCategoryBinding dialogBinding) {
        String[] iconKeys = getResources().getStringArray(R.array.category_icons);
        LinearLayout iconContainer = dialogBinding.layoutIconPicker;
        iconContainer.removeAllViews();

        List<FrameLayout> iconViews = new ArrayList<>();

        for (String key : iconKeys) {
            FrameLayout selectionFrame = new FrameLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            selectionFrame.setLayoutParams(params);

            // Selection outline ring (index 0 child)
            View ringView = new View(this);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dpToPx(46), dpToPx(46));
            ringParams.gravity = android.view.Gravity.CENTER;
            ringView.setLayoutParams(ringParams);
            ringView.setBackgroundResource(R.drawable.bg_selection_ring);
            ringView.setVisibility(key.equals(selectedIcon) ? View.VISIBLE : View.INVISIBLE);

            // Circular badge color background (index 1 child)
            FrameLayout badgeFrame = new FrameLayout(this);
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dpToPx(38), dpToPx(38));
            badgeParams.gravity = android.view.Gravity.CENTER;
            badgeFrame.setLayoutParams(badgeParams);
            badgeFrame.setBackgroundResource(R.drawable.bg_badge_circle);
            badgeFrame.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(selectedColor)));

            // Icon SVG image (inside badge)
            ImageView iconImage = new ImageView(this);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dpToPx(20), dpToPx(20));
            iconParams.gravity = android.view.Gravity.CENTER;
            iconImage.setLayoutParams(iconParams);
            iconImage.setImageResource(CategoryAdapter.getIconDrawableResource(key));

            badgeFrame.addView(iconImage);
            selectionFrame.addView(ringView);
            selectionFrame.addView(badgeFrame);

            selectionFrame.setOnClickListener(v -> {
                for (FrameLayout frame : iconViews) {
                    frame.getChildAt(0).setVisibility(View.INVISIBLE);
                }
                ringView.setVisibility(View.VISIBLE);
                selectedIcon = key;
            });

            iconContainer.addView(selectionFrame);
            iconViews.add(selectionFrame);
        }
    }

    private void setupColorPicker(DialogAddEditCategoryBinding dialogBinding) {
        String[] colors = getResources().getStringArray(R.array.category_swatches);
        LinearLayout colorContainer = dialogBinding.layoutColorPicker;
        colorContainer.removeAllViews();

        List<FrameLayout> colorViews = new ArrayList<>();

        for (String hex : colors) {
            FrameLayout selectionFrame = new FrameLayout(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            selectionFrame.setLayoutParams(params);

            // Selection outline ring (index 0 child)
            View ringView = new View(this);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dpToPx(46), dpToPx(46));
            ringParams.gravity = android.view.Gravity.CENTER;
            ringView.setLayoutParams(ringParams);
            ringView.setBackgroundResource(R.drawable.bg_selection_ring);
            ringView.setVisibility(hex.equalsIgnoreCase(selectedColor) ? View.VISIBLE : View.INVISIBLE);

            // Color circle shape (index 1 child)
            View colorCircle = new View(this);
            FrameLayout.LayoutParams circleParams = new FrameLayout.LayoutParams(dpToPx(38), dpToPx(38));
            circleParams.gravity = android.view.Gravity.CENTER;
            colorCircle.setLayoutParams(circleParams);
            colorCircle.setBackgroundResource(R.drawable.bg_badge_circle);
            colorCircle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));

            selectionFrame.addView(ringView);
            selectionFrame.addView(colorCircle);

            selectionFrame.setOnClickListener(v -> {
                for (FrameLayout frame : colorViews) {
                    frame.getChildAt(0).setVisibility(View.INVISIBLE);
                }
                ringView.setVisibility(View.VISIBLE);
                selectedColor = hex;

                // PREMIUM micro-interaction: update the icon picker circle backgrounds to match the selected color!
                LinearLayout iconContainer = dialogBinding.layoutIconPicker;
                for (int i = 0; i < iconContainer.getChildCount(); i++) {
                    FrameLayout iconFrame = (FrameLayout) iconContainer.getChildAt(i);
                    FrameLayout badge = (FrameLayout) iconFrame.getChildAt(1);
                    badge.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(hex)));
                }
            });

            colorContainer.addView(selectionFrame);
            colorViews.add(selectionFrame);
        }
    }

    private void createCategory(Category category, BottomSheetDialog dialog) {
        ApiClient.getApiService().createCategory(category).enqueue(new Callback<ApiResponse<Category>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Category>> call, @NonNull Response<ApiResponse<Category>> response) {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Category> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        Toast.makeText(CategoryActivity.this, "Kategori berhasil dibuat", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    } else {
                        Toast.makeText(CategoryActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(CategoryActivity.this, "Gagal membuat kategori (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Category>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                Toast.makeText(CategoryActivity.this, "Koneksi gagal. Kategori tidak tersimpan.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateCategory(Category category, BottomSheetDialog dialog) {
        ApiClient.getApiService().updateCategory(category.getId(), category).enqueue(new Callback<ApiResponse<Category>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Category>> call, @NonNull Response<ApiResponse<Category>> response) {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Category> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        Toast.makeText(CategoryActivity.this, "Kategori berhasil diperbarui", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    } else {
                        Toast.makeText(CategoryActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(CategoryActivity.this, "Gagal memperbarui kategori (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Category>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                dialog.dismiss();
                Toast.makeText(CategoryActivity.this, "Koneksi gagal. Perubahan tidak tersimpan.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deleteCategory(int categoryId) {
        ApiClient.getApiService().deleteCategory(categoryId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        Toast.makeText(CategoryActivity.this, "Kategori berhasil dihapus", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    } else {
                        // Surface backend error (e.g. category in use) as requested in PRD 7.4
                        Toast.makeText(CategoryActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(CategoryActivity.this, "Gagal menghapus kategori (" + response.code() + ")", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(CategoryActivity.this, "Koneksi gagal. Kategori tidak terhapus.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}

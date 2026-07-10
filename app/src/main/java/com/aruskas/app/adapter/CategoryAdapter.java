package com.aruskas.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aruskas.app.R;
import com.aruskas.app.databinding.ItemCategoryBinding;
import com.aruskas.app.databinding.ItemCategorySkeletonBinding;
import com.aruskas.app.model.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_SKELETON = 1;

    private final List<Category> categoryList = new ArrayList<>();
    private final OnCategoryClickListener listener;
    private boolean isLoading = true;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        this.categoryList.clear();
        if (categories != null) {
            this.categoryList.addAll(categories);
        }
        this.isLoading = false;
        notifyDataSetChanged();
    }

    public void showLoading() {
        this.isLoading = true;
        this.categoryList.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isLoading ? VIEW_TYPE_SKELETON : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SKELETON) {
            ItemCategorySkeletonBinding skeletonBinding = ItemCategorySkeletonBinding.inflate(inflater, parent, false);
            return new SkeletonViewHolder(skeletonBinding);
        } else {
            ItemCategoryBinding itemBinding = ItemCategoryBinding.inflate(inflater, parent, false);
            return new CategoryViewHolder(itemBinding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CategoryViewHolder) {
            Category category = categoryList.get(position);
            ((CategoryViewHolder) holder).bind(category, listener);
        }
    }

    @Override
    public int getItemCount() {
        // Return a fixed number of skeleton rows when loading
        return isLoading ? 6 : categoryList.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryBinding binding;

        public CategoryViewHolder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Category category, OnCategoryClickListener listener) {
            Context context = itemView.getContext();
            binding.textCategoryName.setText(category.getName());

            // Bind semantic colors for Type label
            boolean isExpense = "expense".equals(category.getType());
            binding.textCategoryType.setText(isExpense ? "Pengeluaran" : "Pemasukan");
            binding.textCategoryType.setTextColor(context.getColor(isExpense ? R.color.colorExpense : R.color.colorIncome));

            // Parse hex color with white icon fallback if color code is corrupted
            int colorVal;
            try {
                colorVal = Color.parseColor(category.getColor());
            } catch (Exception e) {
                colorVal = context.getColor(R.color.colorPrimary);
            }
            binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(colorVal));

            // Map string icon from database to resource ID
            binding.imgCategoryIcon.setImageResource(getIconDrawableResource(category.getIcon()));

            // Setup click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
            binding.imgEditButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }
    }

    public static class SkeletonViewHolder extends RecyclerView.ViewHolder {
        public SkeletonViewHolder(ItemCategorySkeletonBinding binding) {
            super(binding.getRoot());
        }
    }

    /**
     * Helper to map raw icon string values from API payload to app drawable resources.
     * Accessible by other packages for consistent icon mapping throughout the app.
     */
    public static int getIconDrawableResource(String iconKey) {
        if (iconKey == null) return R.drawable.ic_category_other;
        switch (iconKey.toLowerCase().trim()) {
            case "food":
                return R.drawable.ic_category_food;
            case "transport":
                return R.drawable.ic_category_transport;
            case "shopping":
                return R.drawable.ic_category_shopping;
            case "bills":
                return R.drawable.ic_category_bills;
            case "entertainment":
                return R.drawable.ic_category_entertainment;
            case "health":
                return R.drawable.ic_category_health;
            case "salary":
                return R.drawable.ic_category_salary;
            case "bonus":
                return R.drawable.ic_category_bonus;
            case "gift":
                return R.drawable.ic_category_gift;
            case "other":
            default:
                return R.drawable.ic_category_other;
        }
    }
}

package com.aruskas.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aruskas.app.R;
import com.aruskas.app.databinding.ItemBudgetBinding;
import com.aruskas.app.model.Budget;
import com.aruskas.app.model.Category;
import com.aruskas.app.util.CurrencyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private final List<Budget> budgetList = new ArrayList<>();
    private final OnBudgetClickListener listener;

    public interface OnBudgetClickListener {
        void onBudgetClick(Budget budget);
    }

    public BudgetAdapter(OnBudgetClickListener listener) {
        this.listener = listener;
    }

    public void setBudgets(List<Budget> budgets) {
        this.budgetList.clear();
        if (budgets != null) {
            this.budgetList.addAll(budgets);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemBudgetBinding binding = ItemBudgetBinding.inflate(inflater, parent, false);
        return new BudgetViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        Budget budget = budgetList.get(position);
        holder.bind(budget, listener);
    }

    @Override
    public int getItemCount() {
        return budgetList.size();
    }

    public static class BudgetViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetBinding binding;

        public BudgetViewHolder(ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Budget budget, OnBudgetClickListener listener) {
            Context context = itemView.getContext();

            Category category = budget.getCategory();
            if (category != null) {
                binding.textCategoryName.setText(category.getName());
                
                int colorVal;
                try {
                    colorVal = Color.parseColor(category.getColor());
                } catch (Exception e) {
                    colorVal = context.getColor(R.color.colorPrimary);
                }
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(colorVal));
                binding.imgCategoryIcon.setImageResource(CategoryAdapter.getIconDrawableResource(category.getIcon()));
            } else {
                binding.textCategoryName.setText("Kategori");
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorSkeleton)));
                binding.imgCategoryIcon.setImageResource(R.drawable.ic_category_other);
            }

            double limit = budget.getLimitAmount();
            double spent = budget.getSpentAmount();

            String formattedSpent = CurrencyUtil.formatRupiah(spent);
            String formattedLimit = CurrencyUtil.formatRupiah(limit);
            
            // Format labels based on budget state
            if (budget.getId() == 0) {
                // No budget set yet for this category
                binding.textBudgetAmounts.setText(String.format("Terpakai %s (Batas belum diatur)", formattedSpent));
                binding.textBudgetPercentage.setText("0%");
                binding.progressBudget.setProgress(0);
                binding.progressBudget.setIndicatorColor(context.getColor(R.color.colorSkeleton));
            } else {
                binding.textBudgetAmounts.setText(String.format("Terpakai %s dari %s", formattedSpent, formattedLimit));
                
                int percentage = limit > 0 ? (int) ((spent / limit) * 100) : 0;
                binding.textBudgetPercentage.setText(String.format(Locale.US, "%d%%", percentage));

                // Caps visual progress at 100 for bar rendering
                binding.progressBudget.setProgress(Math.min(percentage, 100));

                // Tint indicator based on threshold logic:
                // Green if <80%, Orange if >=80%, Red if >=100%
                int warningColor;
                if (spent >= limit) {
                    warningColor = context.getColor(R.color.colorExpense); // Red
                } else if (spent >= 0.8 * limit) {
                    warningColor = Color.parseColor("#F39C12"); // Orange / Warning
                } else {
                    warningColor = context.getColor(R.color.colorIncome); // Green
                }
                binding.progressBudget.setIndicatorColor(warningColor);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onBudgetClick(budget);
                }
            });
        }
    }
}

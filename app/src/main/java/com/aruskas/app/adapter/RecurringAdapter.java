package com.aruskas.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aruskas.app.R;
import com.aruskas.app.databinding.ItemRecurringTransactionBinding;
import com.aruskas.app.model.Category;
import com.aruskas.app.model.RecurringTransaction;
import com.aruskas.app.util.CurrencyUtil;

import java.util.ArrayList;
import java.util.List;

public class RecurringAdapter extends RecyclerView.Adapter<RecurringAdapter.RecurringViewHolder> {

    private final List<RecurringTransaction> recurringList = new ArrayList<>();
    private final OnRecurringClickListener clickListener;
    private final OnReminderToggleListener toggleListener;

    public interface OnRecurringClickListener {
        void onRecurringClick(RecurringTransaction recurring);
    }

    public interface OnReminderToggleListener {
        void onReminderToggle(RecurringTransaction recurring, boolean isChecked);
    }

    public RecurringAdapter(OnRecurringClickListener clickListener, OnReminderToggleListener toggleListener) {
        this.clickListener = clickListener;
        this.toggleListener = toggleListener;
    }

    public void setRecurrings(List<RecurringTransaction> recurrings) {
        this.recurringList.clear();
        if (recurrings != null) {
            this.recurringList.addAll(recurrings);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecurringViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemRecurringTransactionBinding binding = ItemRecurringTransactionBinding.inflate(inflater, parent, false);
        return new RecurringViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecurringViewHolder holder, int position) {
        RecurringTransaction recurring = recurringList.get(position);
        holder.bind(recurring, clickListener, toggleListener);
    }

    @Override
    public int getItemCount() {
        return recurringList.size();
    }

    public static class RecurringViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecurringTransactionBinding binding;

        public RecurringViewHolder(ItemRecurringTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(RecurringTransaction recurring, OnRecurringClickListener clickListener, OnReminderToggleListener toggleListener) {
            Context context = itemView.getContext();

            binding.textRecurringTitle.setText(recurring.getTitle());
            binding.textDueDay.setText(String.format("Jatuh tempo setiap tanggal %d", recurring.getDueDay()));

            // Format Amount & dynamic color based on type
            boolean isIncome = "income".equals(recurring.getType());
            String formattedAmount = CurrencyUtil.formatRupiah(recurring.getAmount());
            if (isIncome) {
                binding.textRecurringAmount.setText("+ " + formattedAmount);
                binding.textRecurringAmount.setTextColor(context.getColor(R.color.colorIncome));
            } else {
                binding.textRecurringAmount.setText("- " + formattedAmount);
                binding.textRecurringAmount.setTextColor(context.getColor(R.color.colorExpense));
            }

            // Category configuration
            Category category = recurring.getCategory();
            if (category != null) {
                int colorVal;
                try {
                    colorVal = Color.parseColor(category.getColor());
                } catch (Exception e) {
                    colorVal = context.getColor(R.color.colorPrimary);
                }
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(colorVal));
                binding.imgCategoryIcon.setImageResource(CategoryAdapter.getIconDrawableResource(category.getIcon()));
            } else {
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorSkeleton)));
                binding.imgCategoryIcon.setImageResource(R.drawable.ic_category_other);
            }

            // Set checked state for switch avoiding recursive triggers
            binding.switchReminderEnabled.setOnCheckedChangeListener(null);
            binding.switchReminderEnabled.setChecked(recurring.isReminderEnabled());
            binding.switchReminderEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (toggleListener != null) {
                    toggleListener.onReminderToggle(recurring, isChecked);
                }
            });

            // Card click action
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onRecurringClick(recurring);
                }
            });
        }
    }
}

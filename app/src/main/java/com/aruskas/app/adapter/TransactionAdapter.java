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
import com.aruskas.app.databinding.ItemRecentTransactionBinding;
import com.aruskas.app.model.Category;
import com.aruskas.app.model.Transaction;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private final List<Transaction> transactionList = new ArrayList<>();
    private final OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    public TransactionAdapter(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactionList.clear();
        if (transactions != null) {
            this.transactionList.addAll(transactions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemRecentTransactionBinding binding = ItemRecentTransactionBinding.inflate(inflater, parent, false);
        return new TransactionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactionList.get(position);
        holder.bind(transaction, listener);
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemRecentTransactionBinding binding;

        public TransactionViewHolder(ItemRecentTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Transaction transaction, OnTransactionClickListener listener) {
            Context context = itemView.getContext();

            // Handle nested Category properties
            Category category = transaction.getCategory();
            if (category != null) {
                binding.textTransactionTitle.setText(category.getName());
                
                int colorVal;
                try {
                    colorVal = Color.parseColor(category.getColor());
                } catch (Exception e) {
                    colorVal = context.getColor(R.color.colorPrimary);
                }
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(colorVal));
                binding.imgCategoryIcon.setImageResource(CategoryAdapter.getIconDrawableResource(category.getIcon()));
            } else {
                binding.textTransactionTitle.setText("Transaksi");
                binding.viewBadgeBg.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorSkeleton)));
                binding.imgCategoryIcon.setImageResource(R.drawable.ic_category_other);
            }

            // Set optional note
            String note = transaction.getNote();
            if (note != null && !note.trim().isEmpty()) {
                binding.textTransactionNote.setText(note);
                binding.textTransactionNote.setVisibility(View.VISIBLE);
            } else {
                binding.textTransactionNote.setVisibility(View.GONE);
            }

            // Convert and set transaction date
            binding.textTransactionDate.setText(DateTimeUtil.formatIsoToDisplayDate(transaction.getTransactionDate()));

            // Format Amount & color code by transaction type
            boolean isIncome = "income".equals(transaction.getType());
            String formattedAmount = CurrencyUtil.formatRupiah(transaction.getAmount());
            if (isIncome) {
                binding.textTransactionAmount.setText("+ " + formattedAmount);
                binding.textTransactionAmount.setTextColor(context.getColor(R.color.colorIncome));
            } else {
                binding.textTransactionAmount.setText("- " + formattedAmount);
                binding.textTransactionAmount.setTextColor(context.getColor(R.color.colorExpense));
            }

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTransactionClick(transaction);
                }
            });
        }
    }
}

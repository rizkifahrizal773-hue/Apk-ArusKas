package com.aruskas.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aruskas.app.R;
import com.aruskas.app.databinding.ItemReportCategoryBinding;
import com.aruskas.app.model.CategoryBreakdown;
import com.aruskas.app.util.CurrencyUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportCategoryAdapter extends RecyclerView.Adapter<ReportCategoryAdapter.ReportCategoryViewHolder> {

    private final List<CategoryBreakdown> breakdownList = new ArrayList<>();

    public void setData(List<CategoryBreakdown> data) {
        this.breakdownList.clear();
        if (data != null) {
            this.breakdownList.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportCategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemReportCategoryBinding binding = ItemReportCategoryBinding.inflate(inflater, parent, false);
        return new ReportCategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportCategoryViewHolder holder, int position) {
        CategoryBreakdown item = breakdownList.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return breakdownList.size();
    }

    public static class ReportCategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemReportCategoryBinding binding;

        public ReportCategoryViewHolder(ItemReportCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(CategoryBreakdown item) {
            Context context = itemView.getContext();

            binding.textCategoryName.setText(item.getCategory());
            binding.textCategoryPercentage.setText(String.format(Locale.US, "%.1f%%", item.getPercentage()));
            binding.textCategoryAmount.setText(CurrencyUtil.formatRupiah(item.getAmount()));

            int colorVal;
            try {
                colorVal = Color.parseColor(item.getColor());
            } catch (Exception e) {
                colorVal = context.getColor(R.color.colorPrimary);
            }
            binding.viewColorIndicator.setBackgroundTintList(ColorStateList.valueOf(colorVal));
        }
    }
}

package com.aruskas.app.activity;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.aruskas.app.R;
import com.aruskas.app.adapter.ReportCategoryAdapter;
import com.aruskas.app.databinding.ActivityReportBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.CategoryBreakdown;
import com.aruskas.app.model.MonthlyTrend;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.CurrencyUtil;
import com.aruskas.app.util.DateTimeUtil;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportActivity extends AppCompatActivity {

    private ActivityReportBinding binding;
    private ReportCategoryAdapter categoryAdapter;

    // Filter states
    private String selectedMonthIso = ""; // YYYY-MM
    private String selectedType = "expense"; // default
    private int currentTab = 0; // 0 = Pie (Breakdown), 1 = Bar (Trend)

    // Chart animation state flags
    private boolean isPieAnimated = false;
    private boolean isBarAnimated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        selectedMonthIso = DateTimeUtil.getCurrentMonthIso();

        setupToolbar();
        setupTabs();
        setupFilters();
        setupRecyclerView();

        loadActiveReport();
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
                        isPieAnimated = false; // re-animate on month change
                        loadActiveReport();
                    }, year, month, 1);
            
            datePickerDialog.setTitle("Pilih Bulan");
            datePickerDialog.show();
        });
    }

    private void setupTabs() {
        binding.tabLayoutReport.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                if (currentTab == 0) {
                    binding.layoutPieChartSection.setVisibility(View.VISIBLE);
                    binding.layoutBarChartSection.setVisibility(View.GONE);
                    binding.btnMonthFilter.setVisibility(View.VISIBLE);
                } else {
                    binding.layoutPieChartSection.setVisibility(View.GONE);
                    binding.layoutBarChartSection.setVisibility(View.VISIBLE);
                    binding.btnMonthFilter.setVisibility(View.GONE); // Trend shows last 6 months cumulatively
                }
                loadActiveReport();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFilters() {
        // Toggle Pemasukan / Pengeluaran for Pie Chart
        binding.toggleReportType.check(R.id.btn_report_expense);
        binding.toggleReportType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_report_expense) {
                    selectedType = "expense";
                } else if (checkedId == R.id.btn_report_income) {
                    selectedType = "income";
                }
                isPieAnimated = false;
                loadActiveReport();
            }
        });
    }

    private void setupRecyclerView() {
        categoryAdapter = new ReportCategoryAdapter();
        binding.recyclerReportCategories.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerReportCategories.setAdapter(categoryAdapter);
    }

    private void loadActiveReport() {
        if (currentTab == 0) {
            fetchCategoryBreakdown();
        } else {
            fetchMonthlyTrend();
        }
    }

    private void fetchCategoryBreakdown() {
        setLoading(true);
        ApiClient.getApiService().getCategoryBreakdown(selectedMonthIso, selectedType).enqueue(new Callback<ApiResponse<List<CategoryBreakdown>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<CategoryBreakdown>>> call, @NonNull Response<ApiResponse<List<CategoryBreakdown>>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<CategoryBreakdown>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        List<CategoryBreakdown> list = apiResponse.getData();
                        bindCategoryBreakdown(list);
                    } else {
                        showEmptyState(true, apiResponse.getMessage());
                    }
                } else {
                    showEmptyState(true, "Gagal memuat distribusi kategori.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<CategoryBreakdown>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showEmptyState(true, "Koneksi gagal. Tidak dapat memuat laporan distribusi.");
            }
        });
    }

    private void bindCategoryBreakdown(List<CategoryBreakdown> list) {
        if (list.isEmpty()) {
            showEmptyState(true, "Belum ada transaksi " + 
                    ("expense".equals(selectedType) ? "pengeluaran" : "pemasukan") + " pada bulan ini.");
            return;
        }

        showEmptyState(false, null);
        categoryAdapter.setData(list);

        // Configure Pie Chart
        PieChart pieChart = binding.pieChartDistribution;
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setExtraOffsets(5, 5, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);

        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(58f);
        pieChart.setHoleRadius(54f);

        // Hide legend text in chart layout
        pieChart.getLegend().setEnabled(false);

        // Prepare Entries & Colors
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        double totalSum = 0.0;

        for (CategoryBreakdown item : list) {
            entries.add(new PieEntry((float) item.getPercentage(), ""));
            totalSum += item.getAmount();
            
            int cVal;
            try {
                cVal = Color.parseColor(item.getColor());
            } catch (Exception e) {
                cVal = getColor(R.color.colorPrimary);
            }
            colors.add(cVal);
        }

        // Setup center label text
        String summaryTitle = "expense".equals(selectedType) ? "Pengeluaran" : "Pemasukan";
        pieChart.setCenterText(summaryTitle + "\n" + CurrencyUtil.formatRupiah(totalSum));
        pieChart.setCenterTextSize(13f);
        pieChart.setCenterTextColor(getColor(R.color.colorTextPrimary));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(2f);
        dataSet.setSelectionShift(5f);

        // Format pie percentages in slices
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.WHITE);

        pieChart.setData(data);
        pieChart.highlightValues(null);

        if (!isPieAnimated) {
            pieChart.animateY(1000);
            isPieAnimated = true;
        } else {
            pieChart.invalidate();
        }
    }

    private void fetchMonthlyTrend() {
        setLoading(true);
        ApiClient.getApiService().getMonthlyTrend(6).enqueue(new Callback<ApiResponse<List<MonthlyTrend>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<MonthlyTrend>>> call, @NonNull Response<ApiResponse<List<MonthlyTrend>>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<MonthlyTrend>> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        bindMonthlyTrend(apiResponse.getData());
                    } else {
                        showEmptyState(true, apiResponse.getMessage());
                    }
                } else {
                    showEmptyState(true, "Gagal memuat tren grafik.");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<MonthlyTrend>>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                showEmptyState(true, "Koneksi gagal. Tidak dapat memuat laporan tren.");
            }
        });
    }

    private void bindMonthlyTrend(List<MonthlyTrend> list) {
        if (list.isEmpty()) {
            showEmptyState(true, "Belum memiliki data transaksi bulanan.");
            return;
        }

        showEmptyState(false, null);

        BarChart barChart = binding.barChartTrend;
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);

        // Customize X-Axis values
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(list.size());
        xAxis.setTextColor(getColor(R.color.colorTextSecondary));

        final List<String> monthLabels = new ArrayList<>();
        for (MonthlyTrend trend : list) {
            monthLabels.add(DateTimeUtil.formatIsoToDisplayMonth(trend.getMonth()));
        }

        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < monthLabels.size()) {
                    String fullLabel = monthLabels.get(index);
                    if (fullLabel.contains(" ")) {
                        return fullLabel.substring(0, fullLabel.indexOf(" "));
                    }
                    return fullLabel;
                }
                return "";
            }
        });

        // Customize Y-Axis
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getColor(R.color.colorBorder));
        leftAxis.setTextColor(getColor(R.color.colorTextSecondary));
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 1_000_000) {
                    return String.format(Locale.US, "Rp %.1fJt", value / 1_000_000.0);
                } else if (value >= 1_000) {
                    return String.format(Locale.US, "Rp %.0fRb", value / 1_000.0);
                } else {
                    return String.format(Locale.US, "Rp %.0f", value);
                }
            }
        });

        barChart.getAxisRight().setEnabled(false);

        // Construct datasets
        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();

        double maxIncome = 0.0;
        double maxExpense = 0.0;
        String maxIncomeMonth = "";
        String maxExpenseMonth = "";

        for (int i = 0; i < list.size(); i++) {
            MonthlyTrend trend = list.get(i);
            incomeEntries.add(new BarEntry(i, (float) trend.getTotalIncome()));
            expenseEntries.add(new BarEntry(i, (float) trend.getTotalExpense()));

            if (trend.getTotalIncome() > maxIncome) {
                maxIncome = trend.getTotalIncome();
                maxIncomeMonth = DateTimeUtil.formatIsoToDisplayMonth(trend.getMonth());
            }
            if (trend.getTotalExpense() > maxExpense) {
                maxExpense = trend.getTotalExpense();
                maxExpenseMonth = DateTimeUtil.formatIsoToDisplayMonth(trend.getMonth());
            }
        }

        BarDataSet setIncome = new BarDataSet(incomeEntries, "Pemasukan");
        setIncome.setColor(getColor(R.color.colorIncome));
        setIncome.setDrawValues(false);

        BarDataSet setExpense = new BarDataSet(expenseEntries, "Pengeluaran");
        setExpense.setColor(getColor(R.color.colorExpense));
        setExpense.setDrawValues(false);

        BarData data = new BarData(setIncome, setExpense);
        
        float groupSpace = 0.34f;
        float barSpace = 0.03f;
        float barWidth = 0.3f;
        data.setBarWidth(barWidth);
        
        barChart.setData(data);
        barChart.groupBars(0f, groupSpace, barSpace);
        
        barChart.getXAxis().setAxisMinimum(0f);
        barChart.getXAxis().setAxisMaximum(data.getGroupWidth(groupSpace, barSpace) * list.size());
        barChart.getXAxis().setCenterAxisLabels(true);
        barChart.setFitBars(true);

        Legend legend = barChart.getLegend();
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(true);
        legend.setTextSize(10f);
        legend.setTextColor(getColor(R.color.colorTextPrimary));

        if (!isBarAnimated) {
            barChart.animateY(1200);
            isBarAnimated = true;
        } else {
            barChart.invalidate();
        }

        // Generate Brief Analysis Summary paragraph dynamically
        String analysis;
        if (maxIncome > 0 || maxExpense > 0) {
            analysis = String.format("Dalam 6 bulan terakhir, pemasukan bulanan tertinggi terjadi pada bulan %s sebesar %s. " +
                            "Sedangkan pengeluaran terbesar dicatat pada bulan %s sebesar %s.",
                    maxIncomeMonth.isEmpty() ? "-" : maxIncomeMonth,
                    CurrencyUtil.formatRupiah(maxIncome),
                    maxExpenseMonth.isEmpty() ? "-" : maxExpenseMonth,
                    CurrencyUtil.formatRupiah(maxExpense));
        } else {
            analysis = "Belum memiliki data transaksi yang cukup untuk menyusun analisis keuangan bulanan.";
        }
        binding.textTrendAnalysis.setText(analysis);
    }

    private void showEmptyState(boolean isEmpty, String message) {
        if (isEmpty) {
            binding.layoutPieChartSection.setVisibility(View.GONE);
            binding.layoutBarChartSection.setVisibility(View.GONE);
            binding.textEmptyReport.setVisibility(View.VISIBLE);
            if (message != null) {
                binding.textEmptyReport.setText(message);
            }
        } else {
            binding.textEmptyReport.setVisibility(View.GONE);
            if (currentTab == 0) {
                binding.layoutPieChartSection.setVisibility(View.VISIBLE);
            } else {
                binding.layoutBarChartSection.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setLoading(boolean isLoading) {
        binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}

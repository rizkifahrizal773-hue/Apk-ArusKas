package com.aruskas.app.model;

import com.google.gson.annotations.SerializedName;

public class MonthlyTrend {
    private String month; // "YYYY-MM"
    
    @SerializedName("total_income")
    private double totalIncome;
    
    @SerializedName("total_expense")
    private double totalExpense;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public double getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(double totalIncome) {
        this.totalIncome = totalIncome;
    }

    public double getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(double totalExpense) {
        this.totalExpense = totalExpense;
    }
}

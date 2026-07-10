package com.aruskas.app.model;

import com.google.gson.annotations.SerializedName;

public class FinanceSummary {
    @SerializedName("total_income")
    private double totalIncome;
    
    @SerializedName("total_expense")
    private double totalExpense;
    
    private double balance;
    
    @SerializedName("previous_month_balance")
    private double previousMonthBalance;

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

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getPreviousMonthBalance() {
        return previousMonthBalance;
    }

    public void setPreviousMonthBalance(double previousMonthBalance) {
        this.previousMonthBalance = previousMonthBalance;
    }
}

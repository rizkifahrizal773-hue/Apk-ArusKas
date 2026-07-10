package com.aruskas.app.network;

import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.User;
import com.aruskas.app.model.Budget;
import com.aruskas.app.model.Category;
import com.aruskas.app.model.CategoryBreakdown;
import com.aruskas.app.model.FinanceSummary;
import com.aruskas.app.model.MonthlyTrend;
import com.aruskas.app.model.RecurringTransaction;
import com.aruskas.app.model.Transaction;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ==========================================
    // Auth Endpoints
    // ==========================================
    @POST("api/auth/register")
    Call<ApiResponse<User>> register(
            @Body com.aruskas.app.model.RegisterRequest request
    );

    @POST("api/auth/login")
    Call<ApiResponse<User>> login(
            @Body com.aruskas.app.model.LoginRequest request
    );

    // ==========================================
    // 6.1 Categories Endpoints
    // ==========================================
    @GET("api/categories")
    Call<ApiResponse<List<Category>>> getCategories(
            @Query("type") String type
    );

    @POST("api/categories")
    Call<ApiResponse<Category>> createCategory(
            @Body Category category
    );

    @PUT("api/categories/{id}")
    Call<ApiResponse<Category>> updateCategory(
            @Path("id") int id,
            @Body Category category
    );

    @DELETE("api/categories/{id}")
    Call<ApiResponse<Void>> deleteCategory(
            @Path("id") int id
    );

    // ==========================================
    // 6.2 Transactions Endpoints
    // ==========================================
    @GET("api/transactions")
    Call<ApiResponse<List<Transaction>>> getTransactions(
            @Query("month") String month,
            @Query("category_id") Integer categoryId,
            @Query("type") String type,
            @Query("keyword") String keyword
    );

    @GET("api/transactions/summary")
    Call<ApiResponse<FinanceSummary>> getTransactionSummary(
            @Query("month") String month
    );

    @GET("api/transactions/{id}")
    Call<ApiResponse<Transaction>> getTransactionById(
            @Path("id") int id
    );

    @Multipart
    @POST("api/transactions")
    Call<ApiResponse<Transaction>> createTransactionMultipart(
            @Part("type") RequestBody type,
            @Part("amount") RequestBody amount,
            @Part("category_id") RequestBody categoryId,
            @Part("note") RequestBody note,
            @Part("transaction_date") RequestBody transactionDate,
            @Part MultipartBody.Part receipt
    );

    @Multipart
    @POST("api/transactions")
    Call<ApiResponse<Transaction>> createTransactionMultipartWithoutReceipt(
            @Part("type") RequestBody type,
            @Part("amount") RequestBody amount,
            @Part("category_id") RequestBody categoryId,
            @Part("note") RequestBody note,
            @Part("transaction_date") RequestBody transactionDate
    );

    @Multipart
    @PUT("api/transactions/{id}")
    Call<ApiResponse<Transaction>> updateTransactionMultipart(
            @Path("id") int id,
            @Part("type") RequestBody type,
            @Part("amount") RequestBody amount,
            @Part("category_id") RequestBody categoryId,
            @Part("note") RequestBody note,
            @Part("transaction_date") RequestBody transactionDate,
            @Part MultipartBody.Part receipt
    );

    @PUT("api/transactions/{id}")
    Call<ApiResponse<Transaction>> updateTransactionJson(
            @Path("id") int id,
            @Body Transaction transaction
    );

    @DELETE("api/transactions/{id}")
    Call<ApiResponse<Void>> deleteTransaction(
            @Path("id") int id
    );

    // ==========================================
    // 6.3 Budgets Endpoints
    // ==========================================
    @GET("api/budgets")
    Call<ApiResponse<List<Budget>>> getBudgets(
            @Query("month") String month
    );

    @POST("api/budgets")
    Call<ApiResponse<Budget>> createBudget(
            @Body Budget budget
    );

    @PUT("api/budgets/{id}")
    Call<ApiResponse<Budget>> updateBudget(
            @Path("id") int id,
            @Body Budget budget // only limit_amount is mapped from JSON
    );

    @DELETE("api/budgets/{id}")
    Call<ApiResponse<Void>> deleteBudget(
            @Path("id") int id
    );

    // ==========================================
    // 6.4 Recurring Transactions Endpoints
    // ==========================================
    @GET("api/recurring-transactions")
    Call<ApiResponse<List<RecurringTransaction>>> getRecurringTransactions();

    @POST("api/recurring-transactions")
    Call<ApiResponse<RecurringTransaction>> createRecurringTransaction(
            @Body RecurringTransaction recurringTransaction
    );

    @PUT("api/recurring-transactions/{id}")
    Call<ApiResponse<RecurringTransaction>> updateRecurringTransaction(
            @Path("id") int id,
            @Body RecurringTransaction recurringTransaction
    );

    @DELETE("api/recurring-transactions/{id}")
    Call<ApiResponse<Void>> deleteRecurringTransaction(
            @Path("id") int id
    );

    // ==========================================
    // 6.5 Reports Endpoints
    // ==========================================
    @GET("api/reports/monthly-trend")
    Call<ApiResponse<List<MonthlyTrend>>> getMonthlyTrend(
            @Query("months") Integer months
    );

    @GET("api/reports/category-breakdown")
    Call<ApiResponse<List<CategoryBreakdown>>> getCategoryBreakdown(
            @Query("month") String month,
            @Query("type") String type
    );

    // ==========================================
    // 6.6 Device Token (FCM) Endpoints
    // ==========================================
    @POST("api/device-token")
    Call<ApiResponse<Void>> registerDeviceToken(
            @Body Map<String, String> tokenPayload // JSON: {"token": "fcm_token"}
    );
}

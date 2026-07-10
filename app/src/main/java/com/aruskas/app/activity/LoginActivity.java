package com.aruskas.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.aruskas.app.databinding.ActivityLoginBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.LoginRequest;
import com.aruskas.app.model.User;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.SessionManager;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void attemptLogin() {
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();

        boolean isValid = true;

        if (email.isEmpty()) {
            binding.inputEmailLayout.setError("Email tidak boleh kosong");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputEmailLayout.setError("Format email tidak valid");
            isValid = false;
        } else {
            binding.inputEmailLayout.setError(null);
        }

        if (password.isEmpty()) {
            binding.inputPasswordLayout.setError("Password tidak boleh kosong");
            isValid = false;
        } else {
            binding.inputPasswordLayout.setError(null);
        }

        if (!isValid) return;

        setLoading(true);

        LoginRequest request = new LoginRequest(email, password);
        ApiClient.getApiService().login(request).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<User>> call, @NonNull Response<ApiResponse<User>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<User> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        // Save session & open Dashboard
                        sessionManager.saveSession(apiResponse.getData());
                        Toast.makeText(LoginActivity.this, "Selamat datang, " + apiResponse.getData().getName(), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    handleErrorResponse(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Koneksi gagal. Periksa jaringan Anda.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleErrorResponse(Response<?> response) {
        String errorMsg = "Email atau password salah";
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                try {
                    org.json.JSONObject jsonObject = new org.json.JSONObject(errorBodyStr);
                    if (jsonObject.has("message")) {
                        errorMsg = jsonObject.getString("message");
                    }
                } catch (org.json.JSONException e) {
                    // fallback
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean isLoading) {
        binding.layoutLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnGoToRegister.setEnabled(!isLoading);
    }
}

package com.aruskas.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.aruskas.app.databinding.ActivityRegisterBinding;
import com.aruskas.app.model.ApiResponse;
import com.aruskas.app.model.RegisterRequest;
import com.aruskas.app.model.User;
import com.aruskas.app.network.ApiClient;
import com.aruskas.app.util.SessionManager;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = new SessionManager(this);

        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.btnGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String name = binding.inputName.getText().toString().trim();
        String email = binding.inputEmail.getText().toString().trim();
        String password = binding.inputPassword.getText().toString().trim();

        boolean isValid = true;

        if (name.isEmpty()) {
            binding.inputNameLayout.setError("Nama tidak boleh kosong");
            isValid = false;
        } else {
            binding.inputNameLayout.setError(null);
        }

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
        } else if (password.length() < 6) {
            binding.inputPasswordLayout.setError("Password minimal 6 karakter");
            isValid = false;
        } else {
            binding.inputPasswordLayout.setError(null);
        }

        if (!isValid) return;

        setLoading(true);

        RegisterRequest request = new RegisterRequest(name, email, password);
        ApiClient.getApiService().register(request).enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<User>> call, @NonNull Response<ApiResponse<User>> response) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<User> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        sessionManager.saveSession(apiResponse.getData());
                        Toast.makeText(RegisterActivity.this, "Registrasi berhasil! Selamat datang, " + apiResponse.getData().getName(), Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, DashboardActivity.class));
                        finish();
                    } else {
                        Toast.makeText(RegisterActivity.this, apiResponse.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    handleErrorResponse(response);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Koneksi gagal. Periksa jaringan Anda.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleErrorResponse(Response<?> response) {
        String errorMsg = "Registrasi gagal";
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
        binding.btnRegister.setEnabled(!isLoading);
        binding.btnGoToLogin.setEnabled(!isLoading);
    }
}

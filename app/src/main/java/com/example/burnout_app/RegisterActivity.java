package com.example.burnout_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.burnout_app.helpers.SessionManager;
import com.example.burnout_app.viewmodel.OnboardingViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextInputEditText etNombre;
    private TextInputEditText etApellidos;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnCreateAccount;
    private TextView tvGoLogin;

    private OnboardingViewModel onboardingViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        onboardingViewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);

        btnBack = findViewById(R.id.btnBack);
        etNombre = findViewById(R.id.etNombre);
        etApellidos = findViewById(R.id.etApellidos);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        tvGoLogin = findViewById(R.id.tvGoLogin);

        btnBack.setOnClickListener(v -> finish());

        tvGoLogin.setOnClickListener(v ->
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class)));

        btnCreateAccount.setOnClickListener(v -> {
            String firstName = etNombre.getText() != null ? etNombre.getText().toString().trim() : "";
            String lastName = etApellidos.getText() != null ? etApellidos.getText().toString().trim() : "";
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

            if (!onboardingViewModel.isInputValid(firstName, lastName, email, password, confirmPassword)) {
                Toast.makeText(this, "Revisa los campos introducidos", Toast.LENGTH_SHORT).show();
                return;
            }

            onboardingViewModel.createUserProfile(firstName, lastName, email, password);

            SessionManager sessionManager = new SessionManager(this);
            sessionManager.setLoggedIn(true);

            Toast.makeText(this, "Cuenta creada correctamente", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finishAffinity();
        });
    }
}
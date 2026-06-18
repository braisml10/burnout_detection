package gal.uvigo.burnout_app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import gal.uvigo.burnout_app.R;
import gal.uvigo.burnout_app.base.BaseActivity;
import gal.uvigo.burnout_app.helpers.SessionManager;
import gal.uvigo.burnout_app.viewmodel.OnboardingViewModel;

public class RegisterActivity extends BaseActivity {

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
                Toast.makeText(this, getString(R.string.register_error_invalid_input), Toast.LENGTH_SHORT).show();
                return;
            }

            onboardingViewModel.createUserProfile(firstName, lastName, email, password);

            SessionManager sessionManager = new SessionManager(this);
            sessionManager.setLoggedIn(true);

            Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            finishAffinity();
        });
    }
}
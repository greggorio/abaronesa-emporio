package com.baronesa.website.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

@Configuration
public class FirebaseConfig {
    private final boolean enabled;
    private final String credentialsPath;
    private final CredentialLoader credentialLoader;
    private final BooleanSupplier alreadyInitialized;
    private final FirebaseInitializer firebaseInitializer;

    @Autowired
    public FirebaseConfig(@Value("${app.firebase.enabled:false}") boolean enabled,
                          @Value("${app.firebase.credentials-path:}") String credentialsPath) {
        this(enabled, credentialsPath,
                path -> GoogleCredentials.fromStream(new FileInputStream(path)),
                () -> !FirebaseApp.getApps().isEmpty(),
                credentials -> FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build()));
    }

    FirebaseConfig(boolean enabled, String credentialsPath, CredentialLoader credentialLoader,
                   BooleanSupplier alreadyInitialized, FirebaseInitializer firebaseInitializer) {
        this.enabled = enabled;
        this.credentialsPath = credentialsPath;
        this.credentialLoader = credentialLoader;
        this.alreadyInitialized = alreadyInitialized;
        this.firebaseInitializer = firebaseInitializer;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException("Firebase habilitado sem credencial valida");
        }
        try {
            if (!alreadyInitialized.getAsBoolean()) {
                firebaseInitializer.initialize(credentialLoader.load(credentialsPath));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Falha sanitizada ao inicializar Firebase");
        }
    }

    @FunctionalInterface interface CredentialLoader {
        GoogleCredentials load(String path) throws Exception;
    }
    @FunctionalInterface interface FirebaseInitializer {
        void initialize(GoogleCredentials credentials) throws Exception;
    }
}

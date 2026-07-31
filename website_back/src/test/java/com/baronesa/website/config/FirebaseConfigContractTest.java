package com.baronesa.website.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class FirebaseConfigContractTest {
    private static GoogleCredentials credentials() throws Exception {
        return GoogleCredentials.fromStream(new ByteArrayInputStream(
                "{\"type\":\"authorized_user\",\"client_id\":\"test\",\"client_secret\":\"test\",\"refresh_token\":\"test\"}".getBytes()));
    }
    @Test void disabledTouchesNoExternalResource() {
        AtomicBoolean touched = new AtomicBoolean();
        new FirebaseConfig(false, "", p -> { touched.set(true); return credentials(); },
                () -> { touched.set(true); return false; }, c -> touched.set(true)).initialize();
        assertFalse(touched.get());
    }
    @Test void enabledValidInitializes() throws Exception {
        AtomicBoolean initialized = new AtomicBoolean();
        GoogleCredentials credential = credentials();
        new FirebaseConfig(true, "opaque", p -> credential, () -> false, c -> initialized.set(true)).initialize();
        assertTrue(initialized.get());
    }
    @Test void enabledInvalidFailsClosedWithoutPath() {
        var config = new FirebaseConfig(true, "/sensitive/path", p -> { throw new Exception("secret"); },
                () -> false, c -> {});
        IllegalStateException error = assertThrows(IllegalStateException.class, config::initialize);
        assertFalse(error.getMessage().contains("sensitive"));
    }
}

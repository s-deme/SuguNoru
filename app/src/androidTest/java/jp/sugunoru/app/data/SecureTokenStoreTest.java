package jp.sugunoru.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import java.security.KeyStore;

public class SecureTokenStoreTest extends InstrumentationTestCase {
    private SharedPreferences preferences;
    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        assertTrue("Run only against the disposable verification package",
                context.getPackageName().endsWith(".verification"));
        preferences = context.getSharedPreferences("security_token_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }
    @Override protected void tearDown() throws Exception {
        preferences.edit().clear().commit();
        super.tearDown();
    }
    public void testMigrationAndDeletion() {
        preferences.edit().putString(SecureTokenStore.LEGACY, "synthetic-test-token").commit();
        SecureTokenStore store = new SecureTokenStore(preferences);
        assertEquals("synthetic-test-token", store.read());
        assertFalse(preferences.contains(SecureTokenStore.LEGACY));
        assertFalse(preferences.getString(SecureTokenStore.ENCRYPTED, "").contains("synthetic-test-token"));
        assertEquals("synthetic-test-token", new SecureTokenStore(preferences).read());
        store.write("");
        assertEquals("", store.read());
        assertFalse(preferences.contains(SecureTokenStore.ENCRYPTED));
    }
    public void testInterruptedMigration() {
        SecureTokenStore store = new SecureTokenStore(preferences);
        store.write("synthetic-test-token");
        preferences.edit().putString(SecureTokenStore.LEGACY, "synthetic-test-token").commit();
        assertEquals("synthetic-test-token", store.read());
        assertFalse(preferences.contains(SecureTokenStore.LEGACY));
    }
    public void testKeyLossAndCorruptionNeverFallBackToPlaintext() throws Exception {
        SecureTokenStore store = new SecureTokenStore(preferences);
        store.write("synthetic-test-token");
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        keys.deleteEntry(SecureTokenStore.ALIAS);
        assertEquals("", store.read());
        store.write("replacement-test-token");
        assertEquals("replacement-test-token", store.read());
        preferences.edit().putString(SecureTokenStore.ENCRYPTED, "invalid")
                .putString(SecureTokenStore.LEGACY, "must-not-fall-back").commit();
        assertEquals("", store.read());
    }
}

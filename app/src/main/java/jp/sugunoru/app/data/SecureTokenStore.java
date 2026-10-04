package jp.sugunoru.app.data;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Background-readable tokens encrypted with an app-owned, non-exportable Keystore key. */
final class SecureTokenStore {
    static final String LEGACY = "odpt_access_token";
    static final String ENCRYPTED = "odpt_access_token_encrypted_v1";
    static final String ALIAS = "sugunoru-odpt-token-v1";
    private static final Object LOCK = new Object();
    private static final byte[] AAD = ALIAS.getBytes(StandardCharsets.UTF_8);
    private final SharedPreferences preferences;

    SecureTokenStore(SharedPreferences preferences) { this.preferences = preferences; }

    String read() {
        synchronized (LOCK) {
            try {
                String encrypted = preferences.getString(ENCRYPTED, "");
                if (!encrypted.isEmpty()) {
                    String token = decrypt(encrypted);
                    removeLegacy();
                    return token;
                }
                String legacy = preferences.getString(LEGACY, "").trim();
                if (legacy.isEmpty()) return "";
                if (legacy.length() > 512) return "";
                write(legacy);
                return decrypt(preferences.getString(ENCRYPTED, ""));
            } catch (Exception error) {
                // Missing/invalid keys and storage failures require re-entry, never plaintext fallback.
                android.util.Log.w("SecureTokenStore", "Token unavailable; enter it again.");
                return "";
            }
        }
    }

    void write(String token) {
        synchronized (LOCK) {
            if (token.isEmpty()) {
                if (!preferences.edit().remove(ENCRYPTED).remove(LEGACY).commit())
                    throw new IllegalStateException("トークンを削除できませんでした。");
                return;
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key(true));
                cipher.updateAAD(AAD);
                String encrypted = "v1:" + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                        + ":" + Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
                if (!preferences.edit().putString(ENCRYPTED, encrypted).commit())
                    throw new IllegalStateException("トークンを保存できませんでした。");
                if (!token.equals(decrypt(preferences.getString(ENCRYPTED, ""))))
                    throw new GeneralSecurityException("Encrypted token verification failed");
                removeLegacy();
            } catch (Exception error) {
                throw new IllegalStateException("トークンを安全に保存できませんでした。再入力してください。", error);
            }
        }
    }

    private void removeLegacy() {
        if (preferences.contains(LEGACY) && !preferences.edit().remove(LEGACY).commit())
            throw new IllegalStateException("旧トークンを削除できませんでした。");
    }

    private static String decrypt(String encrypted) throws Exception {
        if (encrypted.length() > 8192) throw new GeneralSecurityException("Token too large");
        String[] parts = encrypted.split(":", -1);
        if (parts.length != 3 || !"v1".equals(parts[0]))
            throw new GeneralSecurityException("Invalid token envelope");
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] data = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != 12 || data.length < 16)
            throw new GeneralSecurityException("Invalid token envelope");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }

    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            if (!create) throw new GeneralSecurityException("Token key unavailable");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
}

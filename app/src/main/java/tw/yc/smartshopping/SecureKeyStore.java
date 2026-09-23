package tw.yc.smartshopping;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureKeyStore {
    private static final String LEGACY_ALIAS = "smart-shopping-api-key-v1";
    private static final String PROVIDER_ALIAS_PREFIX = "smart-shopping-api-key-v2-";
    private static final String PREF = "encrypted_api_credentials";
    private final SharedPreferences preferences;

    public SecureKeyStore(Context context) {
        preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Writes the legacy DeepSeek slot for compatibility with v0.2.1 callers. */
    public void save(String value) throws Exception {
        save(value, LEGACY_ALIAS, "iv", "ciphertext");
    }

    /** Reads the v0.2.1 DeepSeek credential. */
    public String get() {
        return read(LEGACY_ALIAS, "iv", "ciphertext");
    }

    /** Clears the legacy DeepSeek slot. */
    public void clear() {
        clearForProvider(AiProvider.DEEPSEEK.id());
    }

    public String getForProvider(String providerId) {
        AiProvider provider = requireProvider(providerId);
        String ivKey = ivKey(provider);
        String ciphertextKey = ciphertextKey(provider);
        if (!preferences.contains(ivKey) && !preferences.contains(ciphertextKey)) {
            return provider == AiProvider.DEEPSEEK ? get() : null;
        }
        String current = read(alias(provider), ivKey, ciphertextKey);
        return current != null || provider != AiProvider.DEEPSEEK ? current : get();
    }

    public void saveForProvider(String providerId, String value) throws Exception {
        AiProvider provider = requireProvider(providerId);
        save(value, alias(provider), ivKey(provider), ciphertextKey(provider));
    }

    public void clearForProvider(String providerId) {
        AiProvider provider = requireProvider(providerId);
        SharedPreferences.Editor editor = preferences.edit()
                .remove(ivKey(provider))
                .remove(ciphertextKey(provider));
        if (provider == AiProvider.DEEPSEEK) editor.remove("iv").remove("ciphertext");
        editor.apply();
        deleteAlias(alias(provider));
        if (provider == AiProvider.DEEPSEEK) deleteAlias(LEGACY_ALIAS);
    }

    private void save(String value, String keyAlias, String ivPreference, String ciphertextPreference) throws Exception {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("API key is empty");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(keyAlias));
        preferences.edit()
                .putString(ivPreference, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(ciphertextPreference, Base64.encodeToString(cipher.doFinal(value.trim().getBytes("UTF-8")), Base64.NO_WRAP))
                .apply();
    }

    private String read(String keyAlias, String ivPreference, String ciphertextPreference) {
        String iv = preferences.getString(ivPreference, null);
        String ciphertext = preferences.getString(ciphertextPreference, null);
        if (iv == null || ciphertext == null) {
            if (iv != null || ciphertext != null) removeSlot(ivPreference, ciphertextPreference);
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(keyAlias), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), "UTF-8");
        } catch (Exception invalidated) {
            removeSlot(ivPreference, ciphertextPreference);
            return null;
        }
    }

    private void removeSlot(String ivPreference, String ciphertextPreference) {
        preferences.edit().remove(ivPreference).remove(ciphertextPreference).apply();
    }

    private SecretKey key(String keyAlias) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(keyAlias, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private void deleteAlias(String keyAlias) {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias);
        } catch (Exception ignored) {
            // The ciphertext has already been removed from preferences.
        }
    }

    private String alias(AiProvider provider) { return PROVIDER_ALIAS_PREFIX + provider.id(); }
    private String ivKey(AiProvider provider) { return provider.id() + "_iv"; }
    private String ciphertextKey(AiProvider provider) { return provider.id() + "_ciphertext"; }

    private AiProvider requireProvider(String providerId) {
        if (AiProvider.GEMINI.id().equals(providerId)) return AiProvider.GEMINI;
        if (AiProvider.DEEPSEEK.id().equals(providerId)) return AiProvider.DEEPSEEK;
        throw new IllegalArgumentException("Unsupported AI provider");
    }
}

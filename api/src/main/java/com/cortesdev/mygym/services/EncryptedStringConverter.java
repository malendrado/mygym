package com.cortesdev.mygym.services;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra en reposo los campos sensibles de la cuenta Flow de cada gym (API key/secret key) —
 * a diferencia de APP_JWT_SECRET (firma, nunca se guarda), estos valores hay que poder
 * recuperarlos en texto plano para llamar a la API de Flow, así que van cifrados
 * (AES-256-GCM, IV aleatorio por valor) en vez de hasheados.
 *
 * Clave desde la env var APP_ENCRYPTION_KEY (32 bytes en base64) — mismo patrón de env var
 * obligatoria que APP_JWT_SECRET/GOOGLE_CLIENT_ID, nunca hardcodeada ni con default. Los
 * AttributeConverter de JPA no pasan por el contenedor de Spring (Hibernate los instancia él
 * mismo), así que la clave se lee directo de la env var una sola vez, sin @Value/inyección.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private static final SecretKeySpec KEY = loadKey();

    private static SecretKeySpec loadKey() {
        String raw = System.getenv("APP_ENCRYPTION_KEY");
        if (raw == null || raw.isBlank()) {
            return null; // se exige recién al usar, no al arrancar la app.
        }
        return new SecretKeySpec(Base64.getDecoder().decode(raw), "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        requireKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        requireKey();
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] cipherText = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar el dato", e);
        }
    }

    private static void requireKey() {
        if (KEY == null) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY no está configurada");
        }
    }
}

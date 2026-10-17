package com.cortesdev.mygym.services;

import java.util.Base64;
import java.util.Locale;

/**
 * Comprueba que un "data:image/...;base64,..." sea de verdad la imagen que dice ser. El prefijo (data:image/png) lo escribe
 * quien llama a la API y no prueba nada: antes bastaba con empezar con ese texto para guardar cualquier contenido, que después
 * se sirve a todos los visitantes de la página pública del gimnasio. Se revisan los primeros bytes (la firma del formato).
 */
final class ImageDataUri {

    private ImageDataUri() {}

    /** true si es base64 y los primeros bytes corresponden al formato declarado (PNG, JPEG o WEBP). */
    static boolean hasMatchingSignature(String dataUri) {
        if (dataUri == null) {
            return false;
        }
        String lower = dataUri.toLowerCase(Locale.ROOT);
        int comma = dataUri.indexOf(',');
        if (comma < 0 || !lower.substring(0, comma).endsWith(";base64")) {
            return false;
        }
        String declared = lower.substring("data:".length(), lower.indexOf(';'));
        byte[] head;
        try {
            // 24 caracteres base64 = 18 bytes: de sobra para las tres firmas, sin decodificar la imagen entera (hasta 3 MB).
            String payload = dataUri.substring(comma + 1);
            String prefix = payload.substring(0, Math.min(payload.length(), 24));
            head = Base64.getDecoder().decode(prefix);
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
        return switch (declared) {
            case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
            case "image/webp" -> head.length >= 12
                    && startsWith(head, 'R', 'I', 'F', 'F')
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((head[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}

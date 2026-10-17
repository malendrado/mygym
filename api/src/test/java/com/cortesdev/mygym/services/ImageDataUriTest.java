package com.cortesdev.mygym.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/** El prefijo "data:image/png" lo escribe quien llama a la API; lo que cuenta es la firma real del archivo. */
class ImageDataUriTest {

    private static String uri(String mime, byte... bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    void realPngJpegAndWebpAreAccepted() {
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/png", PNG))).isTrue();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/jpeg", JPEG))).isTrue();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/webp", WEBP))).isTrue();
        assertThat(ImageDataUri.hasMatchingSignature(uri("IMAGE/PNG", PNG))).isTrue();
    }

    @Test
    void aFileThatOnlyClaimsToBeAnImageIsRejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/png", html))).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/jpeg", html))).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/webp", html))).isFalse();
    }

    @Test
    void theSignatureMustMatchTheDeclaredFormat() {
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/png", JPEG))).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/jpeg", PNG))).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/webp", PNG))).isFalse();
    }

    @Test
    void malformedDataUrisAreRejectedWithoutThrowing() {
        assertThat(ImageDataUri.hasMatchingSignature(null)).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature("")).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature("data:image/png")).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature("data:image/png,iVBORw0KGgo=")).isFalse(); // sin ;base64
        assertThat(ImageDataUri.hasMatchingSignature("data:image/png;base64,###no-es-base64###")).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature("data:image/png;base64,")).isFalse();
        assertThat(ImageDataUri.hasMatchingSignature("data:image/gif;base64,R0lGODlhAQABAAAAACw=")).isFalse();
    }

    @Test
    void aLongImageIsOnlyReadAtTheStart() {
        byte[] big = new byte[2_000_000];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThat(ImageDataUri.hasMatchingSignature(uri("image/png", big))).isTrue();
    }
}

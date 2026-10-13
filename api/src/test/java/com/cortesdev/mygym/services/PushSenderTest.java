package com.cortesdev.mygym.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortesdev.mygym.models.PushSubscription;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PushSenderTest {

    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();

    // Vector de prueba del RFC 8291, seccion 5 y apendice A.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private final PushSender unconfigured = new PushSender("", "", "mailto:x@y.cl");

    private static ECParameterSpec p256() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static KeyPair keyPair(String privateB64, String publicB64) throws Exception {
        PrivateKey priv = KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, DEC.decode(privateB64)), p256()));
        PublicKey pub = PushSender.publicKeyFromUncompressed(DEC.decode(publicB64));
        return new KeyPair(pub, priv);
    }

    @Test
    void encryptionMatchesTheRfc8291TestVector() throws Exception {
        byte[] body = unconfigured.encrypt(
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                DEC.decode(UA_PUBLIC),
                DEC.decode(AUTH_SECRET),
                keyPair(AS_PRIVATE, AS_PUBLIC),
                DEC.decode(SALT));
        assertEquals(EXPECTED_BODY, ENC.encodeToString(body));
    }

    @Test
    void theBrowserSideCanDecryptWhatWeEncrypt() throws Exception {
        // El navegador (user agent) tiene su propio par y un secreto de autenticacion; nosotros solo conocemos lo publico.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair browser = generator.generateKeyPair();
        byte[] browserPublic = PushSender.uncompressed((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] payload = "{\"notification\":{\"title\":\"¡Se liberó un cupo!\"}}".getBytes(StandardCharsets.UTF_8);

        byte[] body = unconfigured.encrypt(payload, browserPublic, auth);

        // Cabecera: salt(16) + rs(4) + idlen(1) + clave del servidor(65) + texto cifrado.
        assertEquals(4096, java.nio.ByteBuffer.wrap(body, 16, 4).getInt());
        assertEquals(65, body[20]);
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        byte[] asPublic = Arrays.copyOfRange(body, 21, 86);
        byte[] ciphertext = Arrays.copyOfRange(body, 86, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(PushSender.publicKeyFromUncompressed(asPublic), true);
        byte[] ecdh = agreement.generateSecret();
        byte[] ikm = expand(
                hmac(auth, ecdh),
                concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, asPublic),
                32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plaintext = cipher.doFinal(ciphertext);

        assertEquals(2, plaintext[plaintext.length - 1]); // delimitador de relleno del ultimo registro
        assertArrayEquals(payload, Arrays.copyOf(plaintext, plaintext.length - 1));
    }

    @Test
    void vapidJwtIsSignedWithTheConfiguredKeyAndCarriesTheAudience() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair vapid = generator.generateKeyPair();
        String privateB64 = ENC.encodeToString(unsigned32(((java.security.interfaces.ECPrivateKey) vapid.getPrivate()).getS()));
        String publicB64 = ENC.encodeToString(PushSender.uncompressed((ECPublicKey) vapid.getPublic()));
        PushSender sender = new PushSender(publicB64, privateB64, "mailto:contacto@mygym.cl");
        assertTrue(sender.isConfigured());
        assertEquals(publicB64, sender.publicKey());

        String jwt = sender.vapidJwt("https://fcm.googleapis.com", Instant.parse("2030-01-01T00:00:00Z"));
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        String claims = new String(DEC.decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(claims.contains("\"aud\":\"https://fcm.googleapis.com\""));
        assertTrue(claims.contains("\"sub\":\"mailto:contacto@mygym.cl\""));
        assertTrue(claims.contains("\"exp\":" + Instant.parse("2030-01-01T00:00:00Z").getEpochSecond()));

        Signature verify = Signature.getInstance("SHA256withECDSAinP1363Format");
        verify.initVerify(vapid.getPublic());
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verify.verify(DEC.decode(parts[2])));
    }

    @Test
    void withoutKeysNothingIsSent() {
        assertFalse(unconfigured.isConfigured());
        assertNull(unconfigured.publicKey());
        PushSubscription subscription = PushSubscription.builder()
                .id(1L)
                .endpoint("https://fcm.googleapis.com/fcm/send/abc")
                .p256dh(UA_PUBLIC)
                .auth(AUTH_SECRET)
                .build();
        PushMessage message = new PushMessage("t", "b", "tag", "/member", 60, PushMessage.Urgency.NORMAL);
        assertEquals(PushSender.Result.SKIPPED, unconfigured.send(subscription, message));
    }

    @Test
    void endpointPolicyOnlyAcceptsKnownPushServices() {
        assertTrue(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com/fcm/send/abc"));
        assertTrue(PushEndpointPolicy.isAllowed("https://updates.push.services.mozilla.com/wpush/v2/abc"));
        assertTrue(PushEndpointPolicy.isAllowed("https://web.push.apple.com/abc"));
        assertTrue(PushEndpointPolicy.isAllowed("https://wns2-par02p.notify.windows.com/w/?token=abc"));
        assertFalse(PushEndpointPolicy.isAllowed("http://fcm.googleapis.com/fcm/send/abc"));
        assertFalse(PushEndpointPolicy.isAllowed("https://evil.com/fcm.googleapis.com"));
        assertFalse(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com.evil.com/x"));
        assertFalse(PushEndpointPolicy.isAllowed("https://user@fcm.googleapis.com/x"));
        assertFalse(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com:8443/x"));
        assertFalse(PushEndpointPolicy.isAllowed("https://169.254.169.254/latest/meta-data"));
        assertFalse(PushEndpointPolicy.isAllowed("https://localhost/x"));
        assertFalse(PushEndpointPolicy.isAllowed("javascript:alert(1)"));
        assertFalse(PushEndpointPolicy.isAllowed(null));
        assertFalse(PushEndpointPolicy.isAllowed("https://fcm.googleapis.com/" + "a".repeat(1000)));
    }

    @Test
    void payloadFollowsTheFormatTheAngularServiceWorkerUnderstands() {
        PushMessage message = new PushMessage("Cancelación · Funcional 19:00", "Ana canceló", "cancel-1-2026-10-15", "/gym-admin", 3600, PushMessage.Urgency.NORMAL);
        String json = new String(message.toPayload(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"notification\""));
        assertTrue(json.contains("\"title\":\"Cancelación · Funcional 19:00\""));
        assertTrue(json.contains("\"tag\":\"cancel-1-2026-10-15\""));
        assertTrue(json.contains("\"operation\":\"navigateLastFocusedOrOpen\""));
        assertTrue(json.contains("\"url\":\"/gym-admin\""));
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}

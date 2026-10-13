package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.PushSubscription;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envia un aviso Web Push a UNA suscripcion: cifra el contenido (RFC 8291, aes128gcm) y se identifica ante el
 * servicio de push con VAPID (RFC 8292). Todo con el JDK, sin librerias. Sin claves configuradas no envia nada
 * (mismo patron que RESEND_API_KEY vacia): asi el backend local y los tests no las necesitan.
 */
@Component
public class PushSender {

    public enum Result {
        SENT,
        /** El servicio de push dice que la suscripcion ya no existe (404/410): hay que borrarla. */
        GONE,
        FAILED,
        /** Push sin configurar o endpoint no permitido: no se intento. */
        SKIPPED
    }

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    private static final int MAX_PAYLOAD_BYTES = 3000;
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private final RestClient restClient;
    private final String publicKey;
    private final PrivateKey vapidPrivateKey;
    private final String subject;

    public PushSender(
            @Value("${app.push.vapid.public-key:}") String publicKey,
            @Value("${app.push.vapid.private-key:}") String privateKey,
            @Value("${app.push.vapid.subject:mailto:contacto@mygym.cl}") String subject) {
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.subject = subject;
        this.vapidPrivateKey = loadPrivateKey(privateKey == null ? "" : privateKey.trim());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return vapidPrivateKey != null && !publicKey.isEmpty();
    }

    /** Clave publica VAPID en base64url (la que el navegador necesita para suscribirse), o null si no esta configurada. */
    public String publicKey() {
        return isConfigured() ? publicKey : null;
    }

    public Result send(PushSubscription subscription, PushMessage message) {
        if (!isConfigured()) {
            log.warn("Push sin claves VAPID configuradas: se omite el aviso '{}'", message.tag());
            return Result.SKIPPED;
        }
        String endpoint = subscription.getEndpoint();
        if (!PushEndpointPolicy.isAllowed(endpoint)) {
            log.warn("Suscripcion push {} con endpoint no permitido: se omite", subscription.getId());
            return Result.SKIPPED;
        }
        try {
            byte[] payload = message.toPayload();
            if (payload.length > MAX_PAYLOAD_BYTES) {
                log.warn("Aviso push '{}' demasiado largo ({} bytes): se omite", message.tag(), payload.length);
                return Result.SKIPPED;
            }
            byte[] body = encrypt(
                    payload, B64URL_DECODER.decode(subscription.getP256dh()), B64URL_DECODER.decode(subscription.getAuth()));
            String jwt = vapidJwt(PushEndpointPolicy.origin(endpoint), Instant.now().plusSeconds(12 * 3600L));
            restClient
                    .post()
                    .uri(endpoint)
                    .header("Authorization", "vapid t=" + jwt + ", k=" + publicKey)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(message.ttlSeconds()))
                    .header("Urgency", message.urgency().header())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            // Nunca se loguea el endpoint completo (identifica el dispositivo): solo id de suscripcion y estado.
            log.warn("Push a la suscripcion {} rechazado por el servicio: HTTP {}", subscription.getId(), status);
            return Result.FAILED;
        } catch (RestClientException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Push a la suscripcion {} fallo: {}", subscription.getId(), e.getClass().getSimpleName());
            return Result.FAILED;
        }
    }

    // ---------------------------------------------------------------------------------------
    // RFC 8291 - cifrado del contenido (aes128gcm)
    // ---------------------------------------------------------------------------------------

    byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, uaPublic, authSecret, generator.generateKeyPair(), salt);
    }

    /** Version determinista (par efimero y salt dados) para poder probarla contra los vectores del RFC 8291. */
    byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        byte[] asPublic = uncompressed((ECPublicKey) asKeys.getPublic());
        PublicKey uaKey = publicKeyFromUncompressed(uaPublic);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(asKeys.getPrivate());
        agreement.doPhase(uaKey, true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // Un solo registro: contenido + 0x02 (delimitador de relleno del ultimo registro).
        byte[] plaintext = concat(payload, new byte[] {2});
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(plaintext);

        ByteBuffer out = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length + ciphertext.length);
        out.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic).put(ciphertext);
        return out.array();
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** HKDF-Expand con SHA-256 para salidas de hasta 32 bytes (un solo bloque). */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        byte[] block = hmac(prk, concat(info, new byte[] {1}));
        return Arrays.copyOf(block, length);
    }

    // ---------------------------------------------------------------------------------------
    // RFC 8292 - VAPID
    // ---------------------------------------------------------------------------------------

    String vapidJwt(String audience, Instant expiry) throws GeneralSecurityException {
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64URL.encodeToString(
                ("{\"aud\":\"" + audience + "\",\"exp\":" + expiry.getEpochSecond() + ",\"sub\":\"" + subject + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(vapidPrivateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + B64URL.encodeToString(signature.sign());
    }

    // ---------------------------------------------------------------------------------------
    // Claves P-256
    // ---------------------------------------------------------------------------------------

    private static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static PrivateKey loadPrivateKey(String base64url) {
        if (base64url.isEmpty()) {
            return null;
        }
        try {
            BigInteger d = new BigInteger(1, B64URL_DECODER.decode(base64url));
            return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, p256()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("La clave privada VAPID no es valida: el push queda desactivado");
            return null;
        }
    }

    static PublicKey publicKeyFromUncompressed(byte[] bytes) throws GeneralSecurityException {
        if (bytes.length != 65 || bytes[0] != 4) {
            throw new IllegalArgumentException("Clave publica P-256 invalida");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(bytes, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(bytes, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256()));
    }

    static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed32(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed32(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] out = new byte[32];
        if (raw.length > 32) { // byte de signo sobrante
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}

package com.cortesdev.mygym.services;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP de bajo nivel para la API de Flow.cl — solo firma y postea, sin ninguna lógica
 * de negocio (eso vive en FlowPaymentService). apiKey/secretKey ya NO son globales: cada gym
 * tiene su propia cuenta Flow (ver Gym.flowApiKey/flowSecretKey), así que esta clase es
 * stateless respecto a credenciales — se las pasa el caller en cada llamada, resueltas del
 * gym correspondiente. baseUrl sigue siendo infraestructura compartida (sandbox/producción de
 * Flow), no algo que varíe por gym.
 *
 * Firma y contrato de /payment/create + /payment/getStatus VERIFICADOS contra el sandbox real
 * de Flow (2026-09-21) — ver SKILL.md del repo para el detalle completo.
 */
@Service
public class FlowApiClient {

    private static final Logger log = LoggerFactory.getLogger(FlowApiClient.class);

    private final RestClient restClient;

    public FlowApiClient(RestClient.Builder restClientBuilder, @Value("${app.flow.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /** POST firmado, form-urlencoded — agrega apiKey automáticamente, firma todo, y postea. */
    public Map<String, Object> post(String path, Map<String, String> params, String apiKey, String secretKey) {
        Map<String, String> signedParams = new TreeMap<>(params);
        signedParams.put("apiKey", apiKey);
        signedParams.put("s", sign(signedParams, secretKey));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        signedParams.forEach(body::add);

        try {
            return restClient
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("Falló la llamada a Flow {}: {}", path, e.getMessage());
            throw e;
        }
    }

    /** GET firmado — mismo criterio de firma, los parámetros van en la query string. */
    public Map<String, Object> get(String path, Map<String, String> params, String apiKey, String secretKey) {
        Map<String, String> signedParams = new TreeMap<>(params);
        signedParams.put("apiKey", apiKey);
        signedParams.put("s", sign(signedParams, secretKey));

        return restClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    signedParams.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .body(Map.class);
    }

    private String sign(Map<String, String> sortedParams, String secretKey) {
        StringBuilder toSign = new StringBuilder();
        sortedParams.forEach((key, value) -> toSign.append(key).append(value));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(toSign.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("No se pudo firmar la request a Flow", e);
        }
    }
}

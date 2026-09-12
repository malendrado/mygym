package com.cortesdev.mygym.services;

import com.cortesdev.mygym.models.dto.BrandingSuggestionResponse;
import com.cortesdev.mygym.services.exception.BrandingSuggestionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Suggests a theme color + logo SVG for a new gym based on its name, using
 * Google's Gemini API (free tier). This is only ever a starting point the
 * super-admin sees and can accept/replace before the gym is created — never
 * saved without their review — so a loose/wrong suggestion is low-stakes.
 */
@Service
public class BrandingSuggestionService {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public BrandingSuggestionService(
            RestClient.Builder restClientBuilder,
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.model}") String model,
            ObjectMapper objectMapper) {
        this.restClient =
                restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public BrandingSuggestionResponse suggest(String gymName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BrandingSuggestionException("GEMINI_API_KEY no está configurada en el servidor.");
        }
        String rawText = callGemini(buildPrompt(gymName));
        return parseAndValidate(rawText);
    }

    private String buildPrompt(String gymName) {
        String keys = GymPalette.ALL.stream().map(GymPalette.Entry::key).collect(Collectors.joining(", "));
        return """
                Eres un asistente de branding para gimnasios. Te doy el nombre de un gimnasio y devuelves \
                SOLO un objeto JSON (sin markdown, sin texto adicional, sin backticks) con este formato exacto:
                {"paletteKey": "<una de: %s>", "logoSvg": "<markup SVG completo>"}

                Reglas para paletteKey: elige la que mejor transmita la personalidad del nombre del gimnasio.

                Reglas para logoSvg:
                - Un ícono/monograma simple, viewBox="0 0 100 100", sin texto (nada de <text>), sin degradados, sin fondo (fondo transparente).
                - Como máximo 2 o 3 formas (círculos, rectángulos, paths simples).
                - No usar <script>, imágenes externas, ni atributos de eventos.
                - Tiene que verse bien a tamaño chico, como un ícono.

                Nombre del gimnasio: "%s"
                """
                .formatted(keys, gymName);
    }

    private String callGemini(String prompt) {
        try {
            Map<String, Object> body =
                    Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            JsonNode response = restClient
                    .post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new BrandingSuggestionException("Gemini no devolvió ninguna respuesta.");
            }
            JsonNode textNode = response.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new BrandingSuggestionException("Gemini no devolvió contenido utilizable.");
            }
            return textNode.asText();
        } catch (RestClientException e) {
            throw new BrandingSuggestionException("No pudimos contactar a Gemini para generar la sugerencia.", e);
        }
    }

    private BrandingSuggestionResponse parseAndValidate(String rawText) {
        String cleaned = rawText.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "");
        }

        JsonNode json;
        try {
            json = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new BrandingSuggestionException("La respuesta de Gemini no fue un JSON válido.", e);
        }

        String paletteKey = json.path("paletteKey").asText(null);
        String logoSvg = json.path("logoSvg").asText(null);

        String themeColor = GymPalette.hexForKey(paletteKey);
        if (themeColor == null) {
            throw new BrandingSuggestionException("Gemini sugirió una paleta de color inválida.");
        }

        if (logoSvg == null || logoSvg.isBlank()) {
            throw new BrandingSuggestionException("Gemini no devolvió un logo.");
        }
        String normalizedSvg = logoSvg.strip();
        String validationError = SvgSanitizer.validate(normalizedSvg);
        if (validationError != null) {
            throw new BrandingSuggestionException("El logo generado no es válido: " + validationError);
        }

        return new BrandingSuggestionResponse(themeColor, normalizedSvg);
    }
}

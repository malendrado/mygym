package com.cortesdev.mygym.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un aviso push. El JSON sigue el formato que entiende el service worker de Angular (ngsw-worker.js) sin codigo
 * propio: muestra `notification` y al tocarla ejecuta `data.onActionClick.default`.
 *
 * @param tag        Un aviso nuevo con el mismo tag reemplaza al anterior en vez de apilarse.
 * @param url        Ruta de la app que se abre al tocarla ("/member", "/gym-admin").
 * @param ttlSeconds Cuanto tiempo guarda el servicio de push el aviso si el telefono esta apagado.
 */
public record PushMessage(String title, String body, String tag, String url, int ttlSeconds, Urgency urgency) {

    public enum Urgency {
        HIGH("high"),
        NORMAL("normal");

        private final String header;

        Urgency(String header) {
            this.header = header;
        }

        public String header() {
            return header;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public byte[] toPayload() {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title);
        notification.put("body", body);
        notification.put("icon", "/icon-192.png");
        notification.put("badge", "/pwa/badge-96.png");
        notification.put("tag", tag);
        notification.put("renotify", true);
        notification.put(
                "data",
                Map.of("onActionClick", Map.of("default", Map.of("operation", "navigateLastFocusedOrOpen", "url", url))));
        try {
            return MAPPER.writeValueAsString(Map.of("notification", notification)).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el aviso push", e);
        }
    }
}

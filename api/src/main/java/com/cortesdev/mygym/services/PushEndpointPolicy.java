package com.cortesdev.mygym.services;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Hosts de los servicios de push de los navegadores. El endpoint de una suscripcion lo manda el cliente y el backend
 * le hace un POST: sin esta lista, un usuario podria apuntarlo a cualquier URL (SSRF). Se valida al registrar la
 * suscripcion y otra vez antes de enviar.
 */
public final class PushEndpointPolicy {

    private static final List<String> EXACT_HOSTS = List.of("fcm.googleapis.com", "updates.push.services.mozilla.com");
    private static final List<String> HOST_SUFFIXES =
            List.of(".push.apple.com", ".notify.windows.com", ".push.services.mozilla.com");
    private static final int MAX_LENGTH = 1000;

    private PushEndpointPolicy() {}

    public static boolean isAllowed(String endpoint) {
        if (endpoint == null || endpoint.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
                return false;
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (EXACT_HOSTS.contains(host)) {
                return true;
            }
            for (String suffix : HOST_SUFFIXES) {
                if (host.endsWith(suffix)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Origen ("https://host") que va en el claim `aud` de la firma VAPID. */
    public static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost();
    }
}

package com.cortesdev.mygym.models.dto;

import java.util.UUID;

/** Vista liviana para /admin/gyms (lista del super-admin, GymList.ts) — solo los campos que
 *  esa pantalla realmente pinta (tarjeta + 3 stats agregados). A diferencia de GymResponse
 *  (usado en el detalle de un gym puntual), esta proyección NUNCA toca flowApiKey/
 *  flowSecretKey ni el resto de columnas de texto que no se muestran ahí — ver
 *  GymRepository.findAllSummaries, que arma esto directo en la query en vez de hidratar el
 *  Gym completo (evita descifrar las credenciales de Flow de cada fila para nada). */
public record GymSummaryResponse(
        Long id,
        UUID publicId,
        String name,
        String slug,
        boolean active,
        Integer maxUsers,
        String themeColor,
        String logoSvg) {}

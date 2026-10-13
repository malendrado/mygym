package com.cortesdev.mygym.services;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Punto de entrada para pedir un aviso push desde cualquier servicio. Solo PUBLICA un evento: el envio real
 * ({@link PushDispatcher}) ocurre DESPUES de confirmar la transaccion del negocio y en un hilo aparte, asi un push
 * lento o caido nunca frena ni revierte una reserva (mismo patron que MembershipPaidEvent).
 */
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    /** A quienes y que se envia. Se arma recien al momento del envio (ya con la transaccion confirmada). */
    public record Delivery(Collection<Long> userIds, PushMessage message) {}

    /** Evento interno: el dispatcher lo escucha. */
    public record PushRequested(Supplier<Delivery> delivery) {}

    private final ApplicationEventPublisher publisher;

    public void notifyUsers(Collection<Long> userIds, PushMessage message) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> ids = List.copyOf(userIds);
        publisher.publishEvent(new PushRequested(() -> new Delivery(ids, message)));
    }

    /**
     * Como {@link #notifyUsers}, pero destinatarios y texto se calculan al enviar (con la base ya actualizada). Si el
     * supplier devuelve null no se envia nada.
     */
    public void notifyLater(Supplier<Delivery> delivery) {
        publisher.publishEvent(new PushRequested(delivery));
    }
}

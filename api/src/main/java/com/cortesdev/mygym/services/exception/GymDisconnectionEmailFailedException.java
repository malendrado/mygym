package com.cortesdev.mygym.services.exception;

/** No se pudo enviar el email con el detalle de los socios a uno o más administradores del
 *  gimnasio — la desvinculación NO se ejecuta en ese caso (ni se guarda auditoría ni se borra
 *  nada): no podemos borrar unilateralmente la cartera de clientes de un gym sin garantizar que
 *  su dueño se quedó con una copia. Ver la conversación con el usuario del 2026-09-23. */
public class GymDisconnectionEmailFailedException extends RuntimeException {

    public GymDisconnectionEmailFailedException(String adminEmail) {
        super("No pudimos enviarle el detalle de sus socios a " + adminEmail
                + " — no se ejecutó la desvinculación. Intenta nuevamente.");
    }
}

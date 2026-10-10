package com.cortesdev.mygym.services.exception;

/** El gym apagó "Los socios pueden ver quién va a cada clase" (Gym.showAttendeesToMembers) —
 *  solo afecta a la vista del socio; el panel del admin y la TV nunca pasan por acá. */
public class AttendeesHiddenException extends RuntimeException {

    public AttendeesHiddenException() {
        super("Este gimnasio no muestra la lista de asistentes a sus socios.");
    }
}

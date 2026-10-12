package com.cortesdev.mygym.services;

/** Se publica cuando un pago (Flow o manual) deja al socio con un período nuevo. FixedClassService
 *  lo escucha DESPUÉS del commit para reservar sus clases fijas — nunca puede revertir el pago. */
public record MembershipPaidEvent(Long memberId) {}

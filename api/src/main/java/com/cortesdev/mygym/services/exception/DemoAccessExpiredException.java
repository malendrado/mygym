package com.cortesdev.mygym.services.exception;

/** El acceso a la demo comercial (Role.DEMO_ADMIN) venció (48h desde que se otorgó) — la fila ya
 *  se borró (ver DemoAccessService). El frontend redirige al formulario de contacto en vez de
 *  mostrar el error genérico de "cuenta no registrada". */
public class DemoAccessExpiredException extends RuntimeException {

    public DemoAccessExpiredException() {
        super("Tu acceso a la demo venció.");
    }
}

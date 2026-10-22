-- La constancia de firma (V58) ya no se puede modificar ni borrar fila por fila, pero TRUNCATE no activa triggers por fila y la vaciaba entera.
-- Se prueba en un Postgres limpio: sin este trigger, TRUNCATE gym_contract_acceptance borra todas las constancias.
-- Reutiliza la funcion de V58, que solo lanza la excepcion.
CREATE TRIGGER trg_gym_contract_acceptance_no_truncate
    BEFORE TRUNCATE ON gym_contract_acceptance
    FOR EACH STATEMENT EXECUTE FUNCTION gym_contract_acceptance_immutable();

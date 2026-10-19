-- Los gimnasios que ya existian antes del cobro (V55) quedan EXENTOS y SIN plan asignado: mygym todavia no empieza a operar el
-- cobro y los administra a mano mas adelante (Activar gimnasio en su ficha, que les asigna plan y fecha de inicio).
-- No toca los gimnasios en estado NOT_STARTED: esos se crearon despues, esperando su primer pago.
-- Con anchor_date NULL y paid_periods 0 el gimnasio queda "sin iniciar" para el cobro; mientras siga exento nunca se pausa ni recibe avisos.
DO $$
DECLARE
    updated integer;
BEGIN
    UPDATE gym_subscription
       SET exempt = TRUE,
           billing_status = 'EXEMPT',
           plan_id = NULL,
           price_override_clp = NULL,
           max_users_override = NULL,
           anchor_date = NULL,
           paid_periods = 0,
           pending_plan_id = NULL,
           pending_price_override_clp = NULL,
           pending_plan_from = NULL,
           suspended_at = NULL,
           last_notice_on = NULL,
           updated_at = now()
     WHERE billing_status <> 'NOT_STARTED';
    GET DIAGNOSTICS updated = ROW_COUNT;
    RAISE NOTICE 'V56: % gimnasios quedaron exentos y sin plan', updated;
END
$$;

-- uk_reservation_slot (V2) covered ALL rows regardless of status, so once a
-- member cancelled a reservation the (gym_block_id, member_id, class_date)
-- combo stayed taken forever — re-booking the same slot later hit the
-- constraint with a raw 500 (ReservationService's own duplicate check only
-- looks at BOOKED rows, so it never caught this before the insert).
-- A partial unique index scoped to BOOKED rows keeps "one active reservation
-- per member per slot" while letting cancelled history coexist.
ALTER TABLE reservation DROP CONSTRAINT uk_reservation_slot;

CREATE UNIQUE INDEX uk_reservation_slot_booked ON reservation (gym_block_id, member_id, class_date)
    WHERE status = 'BOOKED';

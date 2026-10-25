-- L-5: put the "no overlapping appointments per stylist" rule under version control.
-- Production already has this constraint (no_overlapping_appointments) and the appointment_time_range()
-- function, created by hand outside the migrations. A new environment built from the migrations alone
-- (a fresh database, Testcontainers) had neither, so the 23P01 handler in AppointmentService never fired
-- and the native query existsOverlappingAppointment() failed on the missing function.
-- Everything below is guarded, so on production this migration changes nothing.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- The time range an appointment occupies: [start, start + duration minutes). IMMUTABLE so it can be used in
-- an index/constraint expression (minute arithmetic on timestamptz doesn't depend on the session time zone).
-- Created only if missing, so a differently written production version is never replaced.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'appointment_time_range') THEN
        EXECUTE $fn$
            CREATE FUNCTION appointment_time_range(start_at timestamptz, duration_minutes integer)
            RETURNS tstzrange
            LANGUAGE sql IMMUTABLE
            AS 'SELECT tstzrange(start_at, start_at + duration_minutes * interval ''1 minute'')'
        $fn$;
    END IF;
END $$;

-- One stylist can't be in two non-cancelled appointments whose time ranges overlap. Appointments with no
-- stylist assigned are exempt (stylist_id NULL never conflicts).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'appointments'::regclass AND contype = 'x') THEN
        ALTER TABLE appointments
            ADD CONSTRAINT no_overlapping_appointments
            EXCLUDE USING gist (
                stylist_id WITH =,
                appointment_time_range(scheduled_at, duration_minutes) WITH &&
            ) WHERE (status <> 'CANCELLED' AND stylist_id IS NOT NULL);
    END IF;
END $$;

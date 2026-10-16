-- Forgotten-password codes. Like registration codes they live under the verification scope of the
-- account's credential subject; an account without stored credentials (created before V3) can recover too.
CREATE TABLE password_resets (
    subject varchar(128) PRIMARY KEY REFERENCES accounts (provider_subject) ON DELETE CASCADE,
    code_hash char(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    resend_available_at timestamptz NOT NULL,
    delivery_id uuid NOT NULL
);
ALTER TABLE password_resets ENABLE ROW LEVEL SECURITY;
CREATE POLICY scoped_all ON password_resets
    USING (kairos_security.setting('scope')='verification' AND subject=kairos_security.setting('identity'))
    WITH CHECK (kairos_security.setting('scope')='verification' AND subject=kairos_security.setting('identity'));
GRANT SELECT,INSERT,UPDATE,DELETE ON password_resets TO "${runtimeUser}";

-- The subject of an account that may sign in; reveals no more than identity_conflict already does.
CREATE FUNCTION public.password_reset_subject(email text) RETURNS varchar
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT a.provider_subject FROM public.accounts a
        WHERE a.email=password_reset_subject.email AND public.valid_staff(a.id,a.tenant_id,a.tenant_role)
$$;

-- Ends every session of the account whose reset code was just proven in this transaction.
CREATE FUNCTION public.revoke_reset_sessions(at_time timestamptz) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE revoked uuid;
BEGIN
    IF kairos_security.setting('scope')<>'verification' OR NOT EXISTS(
            SELECT 1 FROM public.password_resets r WHERE r.subject=kairos_security.setting('identity')) THEN
        RETURN false;
    END IF;
    UPDATE public.accounts a SET authentication_cutoff=at_time
        WHERE a.provider_subject=kairos_security.setting('identity') RETURNING a.id INTO revoked;
    IF revoked IS NULL THEN RETURN false; END IF;
    DELETE FROM public.spring_session s WHERE s.principal_name=revoked::text;
    RETURN true;
END $$;
REVOKE EXECUTE ON FUNCTION public.password_reset_subject(text),public.revoke_reset_sessions(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.password_reset_subject(text),public.revoke_reset_sessions(timestamptz) TO "${runtimeUser}";

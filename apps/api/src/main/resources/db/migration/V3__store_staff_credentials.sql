-- Kairos stores staff credentials itself instead of delegating them to an external identity provider.
-- Like Spring Session, credentials are authentication infrastructure read before any RLS scope exists.
CREATE TABLE staff_identities (
    subject varchar(128) PRIMARY KEY,
    email varchar(200) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    email_verified boolean NOT NULL DEFAULT false,
    verification_code_hash char(64),
    verification_code_expires_at timestamptz
);
GRANT SELECT,INSERT,UPDATE,DELETE ON staff_identities TO "${runtimeUser}";

-- Existing sessions reference provider sessions that no longer exist.
DELETE FROM SPRING_SESSION;

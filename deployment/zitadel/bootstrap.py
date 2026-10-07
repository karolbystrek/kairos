"""Provision only the provider permissions Kairos uses; never expose bootstrap PAT to API."""
import json
import os
from pathlib import Path
import urllib.error
import urllib.request

TOKEN = Path("/credentials/kairos.pat")
if TOKEN.exists() and TOKEN.read_text().strip():
    print("ZITADEL API credentials already provisioned.")
    raise SystemExit(0)

admin = Path("/bootstrap/admin.pat").read_text().strip()

def call(path, body=None, allow_conflict=False):
    request = urllib.request.Request("http://zitadel:8080" + path,
        data=None if body is None else json.dumps(body).encode(),
        headers={"Authorization": "Bearer " + admin, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if allow_conflict and error.code == 409:
            return {}
        # Provider bodies can contain credentials; never print them.
        raise RuntimeError(f"ZITADEL bootstrap failed: {path}, HTTP {error.code}") from None

org = call("/management/v1/orgs/me")["org"]["id"]
call("/v2/users/new", {"organizationId": org, "userId": "kairos-api",
    "username": "kairos-api", "machine": {"name": "Kairos API"}}, allow_conflict=True)
call("/management/v1/orgs/me/members", {"userId": "kairos-api", "roles": ["ORG_USER_MANAGER"]}, allow_conflict=True)
call("/admin/v1/members", {"userId": "kairos-api", "roles": ["IAM_LOGIN_CLIENT"]}, allow_conflict=True)
# ZITADEL v4.19.4 requires this field; its no-expiry default is year 9999.
result = call("/v2/users/kairos-api/pats", {"expirationDate": "9999-12-31T23:59:59Z"})
token = result["token"]
if not token:
    raise RuntimeError("ZITADEL did not return a service token")
os.umask(0o077)
temporary = TOKEN.with_suffix(".tmp")
temporary.write_text(token)
temporary.replace(TOKEN)
print("ZITADEL API credentials provisioned (non-expiring PAT; rotate or revoke through provider administration).")

# Bootstrap an Ubuntu VM

## Contents

- [Bootstrap an Ubuntu VM](#bootstrap-an-ubuntu-vm)
- [Contents](#contents)
- [Workstation preparation](#workstation-preparation)
- [SSH aliases on your Mac](#ssh-aliases-on-your-mac)
- [Run the bootstrap](#run-the-bootstrap)
- [Verify, then stop](#verify-then-stop)
- [Prepare production configuration after bootstrap verification](#prepare-production-configuration-after-bootstrap-verification)
- [Repository validation](#repository-validation)

This prepares one Ubuntu 24.04 x86-64 host, including the provisioned Lightsail
instance. Run it from a trusted checkout on your Mac or Linux workstation.
The workstation architecture does not need to match the VM.

The playbook installs Docker Engine/Compose and host tools, enables Docker at
boot, creates the `kairos-deploy` deployment account, installs its restricted public SSH
key, and creates private `/srv/kairos/releases`, `/srv/kairos/tls` and application
keys. It uses the existing `setup.sh` and does not start Kairos.

## Workstation preparation

Install Python 3.11 or newer if needed, then create an isolated Ansible environment:

```sh
python3 -m venv "$HOME/.local/share/kairos-ansible"
. "$HOME/.local/share/kairos-ansible/bin/activate"
python -m pip install 'ansible-core==2.19.14'
```

Only `ansible-core` is needed; the playbook uses built-in modules, with no roles,
external collections or agent installed on the VM. See
[Ansible installation](https://docs.ansible.com/projects/ansible/latest/installation_guide/intro_installation.html).

Use the existing Lightsail administrator SSH key to connect as `ubuntu`.
Store the downloaded private key outside the checkout and set its mode to `0600`.
The administrator needs working sudo; Lightsail's initial Ubuntu account normally
provides it. If sudo requires a password, add `--ask-become-pass` to the playbook.

Create a separate deployment key, outside the checkout, if you do not already
have one. Do not overwrite an existing key:

```sh
ssh-keygen -t ed25519 -f "$HOME/.ssh/kairos-deploy" -C kairos-deploy
```

This automation installs only its `.pub` file. Keep the private key on the
workstation. The existing Actions uploader does not prompt for a passphrase,
so its eventual deployment key must work non-interactively. Protect an
unencrypted deployment key as a production credential. Docker access grants
host-level authority.

Before connecting from your Mac, open Lightsail's browser SSH console and obtain
the host's Ed25519 fingerprint and public key:

```sh
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
sudo cat /etc/ssh/ssh_host_ed25519_key.pub
```

Add that independently obtained public key to your workstation's
`~/.ssh/known_hosts` using the static IPv4 address as the host field. For example,
the line has the form `52.58.250.75 ssh-ed25519 <public-key-from-console>`.
Preserve existing entries. Do not substitute an unverified `ssh-keyscan` result.
Ansible is run with strict host-key checking below.

Keep the provider console recovery path available. This bootstrap does not change
Lightsail firewall rules or the administrator's SSH authentication settings.

## SSH aliases on your Mac

Keep the administrator alias mapped to the existing `ubuntu` account. The
second alias works after bootstrap creates `kairos-deploy`:

```sshconfig
Host kairos
    HostName 52.58.250.75
    User ubuntu
    IdentityFile ~/.ssh/LightsailDefaultKey-eu-central-1.pem
    IdentitiesOnly yes
    StrictHostKeyChecking yes
    ControlMaster auto
    ControlPath ~/.ssh/cm-%C
    ControlPersist 5m

Host kairos-deploy
    HostName 52.58.250.75
    User kairos-deploy
    IdentityFile ~/.ssh/kairos-deploy
    IdentitiesOnly yes
    StrictHostKeyChecking yes
```

Bootstrap does not create another administrator, rename `ubuntu`, modify its
keys or change its sudo permissions. Use `ssh kairos` for administration and
`ssh kairos-deploy 'docker version'` for deployment checks after bootstrap.

## Run the bootstrap

Copy the example inventory to a private location outside the checkout and replace
`YOUR_STATIC_IPV4` with the instance's static address (`52.58.250.75` for the
instance shown in this setup):

```sh
mkdir -p "$HOME/.config/kairos"
chmod 0700 "$HOME/.config/kairos"
cp deployment/inventory.example.ini "$HOME/.config/kairos/inventory.ini"
chmod 0600 "$HOME/.config/kairos/inventory.ini"
```

From the repository root, with the Ansible environment active, run the command
below. Replace the Lightsail key path with your downloaded administrator key:

```sh
ansible-playbook -i "$HOME/.config/kairos/inventory.ini" deployment/bootstrap.yml \
  --ssh-common-args='-o StrictHostKeyChecking=yes' \
  -e "ansible_ssh_private_key_file=$HOME/.ssh/LightsailDefaultKey-eu-central-1.pem" \
  -e "kairos_deploy_public_key_file=$HOME/.ssh/kairos-deploy.pub"
```

No application credentials or private-key contents go into command arguments.
The playbook rejects unsupported OS/architecture, invalid deployment public keys
and conflicting Docker packages before package changes. It refuses to uninstall
existing Docker/container tooling; resolve such a conflict as separate maintenance.
Docker installation follows its
[official Ubuntu apt repository instructions](https://docs.docker.com/engine/install/ubuntu/).

The `kairos-deploy` account has a locked password and Docker group access, with password
and keyboard-interactive SSH authentication disabled. The installed key disables
PTY allocation and agent, TCP and X11 forwarding. SCP and ordinary non-interactive
SSH commands remain available to the existing release uploader. Administrator
access continues through `ubuntu`; final root/password SSH policy and restricted
network ingress are separate production preparation.

If you already configured the `kairos` SSH alias above, your inventory can be:

```ini
[kairos]
kairos-production ansible_host=kairos

[kairos:vars]
ansible_python_interpreter=/usr/bin/python3
```

Do not keep an explicit `ansible_user` that disagrees with the alias. The
simplified command then uses SSH configuration for the administrator key:

```sh
ansible-playbook -i "$HOME/.config/kairos/inventory.ini" deployment/bootstrap.yml \
  -e "kairos_deploy_public_key_file=$HOME/.ssh/kairos-deploy.pub"
```

## Verify, then stop

Connect as the deployment account using its separate private key:

```sh
ssh -o StrictHostKeyChecking=yes -i "$HOME/.ssh/kairos-deploy" kairos-deploy@52.58.250.75 \
  'docker info --format "{{.ServerVersion}}"; docker compose version; python3 --version'
```

The playbook also verifies Docker access and the Compose options required by
`deploy.sh`. Through administrator access, verify Docker is enabled for reboot:

```sh
systemctl is-enabled docker
systemctl is-active docker
```

Run the same full playbook again. Existing packages are not upgraded; production
configuration and all five application key contents are preserved. Temporary
setup tasks still report changes because they allocate and remove a private
workspace; zero changes in the recap is not the acceptance criterion. Invalid
or partial key sets stop bootstrap rather than being replaced. Public-key
installation adds the supplied key without revoking prior keys; intentional key
rotation/revocation is separate maintenance.

On a fresh host, `/srv/kairos/production.env.example` is mode `0600` and contains
repository-owned production defaults from `deployment/production.env.example`. Bootstrap deliberately does
not create `/srv/kairos/production.env`. If that file already exists, it is
preserved byte-for-byte. Reruns refresh only the non-secret example, including
on hosts previously bootstrapped with local defaults. Keys are stored under `/srv/kairos/secrets` with directory
mode `0700` and file mode `0400`; release and TLS directories use `0700`.

## Prepare production configuration after bootstrap verification

After merging the template change, update your trusted main checkout and rerun
bootstrap (the SSH-alias command above). For an already prepared VM, use
`--tags configuration` to refresh configuration inputs without host/package tasks.
The existing production environment and application keys are preserved.

Use administrator SSH for the interactive editor; the deployment key deliberately
forbids a PTY:

```sh
ssh kairos
sudo -iu kairos-deploy
cd /srv/kairos
if [ ! -e production.env ]; then
  (umask 077; cp production.env.example production.env)
fi
chmod 0600 production.env
nano production.env
```

Fill the four blank passwords (`POSTGRES_PASSWORD`, `REDIS_PASSWORD`,
`ZITADEL_DB_PASSWORD`, `ZITADEL_FIRSTINSTANCE_ORG_HUMAN_PASSWORD`) with different
strong values. Set `VAPID_SUBJECT` to your `mailto:` contact and set a deliberate
`ZITADEL_FIRSTINSTANCE_ORG_MACHINE_PAT_EXPIRATIONDATE` (UTC ISO 8601), with renewal
scheduled before expiry. Do not leave these fields blank at deployment.
If `production.env` already exists, edit those settings and reconcile public
settings against the example; do not overwrite existing credentials with a new
copy. Hostnames and browser origins must match Cloudflare and the GitHub build
variables. Deployment supplies the blank registry/release fields.

The template is safe to commit; the live environment, keys and certificates are
not. No password generation, container start or deployment occurs in bootstrap.

Stop here and confirm bootstrap and the second run before proceeding. Production
passwords/configuration, registry credentials, Cloudflare DNS/certificate/cache
rules, Lightsail firewall restrictions, GitHub Environment protection and the
first application deployment are later steps. Existing launch gates, including
RLS, backups/restore and live acceptance, still apply. Do not run `reset.sh` during
host preparation or ordinary production maintenance: it removes volumes and keys.

## Repository validation

With the Ansible environment active, run:

```sh
ansible-playbook -i deployment/inventory.example.ini deployment/bootstrap.yml --syntax-check
sh tests/bootstrap-test.sh
sh tests/setup-test.sh
python3 -m unittest discover -s deployment -p 'test_*.py'
git diff --check
```

The bootstrap test executes only the real `configuration` tasks on the local
connection in a temporary directory, with privilege escalation disabled. It
checks generation, permissions, configuration/key preservation and refusal of
invalid/partial keys. It does not install packages, touch SSH/Docker, or prove
real Ubuntu/SSH/reboot behavior; those require the operator's VM verification.
Use the full playbook for VM preparation, without task tags.

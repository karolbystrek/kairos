# kairos
Virtual pager system for restaurants. Scan QR to track your order in real-time.

## Local Development

Follow these steps to run the application locally:

### 1. Configure Environment Variables
Copy the example environment file to create your local configuration:
```bash
cp .env.example .env
```

### 2. Create Local Cryptographic Keys

Generate the ignored local JWT signing keys and webhook signing-secret
encryption key with OpenSSL:
```bash
./scripts/init-jwt-keys.sh
./scripts/init-webhook-encryption-key.sh
```

Both commands are idempotent and reuse valid existing key material. Production
deployments must provide these secrets through externally managed resources.

### 3. Build and Start the Development Environment
Run Docker Compose with Watch enabled. Keep this terminal open while developing so frontend source changes use Next.js Fast Refresh and backend source changes are compiled before Spring Boot restarts:
```bash
docker compose up --build --watch
```

### 4. Access the Applications
Once the containers are up and running, you can access the applications locally via secure HTTPS:

* **Customer Frontend:** [https://app.localhost](https://app.localhost)
* **Panel Frontend:** [https://panel.localhost](https://panel.localhost)
* **Backend API:** [https://api.localhost](https://api.localhost)

The production database schema is intentionally empty. Register the first tenant, location, and administrator through the staff panel; production migrations do not install demo records.

The current walking vertical slice is for local development. Staff authentication and tenant/location authorization are implemented; real-time updates and the remaining production hardening are still required before deployment.

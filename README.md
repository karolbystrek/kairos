# kairos
Virtual pager system for restaurants. Scan QR to track your order in real-time.

## Local Development

Follow these steps to run the application locally:

### 1. Configure Environment Variables
Copy the example environment file to create your local configuration:
```bash
cp .env.example .env
```

### 2. Build and Start the Development Environment
Run Docker Compose with Watch enabled. Keep this terminal open while developing so frontend source changes use Next.js Fast Refresh and backend source changes are compiled before Spring Boot restarts:
```bash
docker compose up --build --watch
```

### 3. Access the Applications
Once the containers are up and running, you can access the applications locally via secure HTTPS:

* **Customer Frontend:** [https://app.localhost](https://app.localhost)
* **Panel Frontend:** [https://panel.localhost](https://panel.localhost)
* **Backend API:** [https://api.localhost](https://api.localhost)

The database schema is intentionally empty. Create a tenant and at least one associated location in PostgreSQL before using the staff panel; production migrations do not install demo records.

The current walking vertical slice is for local development and leaves order-management endpoints unauthenticated. Authentication, tenant/location authorization, and real-time updates are the next development phases and are required before deployment.

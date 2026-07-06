# kairos
Virtual pager system for restaurants. Scan QR to track your order in real-time.

## Local Development

Follow these steps to run the application locally:

### 1. Configure Environment Variables
Copy the example environment file to create your local configuration:
```bash
cp .env.example .env
```

### 2. Build and Start the Containers
Run Docker Compose to build the application images and start the services:
```bash
docker compose up --build
```

### 3. Access the Applications
Once the containers are up and running, you can access the applications locally via secure HTTPS:

* **Customer Frontend:** [https://customer.localhost](https://customer.localhost)
* **Panel Frontend:** [https://panel.localhost](https://panel.localhost)
* **Backend API:** [https://api.localhost](https://api.localhost)

# Architecture and Requirements: Virtual Pager System

## 1. Tech Stack
* **Client Frontend:** Next.js (PWA). Optional App Clip (iOS) / Instant App (Android) integration. No install. Welcome screen unblock vibrate/audio API. Wake Lock API stop screen sleep.
* **Staff Frontend:** Next.js (Web/PWA).
* **Backend:** Java 25, Spring Boot 4.
* **Database:** PostgreSQL. Shared Database/Shared Schema pattern. Data isolate via Row Level Security (RLS) on `tenant_id` column.
* **Cache & Real-time:** Redis. WebSocket session store. Pub/Sub pattern for distributed notification.

## 2. Data Model (Core Entities)
* **Tenant:** `id`, `name`, `api_key_hash`, `webhook_url`.
* **Order:** `id` (UUID4), `tenant_id`, `display_name` (a name specified by the staff), `status`, `created_at`, `completed_at`.
* **OrderHistory (Event Ledger):** `id`, `order_id`, `status_from`, `status_to`, `timestamp`. Append-only table.
* **OutboxEvent:** `id`, `tenant_id`, `payload` (JSON status change), `status` (PENDING/SENT/FAILED), `retry_count`.

## 3. Order Lifecycle & State Management
1. **CREATED:** Order placed. POS or staff panel create record. Generate QR code with UUID (`https://app.domain.com/order/{uuid}`).
2. **IN_PREPARATION:** Client scan QR. Click "Track". WebSocket open.
3. **READY_FOR_PICKUP:** Staff change status. Backend send STOMP message. Client phone vibrate.
4. **COMPLETED:** Food delivered. Backend close WebSocket. Next QR scan render static "Order completed" view.
5. **CANCELED:** Order cancel. WebSocket close with message.

## 4. Communication & Security
* **IDOR Protection:** QR codes use UUID4. Client URL guess impossible.
* **Staff Auth:** JSON Web Token (JWT).
* **POS API Auth:** HTTP header `Authorization: Bearer <API_KEY>`. Database bcrypt verify.
* **WebSocket (Client):** Read-only one-way connect. Subscribe private `/topic/orders/{uuid}` channel.
* **Webhooks (POS):** Transactional Outbox pattern. Order status update and `OutboxEvent` save in one DB transaction. Async worker send HTTP payload to external system.

## 5. Resilience Scenarios (Defensive Programming)
* **Network loss (Client):** Client app use exponential backoff for WebSocket reconnect. After reconnect, send REST GET for current status (verify status change during outage).
* **No PWA/Push support:** System still work. WebSocket and Wake Lock API keep notification alive.
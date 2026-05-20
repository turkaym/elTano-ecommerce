# El Tano Ecommerce

Production-ready ecommerce monorepo for **El Tano**, built with a React/Vite storefront and a Spring Boot API. The project supports catalog browsing, cart and checkout flows, admin operations, PostgreSQL persistence, Docker-based production deployment, and an isolated Nginx site configuration for `frutoseltano.com.ar`.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Local Development](#local-development)
- [Environment Variables](#environment-variables)
- [Verification](#verification)
- [Production Deployment](#production-deployment)
- [Security Notes](#security-notes)

## Overview

This repository contains the customer-facing storefront and the backend API required to operate the El Tano ecommerce experience.

| Area | Description |
| --- | --- |
| Storefront | React + Vite web app with catalog navigation, product detail flows, cart, and checkout pages. |
| Admin API | Spring Boot endpoints protected with Basic Auth and CSRF for catalog/order administration. |
| Persistence | PostgreSQL database with Flyway migrations and JPA repositories. |
| Payments | Mercado Pago integration hooks and checkout return URLs. |
| Deployment | Docker Compose production scaffold with host Nginx reverse proxy support. |

## Tech Stack

### Frontend

- React 19
- React Router
- TypeScript
- Vite
- Vitest
- ESLint
- Nginx for production static serving

### Backend

- Java 21
- Spring Boot 3.3
- Spring Web, Security, Validation, Data JPA
- PostgreSQL
- Flyway
- Maven Wrapper

### Infrastructure

- Docker and Docker Compose
- PostgreSQL 16 container for production
- VPS-hosted Nginx reverse proxy
- Certbot/Let's Encrypt for TLS

## Repository Structure

```text
.
├── backend/                 # Spring Boot API
├── frontend/                # React/Vite client
├── deploy/                  # Production deployment templates
│   ├── .env.production.example
│   └── nginx/eltano.conf
├── docs/                    # Release and operational documentation
├── docker-compose.prod.yml  # Production Docker Compose definition
└── README.md
```

> Internal planning artifacts such as `sdd/` and `.atl/` are not required to run or deploy the application. See [Security Notes](#security-notes).

## Prerequisites

- Java 21 available in `PATH`
- Node.js and npm available in `PATH`
- Docker and Docker Compose for production deployment
- PostgreSQL for local backend development, unless using a containerized database

## Local Development

### Frontend

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

The Vite development server uses the values defined in `frontend/.env`.

### Backend

```bash
cd backend
cp .env.example .env
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
cd backend
Copy-Item .env.example .env
.\mvnw.cmd spring-boot:run
```

The backend expects PostgreSQL and the required security/payment variables to be configured before startup.

## Environment Variables

Use the example files as the source of truth for local and production configuration:

- `backend/.env.example`
- `frontend/.env.example`
- `deploy/.env.production.example`

### Backend runtime variables

| Variable | Purpose |
| --- | --- |
| `DB_URL` | JDBC connection string for PostgreSQL. |
| `DB_USER` | PostgreSQL username. |
| `DB_PASSWORD` | PostgreSQL password. |
| `JWT_SECRET` | Secret used for JWT signing/validation. |
| `ADMIN_ENABLED` | Enables or disables admin endpoints. |
| `ADMIN_BASIC_USER` / `ADMIN_BASIC_PASS` | Basic Auth credentials for admin endpoints. |
| `STOREFRONT_BASIC_USER` / `STOREFRONT_BASIC_PASS` | Basic Auth credentials for protected storefront use cases. |
| `EXPIRED_ADMIN_BASIC_USER` / `EXPIRED_ADMIN_BASIC_PASS` | Expired admin credentials used to validate security behavior. |
| `CORS_ADMIN_ALLOWED_ORIGINS` | Comma-separated allowed origins for admin requests. |
| `CORS_STOREFRONT_ALLOWED_ORIGINS` | Comma-separated allowed origins for storefront requests. |
| `PRODUCT_IMAGE_UPLOAD_DIR` | Filesystem path used for product image uploads. |
| `PRODUCT_IMAGE_PUBLIC_PATH` | Public URL path for product images. |
| `MP_*` | Mercado Pago access token, webhook, signature, and checkout URL settings. |

### Frontend build variables

| Variable | Purpose |
| --- | --- |
| `VITE_API_URL` | Backend base URL. Leave empty in production to use same-origin `/api` through Nginx. |
| `VITE_CHECKOUT_MVP_ENABLED` | Enables the checkout MVP flow. |
| `VITE_CHECKOUT_PAYMENT_ENABLED` | Enables payment-related checkout behavior. |
| `VITE_STOREFRONT_VARIANT_FLOW_ENABLED` | Enables the current storefront variant flow. |
| `VITE_ADMIN_ENABLED` | Enables admin UI routes/features. |
| `VITE_ADMIN_BASIC_USER` / `VITE_ADMIN_BASIC_PASS` | Existing admin UI Basic Auth build-time credentials. Avoid setting these in production unless explicitly accepted as a temporary risk. |

## Verification

Run verification from a clean working tree before release or deployment.

### Backend

Windows:

```powershell
cd backend
.\mvnw.cmd -B clean verify
```

Unix/macOS:

```bash
cd backend
./mvnw -B clean verify
```

### Frontend

```bash
cd frontend
npm ci
npm test -- --watch=false
npm run lint
npm run build
```

### Release gate

Release readiness is tracked in `docs/release-readiness-checklist.md`. A release is blocked unless the required gates are green and the final decision is marked as ready.

## Production Deployment

The production scaffold assumes the VPS already runs host-level Nginx for other websites. This repository only provides an isolated site configuration for `frutoseltano.com.ar` and `www.frutoseltano.com.ar`.

### 1. Clone the repository on the VPS

```bash
mkdir -p /home/deploy/apps/eltano
cd /home/deploy/apps/eltano
git clone https://github.com/turkaym/elTano-ecommerce.git app
cd app
git checkout feat/storefront-redesign
```

### 2. Configure production secrets

```bash
cp deploy/.env.production.example .env
```

Edit `.env` and replace every placeholder with real production values. Do not commit `.env`.

### 3. Build and start the application

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

The Compose stack starts:

- `postgres` on the internal Docker network
- `backend` bound to `127.0.0.1:8080`
- `frontend` bound to `127.0.0.1:3000`

### 4. Install the Nginx site configuration

```bash
sudo cp deploy/nginx/eltano.conf /etc/nginx/sites-available/eltano.conf
sudo ln -s /etc/nginx/sites-available/eltano.conf /etc/nginx/sites-enabled/eltano.conf
sudo nginx -t
sudo systemctl reload nginx
```

The site config is scoped to `frutoseltano.com.ar` and `www.frutoseltano.com.ar`; it does not replace other Nginx sites.

### 5. Enable TLS

After DNS resolves to the VPS:

```bash
sudo certbot --nginx -d frutoseltano.com.ar -d www.frutoseltano.com.ar
sudo nginx -t
sudo systemctl reload nginx
```

## Security Notes

- Never commit real `.env` files, database passwords, API tokens, Mercado Pago secrets, or production Basic Auth credentials.
- `VITE_*` variables are bundled into browser assets. Do not put private production secrets in frontend build variables.
- `VITE_ADMIN_BASIC_USER` and `VITE_ADMIN_BASIC_PASS` are a known temporary risk if used in production because they become visible client-side.
- Replace `MP_WEBHOOK_SECRET` with a strong random value before enabling Mercado Pago webhooks.
- Internal planning folders such as `sdd/` and `.atl/` are not required for runtime or deployment. They are usually better kept out of a public production repository unless the team intentionally wants to publish planning history.

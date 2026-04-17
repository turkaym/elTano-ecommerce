# elTano_ecommerce MVP

Monorepo inicial para el MVP de elTano con frontend en React + Vite (TypeScript) y backend en Spring Boot.

## Desarrollo local

### Frontend

1. Entrar a `frontend/`.
2. Instalar dependencias:
   ```bash
   npm install
   ```
3. Copiar variables de entorno:
   ```bash
   cp .env.example .env
   ```
4. Levantar servidor de desarrollo:
   ```bash
   npm run dev
   ```

### Backend

1. Entrar a `backend/`.
2. Configurar variables de entorno del backend.
3. Levantar en modo desarrollo:
   ```bash
   ./mvnw spring-boot:run
   ```
   En Windows PowerShell:
   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

## Variables de entorno

### Frontend

- `VITE_API_URL`: URL base del backend (ejemplo: `http://localhost:8080`).

### Backend

- `DB_URL`: URL JDBC de PostgreSQL.
- `DB_USER`: usuario de PostgreSQL.
- `DB_PASSWORD`: password de PostgreSQL.
- `JWT_SECRET`: secreto para firma/validacion de JWT.
- `MP_ACCESS_TOKEN`: access token de Mercado Pago.

## Arquitectura inicial

### Estructura del monorepo

- `frontend/`: app cliente React + Vite + TypeScript.
- `backend/`: API REST en Spring Boot con seguridad, validacion y persistencia.

### Backend por modulos

Paquete base `com.eltano.ecommerce` organizado para crecer por dominio:

- `config`: configuracion transversal (ejemplo: seguridad).
- `common`: componentes compartidos (ejemplo: health endpoint).
- `catalog`: dominio de catalogo de productos.
- `orders`: dominio de ordenes.
- `users`: dominio de usuarios.

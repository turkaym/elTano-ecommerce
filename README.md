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

## Verificacion completa (contrato canonico)

### Prerrequisitos

- Java 21 disponible en `PATH` (`java -version`).
- Node.js y npm disponibles en `PATH` (`node -v` y `npm -v`).

Si falta alguno de los prerrequisitos, la verificacion fallara de forma inmediata (comando no encontrado / version incompatible). Instala la herramienta faltante y reintenta.

### Secuencia exacta (orden obligatorio)

1. Backend (politica canonica wrapper-first cross-platform):
   ```bash
   # Windows (PowerShell/CMD)
   cd backend && .\mvnw.cmd -B clean verify

   # Unix/macOS (bash/zsh)
   cd backend && ./mvnw -B clean verify
   ```
   `mvn -B clean verify` se permite como alternativa opcional **solo** si Maven esta instalado en PATH.
2. Frontend:
   ```bash
   cd frontend && npm ci && npm test -- --watch=false && npm run lint && npm run build
   ```

### Criterio de exito por etapa

- `./mvnw.cmd -B clean verify` (Windows) o `./mvnw -B clean verify` (Unix/macOS) termina con exit code `0`.
- `mvn -B clean verify` puede usarse de forma opcional cuando existe Maven en PATH, con el mismo criterio de exit code `0`.
- `npm ci`, `npm test -- --watch=false`, `npm run lint` y `npm run build` terminan con exit code `0`.
- Si una etapa falla, no continuar con la siguiente hasta corregir el error reportado.

### Aprobacion de release (obligatorio)

- Registrar y aprobar la evidencia en `docs/release-readiness-checklist.md`.
- Sin checklist con gates en verde y decision final en READY, la release queda bloqueada.

### Guia de fallo rapida

- Error de Java/Maven: validar JDK 21 y ejecutar desde `backend/`.
- Error de Node/npm: validar versiones y ejecutar `npm ci` antes de tests/lint/build.
- Error de tests: revisar stacktrace y corregir la falla especifica antes de reintentar la secuencia completa.

## Variables de entorno

### Frontend

- `VITE_API_URL`: URL base del backend (ejemplo: `http://localhost:8080`).

### Backend

- `DB_URL`: URL JDBC de PostgreSQL.
- `DB_USER`: usuario de PostgreSQL.
- `DB_PASSWORD`: password de PostgreSQL.
- `JWT_SECRET`: secreto para firma/validacion de JWT.
- `ADMIN_BASIC_USER`: usuario Basic Auth para endpoints admin.
- `ADMIN_BASIC_PASS`: password Basic Auth para endpoints admin.
- `STOREFRONT_BASIC_USER`: usuario Basic Auth de storefront para casos protegidos.
- `STOREFRONT_BASIC_PASS`: password Basic Auth de storefront.
- `EXPIRED_ADMIN_BASIC_USER`: usuario admin expirado usado para validar seguridad.
- `EXPIRED_ADMIN_BASIC_PASS`: password del usuario admin expirado.
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

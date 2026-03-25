# Youngsters Sports Club

Angular 19 + Spring Boot 3 full-stack app (Youngsters Sports Club & Cafe, Satna).

## Development server

### Full stack (Spring Boot + built Angular)

```bash
./mvnw spring-boot:run
```

Uses `server.port=${PORT:8080}` locally (default 8080). Add `http://localhost:8080` to Google Cloud → OAuth Web client → **Authorized JavaScript origins**.

### Angular only (live reload)

```bash
ng serve
```

Opens on `http://localhost:4300/` (`angular.json`). Add that origin to Google Cloud if you use Google Sign-In against the dev server.

## Static frontend (Spring Boot)

The production Angular bundle is copied into `src/main/resources/static/` during the Maven `process-resources` phase (`frontend-maven-plugin` runs `npm run build`, then `maven-resources-plugin` copies `dist/youngsters-sports-club/browser/`). To build the UI only and copy manually:

```bash
npm run build
rm -rf src/main/resources/static/*
cp -r dist/youngsters-sports-club/browser/* src/main/resources/static/
```

Use `-Dfrontend.skip=true` if the UI is already built and copied.

## Building

```bash
./mvnw clean package -DskipTests
```

JAR: `target/youngsters-sports-club.jar` (Spring Boot repackage `finalName`).

## Google Sign-In

Set `googleClientId` in `client/src/environments/environment.ts` and `environment.prod.ts` to your OAuth 2.0 **Web** client ID. In Google Cloud, add **Authorized JavaScript origins** for each URL you use: `http://localhost:8080` (Spring Boot), `http://localhost:4300` (`ng serve`), and your Render URL (e.g. `https://your-app.onrender.com`). GIS uses `window.location.origin` at runtime.

## API

- `GET /api/health` → `{ "status": "UP" }` (Render health checks)

## Render Deployment

**Build Command:**

```bash
./mvnw clean install -DskipTests
```

**Start Command:**

```bash
java -jar target/*.jar
```

(or `java -jar target/youngsters-sports-club.jar`)

Render sets `PORT`; the app binds via `server.port=${PORT:8080}`. Register your Render URL (e.g. `https://your-service.onrender.com`) in Google Cloud authorized JavaScript origins.

## SPA routing

`WebController` forwards single-segment client routes (e.g. `/login`, `/dashboard`) to `index.html` so refresh works. Static assets (files with `.` in the last segment) are not forwarded.

## Additional resources

- [Angular CLI](https://angular.dev/tools/cli)
- [Spring Boot](https://docs.spring.io/spring-boot/)

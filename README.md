# PureWell ERP

ERP dashboard prototype for PureWell Refilling Station with a JavaFX desktop wrapper.
Modules: HRM, Finance & Accounting, Procurement & Inventory, Sales & Delivery.

## Quick Start
```bash
mvn spring-boot:run
```
Open `http://localhost:8080/login`.

## Desktop App
```bash
mvn javafx:run
```

## Windows EXE
The desktop executable is built from the React website front end plus the Spring Boot backend.

How the EXE works:
1. The frontend is built in `spa-react` with Vite.
2. Electron wraps the website in a desktop window.
3. The Spring Boot backend is started automatically from `backend.jar`.
4. The app loads the website mode from `http://127.0.0.1:<port>/spa/`.
   It tries port `8080` first, then automatically falls back to another free port up to `8100`.
5. The PureWell logo is embedded into the EXE so File Explorer shows the correct icon.

To rebuild the EXE locally:
```bash
cd spa-react
npm run electron-build
```

`npm run electron-build` now does all required steps in order:
1. Builds the React SPA.
2. Syncs SPA assets into `src/main/resources/static/spa`.
3. Rebuilds Spring Boot JAR (`mvnw -DskipTests clean package`).
4. Packages the Electron app.
5. Creates `PureWell-ERP-Package_Final` in the project root.
6. Creates `PureWell-ERP-Package_Final.zip` ready to upload/share.
7. Bundles a portable Java runtime so target devices do not need Java installed.

The final deliverable folder is `PureWell-ERP-Package_Final`.
Double-click `PureWell ERP.exe` inside that folder to launch the app.

The package includes:
- `PureWell ERP.exe` for the desktop app
- `backend.jar` for the Spring Boot server
- Bundled portable Java runtime (`jre`) used automatically by the app
- `.env` for Supabase database settings
- Electron runtime files needed by Windows

Important when sharing via ZIP/Drive:
- Share `PureWell-ERP-Package_Final.zip` (generated automatically by build).
- Always extract the full ZIP first before running the EXE.
- The package already includes database config (`db.env` and `.env`) automatically.
- The app needs internet access to Supabase (`aws-1-ap-southeast-1.pooler.supabase.com:5432`).
  If that host/port is blocked by firewall/network policy, login will show `Unable to reach the database`.

## Docs
See `docs/overview.txt` and `docs/midterm_documentation.txt`.

## Environment (Optional)
Database settings are loaded from root `.env` using:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

You can also create a `.env` file in the project root (copy from `.env.example`).

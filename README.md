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
4. The app loads the website mode from `http://127.0.0.1:8080/spa/`.
5. The PureWell logo is embedded into the EXE so File Explorer shows the correct icon.

To rebuild the EXE locally:
```bash
cd spa-react
npm run web-build
npx electron-builder --win --dir
```

The final deliverable folder is `PureWell-ERP-Package-Final`.
Double-click `PureWell ERP.exe` inside that folder to launch the app.

The package includes:
- `PureWell ERP.exe` for the desktop app
- `backend.jar` for the Spring Boot server
- `.env` for Supabase database settings
- Electron runtime files needed by Windows

## Docs
See `docs/overview.txt` and `docs/midterm_documentation.txt`.

## Environment (Optional)
Database settings are loaded from root `.env` using:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

You can also create a `.env` file in the project root (copy from `.env.example`).

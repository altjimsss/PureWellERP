# PureWellERP

This repo contains the PureWell ERP web app plus a JavaFX desktop wrapper that loads the same UI.

## Web (Spring Boot)

```powershell
cd demo
mvn spring-boot:run
```

Then open:
`http://localhost:8080/login`

## Desktop (JavaFX wrapper)

```powershell
cd demo
mvn javafx:run
```

Notes:
- The desktop app launches a WebView that loads the same web UI.
- If port 8080 is in use, stop the existing process or change the port in `demo/src/main/resources/application.properties`.

## SPA shell (used by desktop)

The desktop wrapper loads `/spa/` which is built from `demo/spa-react`.

Build the SPA:

```powershell
cd demo/spa-react
npm install
npm run build
```

The build output is written to:
`demo/src/main/resources/static/spa`


package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class DesktopLauncher extends Application {
	private static volatile ConfigurableApplicationContext context;
	private static final String APP_URL = "http://localhost:8080/login";
	private static final String BASE_URL = "http://localhost:8080";
	private static final CookieManager COOKIE_MANAGER = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private Stage primaryStage;
	private String lastNonDownloadUrl = APP_URL;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		CookieHandler.setDefault(COOKIE_MANAGER);
		this.primaryStage = stage;
		WebView webView = new WebView();
		WebEngine engine = webView.getEngine();
		String logoDataUrl = loadLogoDataUrl();
		engine.locationProperty().addListener((obs, oldLoc, newLoc) -> {
			if (newLoc == null) {
				return;
			}
			if (isDownloadUrl(newLoc)) {
				handleDownload(newLoc);
				if (lastNonDownloadUrl != null && !lastNonDownloadUrl.equals(newLoc)) {
					engine.load(lastNonDownloadUrl);
				}
				return;
			}
			lastNonDownloadUrl = newLoc;
		});
		engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
			if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
				String location = engine.getLocation();
				if (location != null && location.contains("/login")) {
					return;
				}
			}
		});
		String splashHtml = String.format(
				"<html>\n"
						+ "<head><style>\n"
						+ "body{font-family:\"Space Grotesk\",Segoe UI,Arial,sans-serif;display:flex;align-items:center;"
						+ "justify-content:center;height:100vh;background:#f7fbff;color:#162432;margin:0}\n"
						+ ".splash{display:flex;flex-direction:column;align-items:center;gap:16px}\n"
						+ ".logo{width:64px;height:64px;object-fit:contain}\n"
						+ ".loader{width:180px;height:4px;border-radius:999px;background:rgba(22,36,50,0.08);"
						+ "overflow:hidden;position:relative}\n"
						+ ".loader::before{content:\"\";position:absolute;inset:0;width:40%%;background:#3aa4e0;"
						+ "border-radius:999px;animation:load 1.2s ease-in-out infinite}\n"
						+ "@keyframes load{0%%{transform:translateX(-120%%)}50%%{transform:translateX(60%%)}"
						+ "100%%{transform:translateX(220%%)}}\n"
						+ ".caption{font-size:14px;color:#6b7c8f}\n"
						+ "</style></head>\n"
						+ "<body>\n"
						+ "  <div class=\"splash\">\n"
						+ "    <img class=\"logo\" src=\"%s\" alt=\"PureWell ERP\">\n"
						+ "    <div class=\"loader\" aria-label=\"Loading\"></div>\n"
						+ "    <div class=\"caption\">Starting PureWell ERP...</div>\n"
						+ "  </div>\n"
						+ "</body>\n"
						+ "</html>\n",
				logoDataUrl == null ? "" : logoDataUrl
		);
		engine.loadContent(splashHtml);

		StackPane root = new StackPane(webView);
		Scene scene = new Scene(root, 1280, 800);
		stage.setTitle("PureWell ERP");
		stage.setScene(scene);
		stage.show();

		startSpringAndLoad(engine);
	}

	private void startSpringAndLoad(WebEngine engine) {
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() {
				context = SpringApplication.run(com.example.demo.Application.class);
				waitForServer();
				Platform.runLater(() -> engine.load(APP_URL));
				return null;
			}
		};
		Thread thread = new Thread(task, "spring-boot");
		thread.setDaemon(true);
		thread.start();
	}

	private boolean isDownloadUrl(String url) {
		try {
			URI uri = URI.create(url);
			String path = uri.getPath();
			return path != null && path.contains("/report");
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private void handleDownload(String url) {
		String format = getQueryParam(url, "format").orElse("");
		String defaultName = buildDefaultFilename(url, format);
		Platform.runLater(() -> {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Save report");
			if (!format.isBlank()) {
				chooser.setInitialFileName(defaultName);
				chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
						format.toUpperCase() + " files", "*." + format.toLowerCase()));
			} else {
				chooser.setInitialFileName(defaultName);
			}
			java.io.File file = chooser.showSaveDialog(primaryStage);
			if (file == null) {
				return;
			}
			Task<Void> downloadTask = new Task<>() {
				@Override
				protected Void call() throws Exception {
					downloadToFile(url, file.toPath());
					return null;
				}
			};
			Thread thread = new Thread(downloadTask, "download-report");
			thread.setDaemon(true);
			thread.start();
		});
	}

	private void downloadToFile(String url, Path target) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		String cookieHeader = buildCookieHeader(url);
		if (!cookieHeader.isBlank()) {
			connection.setRequestProperty("Cookie", cookieHeader);
		}
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(15000);
		connection.connect();
		try (InputStream in = connection.getInputStream();
				OutputStream out = Files.newOutputStream(target)) {
			in.transferTo(out);
		}
	}

	private String buildCookieHeader(String url) {
		try {
			URI uri = URI.create(url.startsWith("http") ? url : BASE_URL + url);
			List<java.net.HttpCookie> cookies = COOKIE_MANAGER.getCookieStore().get(uri);
			if (cookies == null || cookies.isEmpty()) {
				return "";
			}
			StringJoiner joiner = new StringJoiner("; ");
			for (java.net.HttpCookie cookie : cookies) {
				joiner.add(cookie.getName() + "=" + cookie.getValue());
			}
			return joiner.toString();
		} catch (IllegalArgumentException ex) {
			return "";
		}
	}

	private Optional<String> getQueryParam(String url, String key) {
		try {
			URI uri = URI.create(url);
			String query = uri.getRawQuery();
			if (query == null || query.isBlank()) {
				return Optional.empty();
			}
			String[] parts = query.split("&");
			for (String part : parts) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2 && kv[0].equals(key)) {
					return Optional.of(URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
				}
			}
			return Optional.empty();
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private String buildDefaultFilename(String url, String format) {
		String safeFormat = format == null || format.isBlank() ? "pdf" : format.toLowerCase();
		String base = "report-" + LocalDate.now();
		try {
			URI uri = URI.create(url);
			String path = uri.getPath();
			if (path != null) {
				if (path.contains("/finance/report")) {
					base = "finance-report-" + LocalDate.now();
				} else if (path.contains("/sales/history/report")) {
					base = "sales-history-" + LocalDate.now();
				} else if (path.contains("/sales/orders/") && path.contains("/report")) {
					base = "order-report-" + LocalDate.now();
				}
			}
		} catch (IllegalArgumentException ignored) {
		}
		return base + "." + safeFormat;
	}

	private void waitForServer() {
		Instant start = Instant.now();
		while (Duration.between(start, Instant.now()).toSeconds() < 30) {
			try {
				HttpURLConnection connection = (HttpURLConnection) new URL(APP_URL).openConnection();
				connection.setConnectTimeout(1000);
				connection.setReadTimeout(1000);
				connection.setRequestMethod("GET");
				int code = connection.getResponseCode();
				if (code >= 200 && code < 500) {
					return;
				}
			} catch (IOException ignored) {
				// keep retrying until server is ready
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static String quoteForJs(String value) {
		return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
	}

	private static String loadLogoDataUrl() {
		try (InputStream in = DesktopLauncher.class.getResourceAsStream("/static/img/purewelllogo.png")) {
			if (in == null) {
				return "";
			}
			byte[] bytes = in.readAllBytes();
			String encoded = Base64.getEncoder().encodeToString(bytes);
			return "data:image/png;base64," + encoded;
		} catch (IOException ex) {
			return "";
		}
	}

	@Override
	public void stop() {
		if (context != null) {
			context.close();
		}
		Platform.exit();
	}
}

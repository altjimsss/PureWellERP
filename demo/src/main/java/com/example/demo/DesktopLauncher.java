package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class DesktopLauncher extends Application {
	private static ConfigurableApplicationContext context;
	private static final String APP_URL = "http://localhost:8080/login";

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) {
		WebView webView = new WebView();
		WebEngine engine = webView.getEngine();
		String logoDataUrl = loadLogoDataUrl();
		engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
			if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
				String location = engine.getLocation();
				if (location != null && location.contains("/login")) {
					return;
				}
				String css = """
						* {
							box-sizing: border-box;
							font-family: "Space Grotesk", "Segoe UI", sans-serif !important;
						}
						input, select, textarea, button {
							appearance: none;
							-webkit-appearance: none;
							font-family: "Space Grotesk", "Segoe UI", sans-serif !important;
						}
						select {
							appearance: none;
							background-color: #ffffff;
							border: 1px solid rgba(0, 0, 0, 0.08);
							border-radius: 12px;
							padding: 8px 36px 8px 12px;
							font-size: 0.9rem;
							color: #162432;
							line-height: 1.2;
							background-image:
								linear-gradient(45deg, transparent 50%, #6b7c8f 50%),
								linear-gradient(135deg, #6b7c8f 50%, transparent 50%);
							background-position:
								calc(100% - 18px) 50%,
								calc(100% - 12px) 50%;
							background-size: 6px 6px, 6px 6px;
							background-repeat: no-repeat;
						}
						input[type="text"],
						input[type="search"],
						input[type="date"],
						input[type="number"],
						input[type="password"],
						textarea {
							background-color: #ffffff;
							border: 1px solid rgba(0, 0, 0, 0.08);
							border-radius: 12px;
							padding: 8px 12px;
							font-size: 0.9rem;
							color: #162432;
							line-height: 1.2;
							box-shadow: none;
							background-image: none !important;
							appearance: none;
							-webkit-appearance: none;
						}
						input::-webkit-search-decoration,
						input::-webkit-search-cancel-button,
						input::-webkit-search-results-button,
						input::-webkit-search-results-decoration {
							display: none;
						}
						button, .pill {
							font-family: "Space Grotesk", "Segoe UI", sans-serif !important;
						}
						::placeholder {
							color: #9aa8b8;
						}
						.search-input .search-icon {
							display: none !important;
						}
						""";
				engine.executeScript(
						"var style=document.createElement('style');"
								+ "style.innerHTML=" + quoteForJs(css) + ";"
								+ "document.head.appendChild(style);"
				);
				engine.executeScript(
						"try{document.documentElement.classList.add('desktop-performance');}catch(e){}"
				);
				String perfCssText = """
						.desktop-performance * {
							scroll-behavior: auto !important;
						}
						.desktop-performance .card,
						.desktop-performance .card-stack,
						.desktop-performance .panel,
						.desktop-performance .table-shell,
						.desktop-performance .modal-card,
						.desktop-performance .list-card,
						.desktop-performance .banner-card,
						.desktop-performance .kpi-card,
						.desktop-performance .pill,
						.desktop-performance .app-shell,
						.desktop-performance .main {
							box-shadow: none !important;
						}
						.desktop-performance .sidebar {
							box-shadow: none !important;
						}
						.desktop-performance .glass,
						.desktop-performance .blur,
						.desktop-performance .frost,
						.desktop-performance .panel-header,
						.desktop-performance .topbar {
							backdrop-filter: none !important;
							filter: none !important;
						}
						.desktop-performance * {
							transition-duration: 0.08s !important;
							animation-duration: 0.12s !important;
							animation-iteration-count: 1 !important;
						}
						""";
				engine.executeScript(
						"var perf=document.createElement('style');"
								+ "perf.innerHTML=" + quoteForJs(perfCssText) + ";"
								+ "document.head.appendChild(perf);"
				);
				engine.executeScript(
						"if(!window.Chart){"
								+ "var s=document.querySelector('script[data-chartjs]');"
								+ "if(!s){s=document.createElement('script');s.src='/js/vendor/chart.umd.min.js';s.async=true;s.dataset.chartjs='true';"
								+ "s.onload=function(){document.dispatchEvent(new Event('chartjs:ready'));};document.head.appendChild(s);} }"
								+ "else{document.dispatchEvent(new Event('chartjs:ready'));}"
				);
				engine.executeScript(
						"setTimeout(function(){"
								+ "if(typeof tryInitCharts==='function'){tryInitCharts();}"
								+ "if(typeof renderExpensePie==='function'){renderExpensePie();}"
								+ "if(typeof renderIncomeExpenseChart==='function'){renderIncomeExpenseChart();}"
								+ "}, 300);"
				);
				engine.executeScript(
						"try{"
								+ "var routes=['/','/modules/finance','/modules/hrm','/modules/procurement','/modules/sales'];"
								+ "routes.forEach(function(u){var l=document.createElement('link');l.rel='prefetch';l.href=u;document.head.appendChild(l);});"
								+ "}catch(e){}"
				);
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

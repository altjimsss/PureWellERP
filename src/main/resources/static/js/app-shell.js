const appContent = document.getElementById("app-content");
const appLoading = document.getElementById("app-loading");
const initialUrl = document.body.dataset.initialUrl || "/";

const loadScript = (src) =>
	new Promise((resolve, reject) => {
		if (document.querySelector(`script[src="${src}"]`)) {
			resolve();
			return;
		}
		const script = document.createElement("script");
		script.src = src;
		script.onload = resolve;
		script.onerror = reject;
		document.body.appendChild(script);
	});

const runInlineScripts = (doc) => {
	const scripts = Array.from(doc.querySelectorAll("script"))
		.filter((s) => !s.src)
		.map((s) => s.textContent || "");
	if (!scripts.length) {
		return;
	}
	scripts.forEach((code) => {
		const tag = document.createElement("script");
		tag.textContent = code;
		document.body.appendChild(tag);
	});
};

const loadPage = async (url, pushState = true) => {
	// Keep current content visible to avoid white flash during navigation.
	const response = await fetch(url, { headers: { "X-App-Shell": "1" } });
	const html = await response.text();
	const doc = new DOMParser().parseFromString(html, "text/html");
	const main = doc.querySelector("main.main");
	if (!main) {
		window.location.href = url;
		return;
	}

	const next = document.createElement("div");
	next.innerHTML = main.innerHTML;
	next.style.opacity = "0";
	next.style.transition = "opacity 0.15s ease";

	const externalScripts = Array.from(doc.querySelectorAll("script[src]"))
		.map((s) => s.getAttribute("src"))
		.filter(Boolean);
	for (const src of externalScripts) {
		try {
			await loadScript(src);
		} catch (err) {
			console.warn("Failed loading script", src, err);
		}
	}
	runInlineScripts(doc);

	const old = appContent.firstElementChild;
	appContent.appendChild(next);
	requestAnimationFrame(() => {
		next.style.opacity = "1";
	});

	setTimeout(() => {
		if (old && old.parentElement === appContent) {
			old.remove();
		}
	}, 180);

	document.title = doc.title || document.title;
	if (pushState) {
		history.pushState({ url }, "", url);
	}
};

document.addEventListener("click", (event) => {
	const link = event.target.closest("a[data-app-link]");
	if (!link) return;
	const url = link.getAttribute("href");
	if (!url) return;
	event.preventDefault();
	loadPage(url);
});

window.addEventListener("popstate", (event) => {
	if (event.state && event.state.url) {
		loadPage(event.state.url, false);
	}
});

loadPage(initialUrl, false);

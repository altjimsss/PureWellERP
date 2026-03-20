const applyReveal = () => {
	const targets = document.querySelectorAll(
		".panel-header, .dash-banner, .stat-card, .card-map, .card-donut, .card-list, .card-chart, .card-stack, .finance-card, .hrm-card, .procurement-card, .sales-card"
	);

	if (!("IntersectionObserver" in window)) {
		targets.forEach((el) => el.classList.add("is-visible"));
		return;
	}

	const observer = new IntersectionObserver(
		(entries) => {
			entries.forEach((entry) => {
				if (!entry.isIntersecting) {
					return;
				}
				entry.target.classList.add("is-visible");
				observer.unobserve(entry.target);
			});
		},
		{ threshold: 0.18 }
	);

	targets.forEach((el, index) => {
		el.classList.add("reveal");
		el.style.transitionDelay = `${Math.min(index, 6) * 40}ms`;
		observer.observe(el);
	});
};

const addButtonPress = () => {
	document.querySelectorAll(".pill, .icon-btn, .modal-btn").forEach((button) => {
		button.addEventListener("mousedown", () => button.classList.add("pressed"));
		button.addEventListener("mouseup", () => button.classList.remove("pressed"));
		button.addEventListener("mouseleave", () => button.classList.remove("pressed"));
	});
};

const setupToasts = () => {
	const stack = document.createElement("div");
	stack.className = "toast-stack";
	document.body.appendChild(stack);

	const showToast = (message) => {
		if (!message) {
			return;
		}
		const toast = document.createElement("div");
		toast.className = "toast";
		toast.textContent = message;
		stack.appendChild(toast);
		setTimeout(() => {
			toast.style.opacity = "0";
			toast.style.transform = "translateY(6px)";
			setTimeout(() => toast.remove(), 250);
		}, 1600);
	};

	document.querySelectorAll("[data-toast]").forEach((el) => {
		el.addEventListener("click", () => {
			showToast(el.getAttribute("data-toast"));
		});
	});

	document.querySelectorAll("[data-reset-filters]").forEach((btn) => {
		btn.addEventListener("click", () => {
			const form = btn.closest("form") || btn.closest(".filter-bar") || document;
			form.querySelectorAll("input, select").forEach((field) => {
				if (field.type === "search" || field.type === "text" || field.type === "date") {
					field.value = "";
				}
				if (field.tagName === "SELECT") {
					field.selectedIndex = 0;
				}
			});
			showToast(btn.getAttribute("data-toast") || "Filters cleared");
		});
	});
};

applyReveal();
addButtonPress();
setupToasts();

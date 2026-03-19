(() => {
	const modal = document.getElementById("signout-modal");
	const confirmBtn = document.querySelector("[data-signout-confirm]");
	const cancelBtn = document.querySelector("[data-signout-cancel]");
	const signoutLinks = document.querySelectorAll("[data-signout-trigger]");

	if (!modal || signoutLinks.length === 0) {
		return;
	}

	const openModal = (event) => {
		event.preventDefault();
		modal.classList.add("is-visible");
		modal.removeAttribute("aria-hidden");
		const focusTarget = modal.querySelector(".modal-card button");
		if (focusTarget) {
			focusTarget.focus();
		}
	};

	const closeModal = () => {
		modal.classList.remove("is-visible");
		modal.setAttribute("aria-hidden", "true");
	};

	signoutLinks.forEach((link) => link.addEventListener("click", openModal));

	if (cancelBtn) {
		cancelBtn.addEventListener("click", closeModal);
	}

	if (confirmBtn) {
		confirmBtn.addEventListener("click", () => {
			const target = signoutLinks[0].getAttribute("href") || "/logout";
			window.location.href = target;
		});
	}

	modal.addEventListener("click", (event) => {
		if (event.target === modal) {
			closeModal();
		}
	});

	document.addEventListener("keydown", (event) => {
		if (event.key === "Escape" && modal.classList.contains("is-visible")) {
			closeModal();
		}
	});
})();

const customerSearch = document.getElementById("customerSearch");
if (customerSearch) {
	customerSearch.addEventListener("input", (event) => {
		const query = event.target.value.toLowerCase().trim();
		document.querySelectorAll(".customer-row").forEach((row) => {
			const haystack = (row.getAttribute("data-search") || "").toLowerCase();
			row.style.display = haystack.includes(query) ? "" : "none";
		});
	});
}

const productSearch = document.querySelector("input[name='productSearch']");
const productSelect = document.querySelector("select[name='productId']");
if (productSearch && productSelect) {
	const options = Array.from(productSelect.options);
	productSearch.addEventListener("input", (event) => {
		const query = event.target.value.toLowerCase().trim();
		productSelect.innerHTML = "";
		options.forEach((option) => {
			if (!option.value) {
				productSelect.appendChild(option.cloneNode(true));
				return;
			}
			const text = option.textContent.toLowerCase();
			if (text.includes(query)) {
				productSelect.appendChild(option.cloneNode(true));
			}
		});
		if (productSelect.options.length > 1) {
			productSelect.selectedIndex = 1;
		}
	});
}

const customerSearchInput = document.querySelector("input[name='customerSearch']");
const customerSelect = document.querySelector("select[name='customerId']");
if (customerSearchInput && customerSelect) {
	const options = Array.from(customerSelect.options);
	customerSearchInput.addEventListener("input", (event) => {
		const query = event.target.value.toLowerCase().trim();
		customerSelect.innerHTML = "";
		options.forEach((option) => {
			if (!option.value) {
				customerSelect.appendChild(option.cloneNode(true));
				return;
			}
			const text = option.textContent.toLowerCase();
			if (text.includes(query)) {
				customerSelect.appendChild(option.cloneNode(true));
			}
		});
		if (customerSelect.options.length > 1) {
			customerSelect.selectedIndex = 1;
		}
	});
}

const toast = document.getElementById("updateToast");
if (toast) {
	requestAnimationFrame(() => toast.classList.add("show"));
	setTimeout(() => {
		toast.classList.remove("show");
	}, 3000);
	if (window.location.search.includes("updateStatus=")) {
		const url = new URL(window.location.href);
		url.searchParams.delete("updateStatus");
		window.history.replaceState({}, document.title, url.toString());
	}
}

const recordToast = document.getElementById("recordToast");
if (recordToast) {
	requestAnimationFrame(() => recordToast.classList.add("show"));
	setTimeout(() => {
		recordToast.classList.remove("show");
	}, 3000);
	if (window.location.search.includes("recordStatus=")) {
		const url = new URL(window.location.href);
		url.searchParams.delete("recordStatus");
		window.history.replaceState({}, document.title, url.toString());
	}
}

const customerToast = document.getElementById("customerToast");
if (customerToast) {
	requestAnimationFrame(() => customerToast.classList.add("show"));
	setTimeout(() => {
		customerToast.classList.remove("show");
	}, 3000);
	if (window.location.search.includes("addCustomerStatus=")) {
		const url = new URL(window.location.href);
		url.searchParams.delete("addCustomerStatus");
		window.history.replaceState({}, document.title, url.toString());
	}
}

const productToast = document.getElementById("productToast");
if (productToast) {
	requestAnimationFrame(() => productToast.classList.add("show"));
	setTimeout(() => {
		productToast.classList.remove("show");
	}, 3000);
	if (window.location.search.includes("addProductStatus=")) {
		const url = new URL(window.location.href);
		url.searchParams.delete("addProductStatus");
		window.history.replaceState({}, document.title, url.toString());
	}
}

const orderModal = document.getElementById("orderModal");
const orderSummary = document.getElementById("orderSummary");
const orderItems = document.getElementById("orderItems");
const orderTotal = document.getElementById("orderTotal");
const orderStatusBadge = document.getElementById("orderStatusBadge");
const orderStatusBadgeReceipt = document.getElementById("orderStatusBadgeReceipt");

const closeOrderModal = () => {
	if (!orderModal) {
		return;
	}
	orderModal.classList.remove("show");
	orderModal.setAttribute("aria-hidden", "true");
};

document.querySelectorAll(".order-row").forEach((row) => {
	row.addEventListener("click", async () => {
		const orderId = row.getAttribute("data-order-id");
		if (!orderId || !orderModal || !orderSummary || !orderItems || !orderTotal) {
			return;
		}
		const response = await fetch(`/modules/sales/orders/${orderId}`);
		if (!response.ok) {
			return;
		}
		const data = await response.json();
		const header = data.header || {};
		const formatMoney = (value) => {
			if (value === null || value === undefined || value === "") {
				return "0.00";
			}
			const numberValue = Number(value);
			if (Number.isNaN(numberValue)) {
				return value;
			}
			return numberValue.toFixed(2);
		};
		const formatDate = (value) => {
			if (!value) {
				return "-";
			}
			const date = new Date(value);
			if (Number.isNaN(date.getTime())) {
				return value;
			}
			return date.toLocaleString();
		};
		const setStatusBadge = (statusValue) => {
			if (!orderStatusBadge && !orderStatusBadgeReceipt) {
				return;
			}
			const normalizedRaw = String(statusValue || "Pending").toLowerCase();
			const normalized = normalizedRaw === "delivered" ? "Delivered" : "Pending";
			if (orderStatusBadge) {
				orderStatusBadge.textContent = normalized;
				orderStatusBadge.classList.remove("delivered", "pending");
				orderStatusBadge.classList.add(normalized === "Delivered" ? "delivered" : "pending");
			}
			if (orderStatusBadgeReceipt) {
				orderStatusBadgeReceipt.textContent = normalized;
				orderStatusBadgeReceipt.classList.remove("delivered", "pending");
				orderStatusBadgeReceipt.classList.add(normalized === "Delivered" ? "delivered" : "pending");
			}
		};
		orderSummary.innerHTML = `
			<div class="summary-cell"><span class="summary-label">Order #</span><span class="summary-value">${header.id ?? "-"}</span></div>
			<div class="summary-cell"><span class="summary-label">Customer</span><span class="summary-value">${header.customer ?? "-"}</span></div>
			<div class="summary-cell"><span class="summary-label">Contact</span><span class="summary-value">${header.contact ?? "-"}</span></div>
			<div class="summary-cell"><span class="summary-label">Address</span><span class="summary-value">${header.address ?? "-"}</span></div>
			<div class="summary-cell"><span class="summary-label">Order Date</span><span class="summary-value">${formatDate(header.orderDate)}</span></div>
		`;
		setStatusBadge(header.status);
		orderItems.innerHTML = "";
		(data.items || []).forEach((item) => {
			const rowEl = document.createElement("div");
			rowEl.className = "summary-row";
			rowEl.innerHTML = `
				<span>${item.name ?? ""}</span>
				<span>${item.quantity ?? ""}</span>
				<span>${formatMoney(item.price)}</span>
				<span>${formatMoney(item.lineTotal)}</span>
			`;
			orderItems.appendChild(rowEl);
		});
		orderTotal.textContent = formatMoney(data.total);
		document.querySelectorAll("[data-order-download]").forEach((link) => {
			const format = link.getAttribute("data-order-download");
			link.setAttribute("href", `/modules/sales/orders/${orderId}/report?format=${format}`);
		});
		orderModal.classList.add("show");
		orderModal.setAttribute("aria-hidden", "false");
	});
});

const modalCloseButton = document.querySelector("[data-modal-close]");
if (modalCloseButton) {
	modalCloseButton.addEventListener("click", closeOrderModal);
}

if (orderModal) {
	orderModal.addEventListener("click", (event) => {
		if (event.target === orderModal) {
			closeOrderModal();
		}
	});
}

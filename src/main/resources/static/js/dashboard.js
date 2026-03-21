const getDashboardStore = () => {
	if (!window.__DASHBOARD_CHARTS__) {
		window.__DASHBOARD_CHARTS__ = {};
	}
	return window.__DASHBOARD_CHARTS__;
};

const destroyChart = (key) => {
	const store = getDashboardStore();
	const existing = store[key];
	if (existing && typeof existing.destroy === "function") {
		existing.destroy();
	}
	store[key] = null;
};

const initFleetChart = () => {
	const ctx = document.getElementById("fleetChart");
	if (!ctx || !window.__FLEET_DATA__ || !window.Chart) {
		return false;
	}
	destroyChart("fleet");
	const { fleetLabels, fleetDistance, fleetEmpty } = window.__FLEET_DATA__;
	const maxValue = Math.max(...fleetDistance, ...fleetEmpty, 1);
	const gradient = ctx.getContext("2d").createLinearGradient(0, 0, 0, 200);
	gradient.addColorStop(0, "rgba(90, 105, 216, 0.35)");
	gradient.addColorStop(1, "rgba(90, 105, 216, 0.02)");

	const chart = new Chart(ctx, {
		type: "line",
		data: {
			labels: fleetLabels,
			datasets: [
				{
					label: "Distance Driven (km)",
					data: fleetDistance,
					borderColor: "#5a69d8",
					backgroundColor: gradient,
					fill: true,
					tension: 0.35,
					pointRadius: 3,
					pointBackgroundColor: "#5a69d8"
				},
				{
					label: "Empty Miles (km)",
					data: fleetEmpty,
					borderColor: "#3aa4e0",
					backgroundColor: "transparent",
					fill: false,
					tension: 0.35,
					pointRadius: 3,
					pointBackgroundColor: "#3aa4e0"
				}
			]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			scales: {
				y: {
					beginAtZero: true,
					suggestedMax: maxValue,
					grid: {
						color: "rgba(0, 0, 0, 0.06)"
					},
					ticks: {
						color: "#6b7c8f"
					}
				},
				x: {
					grid: {
						display: false
					},
					ticks: {
						color: "#6b7c8f"
					}
				}
			},
			plugins: {
				legend: {
					display: true,
					labels: {
						color: "#6b7c8f",
						usePointStyle: true,
						pointStyle: "circle"
					}
				},
				tooltip: {
					backgroundColor: "#ffffff",
					titleColor: "#162432",
					bodyColor: "#162432",
					borderColor: "rgba(0, 0, 0, 0.08)",
					borderWidth: 1
				}
			}
		}
	});
	getDashboardStore().fleet = chart;
	return true;
};

const initRevenueChart = () => {
	const revenueCtx = document.getElementById("revenueChart");
	if (!revenueCtx || !window.__FLEET_DATA__ || !window.Chart) {
		return false;
	}
	destroyChart("revenue");
	const { revenueLabels, revenueSeries } = window.__FLEET_DATA__;
	const maxValue = Math.max(...revenueSeries, 1);
	const gradient = revenueCtx.getContext("2d").createLinearGradient(0, 0, 0, 200);
	gradient.addColorStop(0, "rgba(58, 164, 224, 0.3)");
	gradient.addColorStop(1, "rgba(58, 164, 224, 0.02)");

	const chart = new Chart(revenueCtx, {
		type: "line",
		data: {
			labels: revenueLabels,
			datasets: [
				{
					label: "Revenue (â‚±)",
					data: revenueSeries,
					borderColor: "#3aa4e0",
					backgroundColor: gradient,
					fill: true,
					tension: 0.35,
					pointRadius: 2,
					pointBackgroundColor: "#3aa4e0"
				}
			]
		},
		options: {
			responsive: true,
			maintainAspectRatio: false,
			scales: {
				y: {
					beginAtZero: true,
					suggestedMax: maxValue,
					grid: {
						color: "rgba(0, 0, 0, 0.06)"
					},
					ticks: {
						color: "#6b7c8f"
					}
				},
				x: {
					grid: {
						display: false
					},
					ticks: {
						color: "#6b7c8f"
					}
				}
			},
			plugins: {
				legend: {
					display: false
				},
				tooltip: {
					backgroundColor: "#ffffff",
					titleColor: "#162432",
					bodyColor: "#162432",
					borderColor: "rgba(0, 0, 0, 0.08)",
					borderWidth: 1
				}
			}
		}
	});
	getDashboardStore().revenue = chart;
	return true;
};

const tryInitCharts = () => {
	const fleetReady = initFleetChart();
	const revenueReady = initRevenueChart();
	return fleetReady || revenueReady;
};

document.addEventListener("spa:content", () => {
	let attempts = 0;
	const maxAttempts = 8;
	const retry = () => {
		attempts += 1;
		const fleet = document.getElementById("fleetChart");
		const revenue = document.getElementById("revenueChart");
		const ready =
			window.Chart &&
			fleet &&
			revenue &&
			fleet.clientWidth > 0 &&
			revenue.clientWidth > 0;
		if (ready) {
			tryInitCharts();
			return;
		}
		if (attempts < maxAttempts) {
			setTimeout(retry, 250);
		}
	};
	retry();
});

if (!tryInitCharts()) {
	document.addEventListener("chartjs:ready", tryInitCharts, { once: true });
	window.addEventListener("load", tryInitCharts, { once: true });
	setTimeout(tryInitCharts, 600);
}

(() => {
	let attempts = 0;
	const maxAttempts = 12;
	const timer = setInterval(() => {
		attempts += 1;
		const fleet = document.getElementById("fleetChart");
		const revenue = document.getElementById("revenueChart");
		const ready =
			window.Chart &&
			fleet &&
			revenue &&
			fleet.clientWidth > 0 &&
			revenue.clientWidth > 0;
		if (ready) {
			tryInitCharts();
			clearInterval(timer);
		} else if (attempts >= maxAttempts) {
			clearInterval(timer);
		}
	}, 400);
})();

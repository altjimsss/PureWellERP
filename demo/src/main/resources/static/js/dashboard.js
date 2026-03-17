const ctx = document.getElementById("fleetChart");
if (ctx && window.__FLEET_DATA__) {
	const { fleetLabels, fleetDistance, fleetEmpty } = window.__FLEET_DATA__;
	const maxValue = Math.max(...fleetDistance, ...fleetEmpty, 1);
	const gradient = ctx.getContext("2d").createLinearGradient(0, 0, 0, 200);
	gradient.addColorStop(0, "rgba(90, 105, 216, 0.35)");
	gradient.addColorStop(1, "rgba(90, 105, 216, 0.02)");

	new Chart(ctx, {
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
}

const revenueCtx = document.getElementById("revenueChart");
if (revenueCtx && window.__FLEET_DATA__) {
	const { revenueLabels, revenueSeries } = window.__FLEET_DATA__;
	const maxValue = Math.max(...revenueSeries, 1);
	const gradient = revenueCtx.getContext("2d").createLinearGradient(0, 0, 0, 200);
	gradient.addColorStop(0, "rgba(58, 164, 224, 0.3)");
	gradient.addColorStop(1, "rgba(58, 164, 224, 0.02)");

	new Chart(revenueCtx, {
		type: "line",
		data: {
			labels: revenueLabels,
			datasets: [
				{
					label: "Revenue (₱)",
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
}

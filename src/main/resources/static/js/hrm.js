document.addEventListener("DOMContentLoaded", () => {
	const employeeSearch = document.getElementById("employeeSearch");
	const employeeCount = document.getElementById("employeeCount");
	const clearEmployeeSearch = document.getElementById("clearEmployeeSearch");
	const departmentFilter = document.getElementById("departmentFilter");
	const positionFilter = document.getElementById("positionFilter");
	const employeeRows = document.querySelectorAll("tbody tr[data-name]");

	const formatMoney = (value) => {
		if (Number.isNaN(value)) return "0.00";
		return value.toFixed(2);
	};

	const salaryForm = document.getElementById("salaryCalc");
	const grossEl = document.querySelector('[data-calc="gross"]');
	const deductionsEl = document.querySelector('[data-calc="deductions"]');
	const netEl = document.querySelector('[data-calc="net"]');

	const readNumber = (input) => {
		if (!input) return 0;
		const value = parseFloat(input.value);
		return Number.isNaN(value) ? 0 : value;
	};

	const updateSalaryCalc = () => {
		if (!salaryForm) return;
		const basic = readNumber(salaryForm.elements.basic);
		const allowances = readNumber(salaryForm.elements.allowances);
		const overtimeHours = readNumber(salaryForm.elements.overtimeHours);
		const overtimeRate = readNumber(salaryForm.elements.overtimeRate);
		const taxRate = readNumber(salaryForm.elements.taxRate);
		const ssRate = readNumber(salaryForm.elements.ssRate);
		const otherDeductions = readNumber(salaryForm.elements.otherDeductions);

		const overtimePay = overtimeHours * overtimeRate;
		const gross = basic + allowances + overtimePay;
		const percentDeductions = gross * (taxRate + ssRate) / 100;
		const totalDeductions = percentDeductions + otherDeductions;
		const net = Math.max(gross - totalDeductions, 0);

		if (grossEl) grossEl.textContent = formatMoney(gross);
		if (deductionsEl) deductionsEl.textContent = formatMoney(totalDeductions);
		if (netEl) netEl.textContent = formatMoney(net);
	};

	if (salaryForm) {
		salaryForm.addEventListener("input", updateSalaryCalc);
		updateSalaryCalc();
	}

	const payrollProcess = document.getElementById("payrollProcess");
	const payrollStatus = document.getElementById("payrollProcessStatus");
	if (payrollProcess && payrollStatus) {
		payrollProcess.addEventListener("submit", (event) => {
			event.preventDefault();
			const date = payrollProcess.elements.payDate.value || "the selected date";
			const method = payrollProcess.elements.method.value || "selected method";
			payrollStatus.textContent = `Queued payroll for ${date} via ${method}.`;
		});
	}

	const complianceForm = document.getElementById("complianceReport");
	const complianceStatus = document.getElementById("complianceStatus");
	if (complianceForm && complianceStatus) {
		complianceForm.addEventListener("submit", (event) => {
			event.preventDefault();
			const period = complianceForm.elements.period.value || "selected period";
			const type = complianceForm.elements.type.value || "report";
			complianceStatus.textContent = `${type} report queued for ${period}.`;
		});
	}

	const benefitsForm = document.getElementById("benefitsForm");
	const benefitsList = document.getElementById("benefitsList");
	const benefitsTotal = document.getElementById("benefitsTotal");
	const deductionsTotal = document.getElementById("deductionsTotal");

	const updateBenefitsTotals = () => {
		if (!benefitsList) return;
		let benefitsSum = 0;
		let deductionsSum = 0;
		benefitsList.querySelectorAll(".benefit-row").forEach((row) => {
			const amount = parseFloat(row.dataset.amount || "0");
			const type = row.dataset.type;
			if (type === "benefit") {
				benefitsSum += amount;
			} else {
				deductionsSum += amount;
			}
		});
		if (benefitsTotal) benefitsTotal.textContent = formatMoney(benefitsSum);
		if (deductionsTotal) deductionsTotal.textContent = formatMoney(deductionsSum);
	};

	const renderEmptyBenefits = () => {
		if (!benefitsList) return;
		if (benefitsList.querySelectorAll(".benefit-row").length === 0) {
			benefitsList.innerHTML = '<div class="empty-note">No benefits or deductions added yet.</div>';
		}
	};

	if (benefitsForm && benefitsList) {
		benefitsForm.addEventListener("submit", (event) => {
			event.preventDefault();
			const item = benefitsForm.elements.item.value.trim();
			const type = benefitsForm.elements.type.value;
			const amountValue = parseFloat(benefitsForm.elements.amount.value);
			if (!item || Number.isNaN(amountValue)) {
				return;
			}
			benefitsForm.reset();
			const row = document.createElement("div");
			row.className = "benefit-row";
			row.dataset.type = type;
			row.dataset.amount = amountValue.toString();
			row.innerHTML = `
				<div>
					<strong>${item}</strong>
					<div class="tag ${type === "benefit" ? "good" : "warn"}">${type}</div>
				</div>
				<div class="mono">${formatMoney(amountValue)}</div>
				<button type="button" aria-label="Remove">Remove</button>
			`;
			benefitsList.querySelector(".empty-note")?.remove();
			benefitsList.appendChild(row);
			updateBenefitsTotals();
		});

		benefitsList.addEventListener("click", (event) => {
			const target = event.target;
			if (target instanceof HTMLButtonElement) {
				target.closest(".benefit-row")?.remove();
				updateBenefitsTotals();
				renderEmptyBenefits();
			}
		});
		updateBenefitsTotals();
	}

	const updateEmployeeCount = () => {
		if (!employeeCount) return;
		const visibleCount = Array.from(employeeRows).filter((row) => row.style.display !== "none").length;
		employeeCount.textContent = `Showing ${visibleCount}`;
	};

	const filterEmployees = (query) => {
		const normalized = query.toLowerCase();
		const departmentValue = departmentFilter?.value?.toLowerCase() || "";
		const positionValue = positionFilter?.value?.toLowerCase() || "";
		employeeRows.forEach((row) => {
			const name = row.dataset.name?.toLowerCase() || "";
			const position = row.dataset.position?.toLowerCase() || "";
			const department = row.dataset.department?.toLowerCase() || "";
			const textMatch = name.includes(normalized) || position.includes(normalized) || department.includes(normalized);
			const deptMatch = !departmentValue || department === departmentValue;
			const positionMatch = !positionValue || position === positionValue;
			const match = textMatch && deptMatch && positionMatch;
			row.style.display = match ? "" : "none";
		});
		updateEmployeeCount();
	};

	if (employeeSearch) {
		employeeSearch.addEventListener("input", (event) => {
			filterEmployees(event.target.value);
		});
	}

	if (departmentFilter) {
		departmentFilter.addEventListener("change", () => {
			filterEmployees(employeeSearch?.value || "");
		});
	}

	if (positionFilter) {
		positionFilter.addEventListener("change", () => {
			filterEmployees(employeeSearch?.value || "");
		});
	}

	if (clearEmployeeSearch && employeeSearch) {
		clearEmployeeSearch.addEventListener("click", () => {
			employeeSearch.value = "";
			if (departmentFilter) departmentFilter.value = "";
			if (positionFilter) positionFilter.value = "";
			filterEmployees("");
		});
	}

	updateEmployeeCount();
	renderEmptyBenefits();
});

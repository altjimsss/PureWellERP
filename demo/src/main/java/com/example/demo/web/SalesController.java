package com.example.demo.web;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Controller
public class SalesController {

	private final JdbcTemplate jdbcTemplate;

	public SalesController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/modules/sales")
	public String salesModule(
			Model model,
			@RequestParam(name = "salesPeriod", defaultValue = "30") String salesPeriod,
			@RequestParam(name = "salesView", defaultValue = "recent") String salesView,
			@RequestParam(name = "customersView", defaultValue = "recent") String customersView,
			@RequestParam(name = "deliveriesView", defaultValue = "recent") String deliveriesView,
			@RequestParam(name = "recordStatus", required = false) String recordStatus,
			@RequestParam(name = "addCustomerStatus", required = false) String addCustomerStatus,
			@RequestParam(name = "addProductStatus", required = false) String addProductStatus,
			@RequestParam(name = "updateStatus", required = false) String updateStatus
	) {
		model.addAttribute("siteTitle", "Sales and Delivery Module");
		model.addAttribute("moduleName", "Sales and Delivery");
		model.addAttribute("salesPeriod", salesPeriod);
		model.addAttribute("salesView", salesView);
		model.addAttribute("customersView", customersView);
		model.addAttribute("deliveriesView", deliveriesView);
		model.addAttribute("recordStatus", recordStatus);
		model.addAttribute("addCustomerStatus", addCustomerStatus);
		model.addAttribute("addProductStatus", addProductStatus);
		model.addAttribute("updateStatus", updateStatus);

		try {
			Integer salesDays = parseDaysPeriod(salesPeriod, 30);
			boolean salesAll = isViewAll(salesView);
			boolean customersAll = isViewAll(customersView);
			boolean deliveriesAll = isViewAll(deliveriesView);

			model.addAttribute("snapshot", loadSnapshot());
			model.addAttribute("products", loadProducts());
			model.addAttribute("customers", loadCustomers(customersAll ? null : 8));
			model.addAttribute("salesHistory", loadSalesHistory(salesDays, salesAll ? null : 8));
			model.addAttribute("deliveries", loadDeliveries(deliveriesAll ? null : 8));
			model.addAttribute("dbAvailable", true);
		} catch (DataAccessException ex) {
			model.addAttribute("snapshot", SalesSnapshot.empty());
			model.addAttribute("products", List.of());
			model.addAttribute("customers", List.of());
			model.addAttribute("salesHistory", List.of());
			model.addAttribute("deliveries", List.of());
			model.addAttribute("dbAvailable", false);
		}
		return "sales-module";
	}

	@GetMapping("/modules/sales/orders/{orderId}")
	@ResponseBody
	public Map<String, Object> orderDetails(@PathVariable("orderId") int orderId) {
		Map<String, Object> header = jdbcTemplate.queryForObject(
				"""
				select o.id,
				       c.name as customer,
				       c.contact,
				       c.address,
				       o.order_date,
				       o.status
				from orders o
				join customers c on o.customer_id = c.id
				where o.id = ?
				""",
				(rs, rowNum) -> Map.<String, Object>of(
						"id", rs.getInt("id"),
						"customer", rs.getString("customer"),
						"contact", rs.getString("contact"),
						"address", rs.getString("address"),
						"orderDate", rs.getTimestamp("order_date").toString(),
						"status", rs.getString("status")
				),
				orderId
		);

		List<Map<String, Object>> items = jdbcTemplate.query(
				"""
				select p.name, oi.quantity, oi.price, (oi.quantity * oi.price) as line_total
				from order_items oi
				join products p on oi.product_id = p.id
				where oi.order_id = ?
				""",
				(rs, rowNum) -> Map.<String, Object>of(
						"name", rs.getString("name"),
						"quantity", rs.getInt("quantity"),
						"price", rs.getBigDecimal("price"),
						"lineTotal", rs.getBigDecimal("line_total")
				),
				orderId
		);

		BigDecimal total = items.stream()
				.map(item -> (BigDecimal) item.get("lineTotal"))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return Map.of(
				"header", header,
				"items", items,
				"total", total
		);
	}

	@GetMapping("/modules/sales/history/report")
	public void downloadSalesHistoryReport(
			HttpServletResponse response,
			@RequestParam(name = "format", defaultValue = "pdf") String format,
			@RequestParam(name = "salesPeriod", defaultValue = "30") String salesPeriod
	) throws IOException {
		Integer days = parseDaysPeriod(salesPeriod, 30);
		List<SalesRow> history = loadSalesHistory(days, null);
		String safeFormat = normalizeFormat(format);
		String filename = "sales-history-" + LocalDate.now() + "." + safeFormat;

		if ("xlsx".equals(safeFormat)) {
			writeSalesHistoryExcel(response, filename, history);
			return;
		}
		writeSalesHistoryPdf(response, filename, history);
	}

	@GetMapping("/modules/sales/orders/{orderId}/report")
	public void downloadOrderReport(
			HttpServletResponse response,
			@PathVariable("orderId") int orderId,
			@RequestParam(name = "format", defaultValue = "pdf") String format
	) throws IOException {
		Map<String, Object> details = orderDetails(orderId);
		Map<String, Object> header = details.get("header") instanceof Map<?, ?>
				? (Map<String, Object>) details.get("header")
				: Map.of();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) details.getOrDefault("items", List.of());
		BigDecimal total = (BigDecimal) details.get("total");
		String safeFormat = normalizeFormat(format);
		String filename = "order-" + orderId + "-" + LocalDate.now() + "." + safeFormat;

		if ("xlsx".equals(safeFormat)) {
			writeOrderExcel(response, filename, header, items, total);
			return;
		}
		writeOrderPdf(response, filename, header, items, total);
	}

	@PostMapping("/modules/sales/customers/add")
	public String addCustomer(
			@RequestParam("name") String name,
			@RequestParam("contact") String contact,
			@RequestParam("address") String address
	) {
		try {
			if (customerExists(name, contact, address)) {
				return "redirect:/modules/sales?addCustomerStatus=duplicate";
			}
			jdbcTemplate.update(
					"insert into customers(name, contact, address) values(?, ?, ?)",
					name,
					contact,
					address
			);
			return "redirect:/modules/sales?addCustomerStatus=success";
		} catch (DataAccessException ex) {
			return "redirect:/modules/sales?addCustomerStatus=error";
		}
	}

	@PostMapping("/modules/sales/products/add")
	public String addProduct(
			@RequestParam("name") String name,
			@RequestParam("price") BigDecimal price
	) {
		try {
			jdbcTemplate.update(
					"insert into products(name, price) values(?, ?)",
					name,
					price
			);
			return "redirect:/modules/sales?addProductStatus=success";
		} catch (DataAccessException ex) {
			return "redirect:/modules/sales?addProductStatus=error";
		}
	}

	@PostMapping("/modules/sales/record")
	public String recordSale(
			@RequestParam("customerId") int customerId,
			@RequestParam("productId") int productId,
			@RequestParam("quantity") int quantity
	) {
		try {
			BigDecimal price = jdbcTemplate.queryForObject(
					"select price from products where id = ?",
					BigDecimal.class,
					productId
			);
			BigDecimal total = price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(quantity));

			Integer orderId = jdbcTemplate.queryForObject(
					"""
					insert into orders(customer_id, order_date, status)
					values(?, now(), 'Pending')
					returning id
					""",
					Integer.class,
					customerId
			);

			if (orderId != null) {
				jdbcTemplate.update(
						"""
						insert into order_items(order_id, product_id, quantity, price)
						values(?, ?, ?, ?)
						""",
						orderId,
						productId,
						quantity,
						price == null ? BigDecimal.ZERO : price
				);
				jdbcTemplate.update(
						"""
						insert into order_delivery_tracking(order_id, delivery_date, delivery_status)
						values(?, current_date, 'Pending')
						""",
						orderId
				);
			}

			Integer saleId = jdbcTemplate.queryForObject(
					"""
					insert into sales(customer_id, product_id, quantity, total, sale_date)
					values(?, ?, ?, ?, now())
					returning id
					""",
					Integer.class,
					customerId,
					productId,
					quantity,
					total
			);

			if (saleId != null) {
				jdbcTemplate.update(
						"insert into deliveries(sales_id, delivery_date, status) values(?, current_date, 'Pending')",
						saleId
				);
			}
			return "redirect:/modules/sales?recordStatus=success";
		} catch (DataAccessException ex) {
			return "redirect:/modules/sales?recordStatus=error";
		}
	}

	@PostMapping("/modules/sales/deliveries/update")
	public String updateDelivery(
			@RequestParam("orderId") int orderId,
			@RequestParam("status") String status
	) {
		try {
			jdbcTemplate.update(
					"update order_delivery_tracking set delivery_status = ? where order_id = ?",
					status,
					orderId
			);
			jdbcTemplate.update(
					"update orders set status = ? where id = ?",
					status,
					orderId
			);
			jdbcTemplate.update(
					"insert into order_status_history(order_id, status, status_date) values(?, ?, now())",
					orderId,
					status
			);
			return "redirect:/modules/sales?updateStatus=success";
		} catch (DataAccessException ex) {
			return "redirect:/modules/sales?updateStatus=error";
		}
	}

	private SalesSnapshot loadSnapshot() {
		Double revenue = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(oi.quantity * oi.price),0)
				from orders o
				join order_items oi on oi.order_id = o.id
				where o.order_date >= now() - interval '30 days'
				""",
				Double.class
		);
		Integer orders = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from orders
				where order_date >= now() - interval '30 days'
				""",
				Integer.class
		);
		Double avgOrder = jdbcTemplate.queryForObject(
				"""
				select coalesce(avg(order_total),0)
				from (
					select o.id, sum(oi.quantity * oi.price) as order_total
					from orders o
					join order_items oi on oi.order_id = o.id
					where o.order_date >= now() - interval '30 days'
					group by o.id
				) totals
				""",
				Double.class
		);
		Integer pending = jdbcTemplate.queryForObject(
				"select count(*) from order_delivery_tracking where delivery_status = 'Pending'",
				Integer.class
		);
		Integer delivered = jdbcTemplate.queryForObject(
				"select count(*) from order_delivery_tracking where delivery_status = 'Delivered'",
				Integer.class
		);

		return new SalesSnapshot(
				round2(valueOrZero(revenue)),
				valueOrZero(orders),
				round2(valueOrZero(avgOrder)),
				valueOrZero(pending),
				valueOrZero(delivered),
				"Last 30 days"
		);
	}

	private List<ProductOption> loadProducts() {
		return jdbcTemplate.query(
				"""
				select id, name, price
				from products
				order by name
				limit 200
				""",
				(rs, rowNum) -> new ProductOption(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getBigDecimal("price")
				)
		);
	}

	private List<CustomerRow> loadCustomers(Integer limit) {
		String sql = "select id, name, contact, address from customers order by id desc";
		if (limit != null) {
			sql += " limit " + limit;
		}
		return jdbcTemplate.query(
				sql,
				(rs, rowNum) -> new CustomerRow(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getString("contact"),
						rs.getString("address")
				)
		);
	}

	private boolean customerExists(String name, String contact, String address) {
		Integer count = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from customers
				where lower(trim(name)) = lower(trim(?))
				   or (lower(trim(contact)) = lower(trim(?))
				       and lower(trim(address)) = lower(trim(?)))
				""",
				Integer.class,
				name,
				contact,
				address
		);
		return count != null && count > 0;
	}

	private List<SalesRow> loadSalesHistory(Integer days, Integer limit) {
		StringBuilder sql = new StringBuilder(
				"""
				select o.id,
				       c.name as customer,
				       p.name as product,
				       oi.quantity,
				       (oi.quantity * oi.price) as total,
				       o.order_date as sale_date
				from orders o
				join customers c on o.customer_id = c.id
				join order_items oi on oi.order_id = o.id
				join products p on oi.product_id = p.id
				"""
		);
		Object[] params = new Object[] {};
		if (days != null) {
			sql.append(" where o.order_date >= now() - (? * interval '1 day')");
			params = new Object[] { days };
		}
		sql.append(" order by o.order_date desc, o.id desc");
		if (limit != null) {
			sql.append(" limit ").append(limit);
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new SalesRow(
						rs.getInt("id"),
						rs.getString("customer"),
						rs.getString("product"),
						rs.getInt("quantity"),
						rs.getBigDecimal("total"),
						rs.getTimestamp("sale_date").toLocalDateTime()
				),
				params
		);
	}

	private List<DeliveryRow> loadDeliveries(Integer limit) {
		String sql =
				"""
				select odt.order_id as id,
				       c.name as customer,
				       coalesce(sum(oi.quantity * oi.price),0) as total,
				       odt.delivery_date,
				       odt.delivery_status as status
				from order_delivery_tracking odt
				join orders o on odt.order_id = o.id
				join customers c on o.customer_id = c.id
				left join order_items oi on oi.order_id = o.id
				group by odt.order_id, c.name, odt.delivery_date, odt.delivery_status
				order by odt.delivery_date desc, odt.order_id desc
				""";
		if (limit != null) {
			sql += " limit " + limit;
		}
		return jdbcTemplate.query(
				sql,
				(rs, rowNum) -> new DeliveryRow(
						rs.getInt("id"),
						rs.getString("customer"),
						rs.getBigDecimal("total"),
						rs.getDate("delivery_date").toLocalDate(),
						rs.getString("status")
				)
		);
	}

	private static Integer parseDaysPeriod(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase();
		if ("all".equals(normalized)) {
			return null;
		}
		try {
			int parsed = Integer.parseInt(normalized);
			if (parsed == 7 || parsed == 30 || parsed == 90) {
				return parsed;
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	private static boolean isViewAll(String value) {
		return value != null && value.trim().equalsIgnoreCase("all");
	}

	private static int valueOrZero(Integer value) {
		return value == null ? 0 : value;
	}

	private static double valueOrZero(Double value) {
		return value == null ? 0 : value;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static String normalizeFormat(String format) {
		if (format == null) {
			return "pdf";
		}
		String normalized = format.trim().toLowerCase();
		if ("xlsx".equals(normalized) || "excel".equals(normalized)) {
			return "xlsx";
		}
		return "pdf";
	}

	private void writeSalesHistoryPdf(HttpServletResponse response, String filename, List<SalesRow> history)
			throws IOException {
		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (Document document = new Document()) {
			PdfWriter.getInstance(document, response.getOutputStream());
			document.open();
			document.add(new Paragraph("Sales History Report"));
			document.add(new Paragraph("Generated: " + LocalDate.now()));
			document.add(new Paragraph(" "));

			PdfPTable table = new PdfPTable(5);
			table.addCell("Customer");
			table.addCell("Product");
			table.addCell("Qty");
			table.addCell("Total");
			table.addCell("Order Date");
			for (SalesRow row : history) {
				table.addCell(row.customer());
				table.addCell(row.product());
				table.addCell(String.valueOf(row.quantity()));
				table.addCell(String.valueOf(row.total()));
				table.addCell(String.valueOf(row.saleDate()));
			}
			document.add(table);
		} catch (DocumentException ex) {
			throw new IOException("Failed to generate PDF report", ex);
		}
	}

	private void writeSalesHistoryExcel(HttpServletResponse response, String filename, List<SalesRow> history)
			throws IOException {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Sales History");
			int rowIndex = 0;
			Row header = sheet.createRow(rowIndex++);
			header.createCell(0).setCellValue("Customer");
			header.createCell(1).setCellValue("Product");
			header.createCell(2).setCellValue("Qty");
			header.createCell(3).setCellValue("Total");
			header.createCell(4).setCellValue("Order Date");
			for (SalesRow row : history) {
				Row data = sheet.createRow(rowIndex++);
				data.createCell(0).setCellValue(row.customer());
				data.createCell(1).setCellValue(row.product());
				data.createCell(2).setCellValue(row.quantity());
				data.createCell(3).setCellValue(row.total().doubleValue());
				data.createCell(4).setCellValue(row.saleDate().toString());
			}
			workbook.write(response.getOutputStream());
		}
	}

	private void writeOrderPdf(
			HttpServletResponse response,
			String filename,
			Map<String, Object> header,
			List<Map<String, Object>> items,
			BigDecimal total
	) throws IOException {
		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (Document document = new Document()) {
			PdfWriter.getInstance(document, response.getOutputStream());
			document.open();
			document.add(new Paragraph("Order Summary"));
			document.add(new Paragraph("Generated: " + LocalDate.now()));
			document.add(new Paragraph(" "));

			PdfPTable meta = new PdfPTable(2);
			meta.addCell("Order #");
			meta.addCell(String.valueOf(header.getOrDefault("id", "")));
			meta.addCell("Status");
			meta.addCell(String.valueOf(header.getOrDefault("status", "")));
			meta.addCell("Customer");
			meta.addCell(String.valueOf(header.getOrDefault("customer", "")));
			meta.addCell("Contact");
			meta.addCell(String.valueOf(header.getOrDefault("contact", "")));
			meta.addCell("Address");
			meta.addCell(String.valueOf(header.getOrDefault("address", "")));
			meta.addCell("Order Date");
			meta.addCell(String.valueOf(header.getOrDefault("orderDate", "")));
			document.add(meta);
			document.add(new Paragraph(" "));

			PdfPTable table = new PdfPTable(4);
			table.addCell("Item");
			table.addCell("Qty");
			table.addCell("Price");
			table.addCell("Total");
			for (Map<String, Object> item : items) {
				table.addCell(String.valueOf(item.get("name")));
				table.addCell(String.valueOf(item.get("quantity")));
				table.addCell(String.valueOf(item.get("price")));
				table.addCell(String.valueOf(item.get("lineTotal")));
			}
			document.add(table);
			document.add(new Paragraph(" "));
			document.add(new Paragraph("Total: " + String.valueOf(total)));
		} catch (DocumentException ex) {
			throw new IOException("Failed to generate PDF report", ex);
		}
	}

	private void writeOrderExcel(
			HttpServletResponse response,
			String filename,
			Map<String, Object> header,
			List<Map<String, Object>> items,
			BigDecimal total
	) throws IOException {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			Sheet metaSheet = workbook.createSheet("Order Summary");
			int rowIndex = 0;
			rowIndex = writeKeyValue(metaSheet, rowIndex, "Order #", header.get("id"));
			rowIndex = writeKeyValue(metaSheet, rowIndex, "Status", header.get("status"));
			rowIndex = writeKeyValue(metaSheet, rowIndex, "Customer", header.get("customer"));
			rowIndex = writeKeyValue(metaSheet, rowIndex, "Contact", header.get("contact"));
			rowIndex = writeKeyValue(metaSheet, rowIndex, "Address", header.get("address"));
			writeKeyValue(metaSheet, rowIndex, "Order Date", header.get("orderDate"));

			Sheet itemsSheet = workbook.createSheet("Items");
			int itemIndex = 0;
			Row headerRow = itemsSheet.createRow(itemIndex++);
			headerRow.createCell(0).setCellValue("Item");
			headerRow.createCell(1).setCellValue("Qty");
			headerRow.createCell(2).setCellValue("Price");
			headerRow.createCell(3).setCellValue("Total");
			for (Map<String, Object> item : items) {
				Row row = itemsSheet.createRow(itemIndex++);
				row.createCell(0).setCellValue(String.valueOf(item.get("name")));
				row.createCell(1).setCellValue(Integer.parseInt(String.valueOf(item.get("quantity"))));
				row.createCell(2).setCellValue(Double.parseDouble(String.valueOf(item.get("price"))));
				row.createCell(3).setCellValue(Double.parseDouble(String.valueOf(item.get("lineTotal"))));
			}

			Sheet totalSheet = workbook.createSheet("Total");
			Row totalRow = totalSheet.createRow(0);
			totalRow.createCell(0).setCellValue("Total");
			totalRow.createCell(1).setCellValue(total == null ? 0.0 : total.doubleValue());

			workbook.write(response.getOutputStream());
		}
	}

	private static int writeKeyValue(Sheet sheet, int rowIndex, String key, Object value) {
		Row row = sheet.createRow(rowIndex++);
		row.createCell(0).setCellValue(key);
		row.createCell(1).setCellValue(value == null ? "" : String.valueOf(value));
		return rowIndex;
	}

	public record SalesSnapshot(
			double totalRevenue,
			int totalOrders,
			double avgOrderValue,
			int pendingDeliveries,
			int deliveredOrders,
			String periodLabel
	) {
		public static SalesSnapshot empty() {
			return new SalesSnapshot(0, 0, 0, 0, 0, "Last 30 days");
		}
	}

	public record ProductOption(int id, String name, BigDecimal price) { }

	public record CustomerRow(int id, String name, String contact, String address) { }

	public record SalesRow(
			int id,
			String customer,
			String product,
			int quantity,
			BigDecimal total,
			java.time.LocalDateTime saleDate
	) { }

	public record DeliveryRow(
			int id,
			String customer,
			BigDecimal total,
			LocalDate deliveryDate,
			String status
	) { }
}

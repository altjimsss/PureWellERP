package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProcurementController {

	private final JdbcTemplate jdbcTemplate;

	public ProcurementController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/modules/procurement")
	public String procurementModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Procurement and Inventory Module");
		model.addAttribute("moduleName", "Procurement and Inventory");
		model.addAttribute("userProfile", userProfile);
		return "procurement-module";
	}

	@PostMapping("/modules/procurement/suppliers")
	public String addSupplier(
			@RequestParam("name") String name,
			@RequestParam("contact") String contact,
			@RequestParam("address") String address
	) {
		jdbcTemplate.update(
				"""
				insert into suppliers (name, contact, address)
				values (?, ?, ?)
				""",
				name,
				contact,
				address
		);
		return "redirect:/modules/procurement";
	}

	@PostMapping("/modules/procurement/suppliers/delete")
	public String removeSupplier(@RequestParam("supplierId") Integer supplierId) {
		jdbcTemplate.update("delete from purchase_orders where supplier_id = ?", supplierId);
		jdbcTemplate.update("delete from products where supplier_id = ?", supplierId);
		jdbcTemplate.update("delete from suppliers where id = ?", supplierId);
		return "redirect:/modules/procurement";
	}

	@PostMapping("/modules/procurement/products")
	public String addProduct(
			@RequestParam("name") String name,
			@RequestParam("type") String type,
			@RequestParam("price") BigDecimal price,
			@RequestParam("stock") Integer stock,
			@RequestParam(name = "supplierId", required = false) Integer supplierId
	) {
		jdbcTemplate.update(
				"""
				insert into products (name, type, price, stock, supplier_id)
				values (?, ?, ?, ?, ?)
				""",
				name,
				type,
				price,
				stock,
				supplierId
		);
		return "redirect:/modules/procurement";
	}

	@PostMapping("/modules/procurement/products/delete")
	public String removeProduct(@RequestParam("productId") Integer productId) {
		jdbcTemplate.update("delete from purchase_orders where product_id = ?", productId);
		jdbcTemplate.update("delete from products where id = ?", productId);
		return "redirect:/modules/procurement";
	}

	@PostMapping("/modules/procurement/purchase-orders/status")
	public String updatePurchaseOrderStatus(
			@RequestParam("orderId") Integer orderId,
			@RequestParam("status") String status
	) {
		jdbcTemplate.update(
				"update purchase_orders set status = ? where id = ?",
				status,
				orderId
		);
		return "redirect:/modules/procurement";
	}

	private ProcurementSnapshot loadSnapshot() {
		Integer supplierCount = jdbcTemplate.queryForObject(
				"select count(*) from suppliers", Integer.class);
		Integer productCount = jdbcTemplate.queryForObject(
				"select count(*) from products", Integer.class);
		Integer poCount = jdbcTemplate.queryForObject(
				"select count(*) from purchase_orders", Integer.class);
		Integer pendingPoCount = jdbcTemplate.queryForObject(
				"select count(*) from purchase_orders where status = 'Pending'", Integer.class);
		Integer lowStockCount = jdbcTemplate.queryForObject(
				"select count(*) from products where stock < 50", Integer.class);

		return new ProcurementSnapshot(
				valueOrZero(supplierCount),
				valueOrZero(productCount),
				valueOrZero(poCount),
				valueOrZero(pendingPoCount),
				valueOrZero(lowStockCount)
		);
	}

	private List<SupplierRow> loadSuppliers(Integer limit) {
		StringBuilder sql = new StringBuilder(
				"select id, name, contact, address from suppliers order by id desc");
		if (limit != null) {
			sql.append(" limit ").append(limit);
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new SupplierRow(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getString("contact"),
						rs.getString("address")
				)
		);
	}

	private List<PurchaseOrderRow> loadPurchaseOrders(Integer days, Integer limit) {
		StringBuilder sql = new StringBuilder(
				"""
				select po.id,
				       s.name as supplier_name,
				       p.name as product_name,
				       po.quantity,
				       po.order_date,
				       po.status
				from purchase_orders po
				join suppliers s on po.supplier_id = s.id
				join products p on po.product_id = p.id
				""");
		Object[] params = new Object[] {};
		if (days != null) {
			sql.append(" where po.order_date >= current_date - (? * interval '1 day')");
			params = new Object[] { days };
		}
		sql.append(" order by po.order_date desc, po.id desc");
		if (limit != null) {
			sql.append(" limit ").append(limit);
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new PurchaseOrderRow(
						rs.getInt("id"),
						rs.getString("supplier_name"),
						rs.getString("product_name"),
						rs.getInt("quantity"),
						rs.getDate("order_date").toLocalDate(),
						rs.getString("status")
				),
				params
		);
	}

	private List<InventoryRow> loadInventoryItems(String view) {
		boolean lowOnly = view == null || view.trim().equalsIgnoreCase("low");
		StringBuilder sql = new StringBuilder(
				"""
				select p.id,
				       p.name,
				       p.type,
				       p.stock,
				       p.price,
				       s.name as supplier_name
				from products p
				left join suppliers s on p.supplier_id = s.id
				""");
		if (lowOnly) {
			sql.append(" where p.stock < 50");
		}
		sql.append(" order by p.stock asc, p.id desc");
		if (!isViewAll(view)) {
			sql.append(" limit 12");
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new InventoryRow(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getString("type"),
						rs.getInt("stock"),
						rs.getBigDecimal("price"),
						rs.getString("supplier_name")
				)
		);
	}

	private static int valueOrZero(Integer value) {
		return value == null ? 0 : value;
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

	public record ProcurementSnapshot(
			int supplierCount,
			int productCount,
			int poCount,
			int pendingPoCount,
			int lowStockCount
	) {
		public static ProcurementSnapshot empty() {
			return new ProcurementSnapshot(0, 0, 0, 0, 0);
		}
	}

	public record SupplierRow(int id, String name, String contact, String address) { }

	public record PurchaseOrderRow(
			int id,
			String supplierName,
			String productName,
			int quantity,
			LocalDate orderDate,
			String status
	) { }

	public record InventoryRow(
			int id,
			String name,
			String type,
			int stock,
			BigDecimal price,
			String supplierName
	) { }
}

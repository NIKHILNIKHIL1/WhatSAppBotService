package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.report.ReportService;
import com.bot.whatsappbotservice.report.dto.CustomerSummary;
import com.bot.whatsappbotservice.report.dto.DailyRevenueSummary;
import com.bot.whatsappbotservice.report.dto.DateRange;
import com.bot.whatsappbotservice.report.dto.EngagementSummary;
import com.bot.whatsappbotservice.report.dto.InventoryMovementSummary;
import com.bot.whatsappbotservice.report.dto.OutstandingReceivable;
import com.bot.whatsappbotservice.report.dto.ProductPerformanceSummary;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Controller
@RequestMapping("/ui/reports")
public class ReportUiController {

    private final ReportService reportService;

    public ReportUiController(ReportService reportService) {
        this.reportService = reportService;
    }

    // ─── Page handlers ───────────────────────────────────────────────────────

    @GetMapping
    public String overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("overview", reportService.getOverview(range));
        return "ui/reports/index";
    }

    @GetMapping("/revenue")
    public String revenuePage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("dailyRevenue", reportService.getDailyRevenue(range));
        return "ui/reports/revenue";
    }

    @GetMapping("/products")
    public String productsPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("products", reportService.getProductPerformance(range));
        return "ui/reports/products";
    }

    @GetMapping("/customers")
    public String customersPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("customers", reportService.getTopCustomers(range));
        return "ui/reports/customers";
    }

    @GetMapping("/inventory")
    public String inventoryPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("movements", reportService.getInventoryMovements(range));
        return "ui/reports/inventory";
    }

    @GetMapping("/orders")
    public String ordersPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("statusCounts", reportService.getOrderStatusCounts(range));
        return "ui/reports/orders";
    }

    @GetMapping("/payments")
    public String paymentsPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("paymentSummary", reportService.getPaymentSummary(range));
        model.addAttribute("receivables", reportService.getOutstandingReceivables());
        return "ui/reports/payments";
    }

    @GetMapping("/whatsapp")
    public String whatsappPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        DateRange range = reportService.resolveDateRange(from, to);
        addCommonModel(model, range);
        model.addAttribute("engagement", reportService.getEngagementSummary(range));
        return "ui/reports/whatsapp";
    }

    // ─── CSV export handlers ─────────────────────────────────────────────────

    @GetMapping(value = "/revenue/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<DailyRevenueSummary> data = reportService.getDailyRevenue(range);
        return csvResponse("revenue-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Date,Orders,Revenue,Avg Order Value");
            for (DailyRevenueSummary r : data) {
                writeCsv(w, r.date().toString(), String.valueOf(r.orderCount()),
                        r.revenue().toPlainString(), r.averageOrderValue().toPlainString());
            }
            w.flush();
        });
    }

    @GetMapping(value = "/products/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<ProductPerformanceSummary> data = reportService.getProductPerformance(range);
        return csvResponse("products-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("SKU,Product,Category,Qty Sold,Orders,Revenue,Current Stock");
            for (ProductPerformanceSummary p : data) {
                writeCsv(w, p.sku(), p.productName(),
                        p.categoryName() != null ? p.categoryName() : "",
                        p.quantitySold().toPlainString(), String.valueOf(p.orderCount()),
                        p.revenue().toPlainString(), p.currentStock().toPlainString());
            }
            w.flush();
        });
    }

    @GetMapping(value = "/customers/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCustomers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<CustomerSummary> data = reportService.getTopCustomers(range);
        return csvResponse("customers-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Customer,Phone,Orders,Lifetime Spend,Last Order Date");
            for (CustomerSummary c : data) {
                String lastOrder = c.lastOrderAt() != null
                        ? c.lastOrderAt().atZone(ZoneId.of("UTC")).toLocalDate().toString() : "";
                writeCsv(w, c.fullName(), c.phoneNumber(), String.valueOf(c.lifetimeOrders()),
                        c.lifetimeSpend().toPlainString(), lastOrder);
            }
            w.flush();
        });
    }

    @GetMapping(value = "/inventory/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportInventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<InventoryMovementSummary> data = reportService.getInventoryMovements(range);
        return csvResponse("inventory-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Date,Stock In,Stock Out,Net Change");
            for (InventoryMovementSummary m : data) {
                writeCsv(w, m.date().toString(), m.totalIn().toPlainString(),
                        m.totalOut().toPlainString(), m.netChange().toPlainString());
            }
            w.flush();
        });
    }

    @GetMapping(value = "/orders/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        Map<String, Long> data = reportService.getOrderStatusCounts(range);
        return csvResponse("orders-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Status,Count");
            data.forEach((status, count) -> writeCsv(w, status, String.valueOf(count)));
            w.flush();
        });
    }

    @GetMapping(value = "/payments/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportPayments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<OutstandingReceivable> data = reportService.getOutstandingReceivables();
        return csvResponse("receivables-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Customer,Phone,Unpaid Orders,Outstanding Amount,Oldest Unpaid Date");
            for (OutstandingReceivable r : data) {
                String oldest = r.oldestUnpaidOrderAt() != null
                        ? r.oldestUnpaidOrderAt().atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        : "";
                writeCsv(w, r.customerName(), r.phoneNumber(),
                        String.valueOf(r.unpaidOrderCount()),
                        r.outstandingAmount().toPlainString(), oldest);
            }
            w.flush();
        });
    }

    @GetMapping(value = "/whatsapp/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportWhatsapp(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        DateRange range = reportService.resolveDateRange(from, to);
        List<EngagementSummary> data = reportService.getEngagementSummary(range);
        return csvResponse("whatsapp-" + range.from() + "-" + range.to() + ".csv", out -> {
            PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            w.println("Date,Sent,Delivered,Read,Failed,Inbound,Delivery Rate %,Read Rate %");
            for (EngagementSummary e : data) {
                writeCsv(w, e.date().toString(), String.valueOf(e.sent()),
                        String.valueOf(e.delivered()), String.valueOf(e.read()),
                        String.valueOf(e.failed()), String.valueOf(e.inbound()),
                        String.format("%.1f", e.deliveryRate()), String.format("%.1f", e.readRate()));
            }
            w.flush();
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void addCommonModel(Model model, DateRange range) {
        model.addAttribute("dateRange", range);
        model.addAttribute("fromDate", range.from());
        model.addAttribute("toDate", range.to());
        model.addAttribute("currency", reportService.getCurrencyCode());
        DateFilterPresets.addTo(model, range.zone());
    }

    private static ResponseEntity<StreamingResponseBody> csvResponse(
            String filename, StreamingResponseBody body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static void writeCsv(PrintWriter w, String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            String v = values[i] == null ? "" : values[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        w.println(sb);
    }
}

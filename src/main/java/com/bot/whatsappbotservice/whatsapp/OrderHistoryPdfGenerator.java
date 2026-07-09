package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.order.dto.OrderResponse;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Renders a customer's order history (weekly/monthly/yearly PDF export from the WhatsApp bot) as a
 * simple one-table PDF. Pure/offline — no I/O beyond the in-memory byte buffer — so it's cheap to
 * unit test directly and safe to call inline from the conversation turn.
 */
@Component
public class OrderHistoryPdfGenerator {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Color HEADER_BACKGROUND = new Color(230, 230, 230);

    private final WhatsAppMessages messages;

    public OrderHistoryPdfGenerator(WhatsAppMessages messages) {
        this.messages = messages;
    }

    public byte[] generate(Tenant tenant, Customer customer, List<OrderResponse> orders, String periodLabel,
                            String lang) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            Locale locale = Locale.forLanguageTag(lang);
            DateTimeFormatter generatedAtFormat = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", locale)
                    .withZone(ZoneOffset.UTC);
            DateTimeFormatter orderDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", locale)
                    .withZone(ZoneOffset.UTC);

            document.add(new Paragraph(messages.get("bot.orders.pdf_title", lang), TITLE_FONT));
            String customerLabel = customer.getFullName() != null
                    ? customer.getFullName() + " (" + customer.getPhoneNumber() + ")"
                    : customer.getPhoneNumber();
            document.add(new Paragraph(messages.get("bot.orders.pdf_customer_line", lang, customerLabel), NORMAL_FONT));
            document.add(new Paragraph(messages.get("bot.orders.pdf_period_line", lang, periodLabel), NORMAL_FONT));
            document.add(new Paragraph(
                    messages.get("bot.orders.pdf_generated_line", lang, generatedAtFormat.format(Instant.now())),
                    NORMAL_FONT));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {2.5f, 1.5f, 1.5f, 1.5f});
            addHeaderCell(table, messages.get("bot.orders.pdf_col_order", lang));
            addHeaderCell(table, messages.get("bot.orders.pdf_col_date", lang));
            addHeaderCell(table, messages.get("bot.orders.pdf_col_status", lang));
            addHeaderCell(table, messages.get("bot.orders.pdf_col_total", lang));

            BigDecimal grandTotal = BigDecimal.ZERO;
            String currencyCode = tenant.getCurrencyCode();
            for (OrderResponse order : orders) {
                table.addCell(new Phrase(order.orderNumber(), NORMAL_FONT));
                table.addCell(new Phrase(orderDateFormat.format(order.createdAt()), NORMAL_FONT));
                table.addCell(new Phrase(order.status().name(), NORMAL_FONT));
                table.addCell(new Phrase(order.totalAmount() + " " + order.currencyCode(), NORMAL_FONT));
                grandTotal = grandTotal.add(order.totalAmount());
            }
            document.add(table);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph(
                    messages.get("bot.orders.pdf_total_line", lang, grandTotal + " " + currencyCode), HEADER_FONT));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate order history PDF", e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, HEADER_FONT));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        table.addCell(cell);
    }
}

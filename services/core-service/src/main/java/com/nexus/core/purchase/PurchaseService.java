package com.nexus.core.purchase;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import com.nexus.core.shared.PaymentStatus;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of buying: suppliers and purchases.
 *
 * <p>Saving a purchase also puts its goods into stock, all in one transaction: if any line is
 * wrong, nothing is saved, neither the purchase nor any unit or lot.
 */
@Service
public class PurchaseService {

    static final int MAX_ITEMS_PER_LINE = 1000;

    private final PurchaseRepository repository;
    private final CatalogService catalog;
    private final StockService stock;
    private final PurchasePayments payments;

    PurchaseService(PurchaseRepository repository, CatalogService catalog, StockService stock,
                    PurchasePayments payments) {
        this.repository = repository;
        this.catalog = catalog;
        this.stock = stock;
        this.payments = payments;
    }

    @Transactional
    public Supplier createSupplier(String name, String country, String contact, String notes) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Supplier name is required");
        }
        long id = repository.insertSupplier(name.strip(), blankToNull(country), blankToNull(contact),
                blankToNull(notes));
        return getSupplier(id);
    }

    @Transactional(readOnly = true)
    public Supplier getSupplier(long id) {
        return repository.findSupplier(id).orElseThrow(() -> new NotFoundException("Supplier " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Supplier> listSuppliers() {
        return repository.findSuppliers();
    }

    @Transactional
    public Purchase createPurchase(long supplierId, LocalDate purchaseDate, String currency, String notes,
                                   List<NewPurchaseLine> lines) {
        getSupplier(supplierId);
        String currencyCode = requireCurrency(currency);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("A purchase needs at least one line");
        }
        LocalDate date = purchaseDate == null ? LocalDate.now() : purchaseDate;
        long purchaseId = repository.insertPurchase(supplierId, date, currencyCode, blankToNull(notes));

        int lineNo = 1;
        for (NewPurchaseLine line : lines) {
            String where = "Line " + lineNo;
            try {
                addLine(purchaseId, lineNo, line, currencyCode, date);
            } catch (BusinessRuleException | NotFoundException e) {
                throw new BusinessRuleException(where + ": " + e.getMessage());
            }
            lineNo++;
        }
        return getPurchase(purchaseId);
    }

    @Transactional(readOnly = true)
    public Purchase getPurchase(String publicId) {
        return repository.findPurchaseByPublicId(publicId)
                .map(this::assemble)
                .orElseThrow(() -> new NotFoundException("Purchase " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Purchase> listPurchases() {
        return repository.findPurchases().stream().map(this::assemble).toList();
    }

    private Purchase getPurchase(long id) {
        return repository.findPurchase(id).map(this::assemble).orElseThrow();
    }

    private void addLine(long purchaseId, int lineNo, NewPurchaseLine line, String currency, LocalDate date) {
        if (line == null || line.productId() == null || line.locationId() == null) {
            throw new BusinessRuleException("productId and locationId are required");
        }
        Money unitPrice = requirePrice(line.unitPrice(), currency);
        Product product = catalog.getProduct(line.productId());
        TrackingMode mode = catalog.getCategory(product.categoryId()).trackingMode();
        List<Map<String, String>> items = line.items() == null ? List.of() : line.items();
        int quantity = quantityOf(line.quantity(), items, mode);

        long lineId = repository.insertLine(purchaseId, lineNo, line.productId(), line.locationId(), quantity,
                unitPrice.amount());
        if (mode == TrackingMode.UNIT) {
            List<Map<String, String>> pieces = items.isEmpty() ? Collections.nCopies(quantity, Map.of()) : items;
            for (Map<String, String> values : pieces) {
                stock.receiveUnit(line.productId(), line.locationId(), unitPrice, date,
                        values == null ? Map.of() : values, lineId);
            }
        } else {
            stock.receiveLot(line.productId(), line.locationId(), quantity, unitPrice, date, lineId);
        }
    }

    /** Phones: one entry in {@code items} per piece. Sneakers: just a quantity. */
    private static int quantityOf(Integer quantity, List<Map<String, String>> items, TrackingMode mode) {
        if (mode == TrackingMode.LOT && !items.isEmpty()) {
            throw new BusinessRuleException("items are only for goods tracked per item: give a quantity instead");
        }
        int result;
        if (!items.isEmpty()) {
            if (quantity != null && quantity != items.size()) {
                throw new BusinessRuleException("quantity is " + quantity + " but " + items.size()
                        + " items were given");
            }
            result = items.size();
        } else {
            if (quantity == null || quantity < 1) {
                throw new BusinessRuleException("quantity must be at least 1");
            }
            result = quantity;
        }
        if (mode == TrackingMode.UNIT && result > MAX_ITEMS_PER_LINE) {
            throw new BusinessRuleException("at most " + MAX_ITEMS_PER_LINE + " items per line");
        }
        return result;
    }

    private Purchase assemble(PurchaseRepository.PurchaseRow row) {
        Money total = Money.zero(row.currency());
        List<PurchaseLine> lines = new java.util.ArrayList<>();
        for (PurchaseRepository.LineRow line : repository.findLines(row.id())) {
            Money unitPrice = Money.of(line.unitPrice().toPlainString(), row.currency());
            Money lineTotal = unitPrice.times(line.quantity());
            total = total.plus(lineTotal);
            lines.add(new PurchaseLine(line.id(), line.lineNo(), line.productId(), line.locationId(),
                    line.quantity(), unitPrice, lineTotal, stock.unitIdsOfPurchaseLine(line.id()),
                    stock.lotIdOfPurchaseLine(line.id()).orElse(null)));
        }
        Money paid = Money.of(payments.paidForPurchase(row.id()).toPlainString(), row.currency());
        return new Purchase(row.id(), row.publicId(), row.supplierId(), row.supplierName(), row.purchaseDate(),
                row.currency(), row.notes(), lines, total, paid, total.minus(paid), PaymentStatus.of(total, paid));
    }

    private static String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleException("Currency is required");
        }
        try {
            return Money.zero(currency.strip().toUpperCase()).currency().getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown currency: " + currency);
        }
    }

    private static Money requirePrice(String amount, String currency) {
        if (amount == null || amount.isBlank()) {
            throw new BusinessRuleException("unitPrice is required");
        }
        Money price;
        try {
            price = Money.of(amount.strip(), currency);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("unitPrice is not a valid amount: " + amount);
        }
        if (price.isNegative()) {
            throw new BusinessRuleException("unitPrice cannot be negative");
        }
        return price;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

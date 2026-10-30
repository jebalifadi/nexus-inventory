package com.nexus.core.sale;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.LotTaken;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of selling: customers and sales.
 *
 * <p>Saving a sale takes the goods out of stock, freezes what they really cost (purchase plus
 * shipping), and calculates the profit and warranty end date of each line. All in one transaction:
 * if one line is wrong, nothing is saved.
 */
@Service
public class SaleService {

    static final int MAX_WARRANTY_DAYS = 3650;

    private final SaleRepository repository;
    private final CatalogService catalog;
    private final StockService stock;
    private final ShipmentService shipments;

    SaleService(SaleRepository repository, CatalogService catalog, StockService stock, ShipmentService shipments) {
        this.repository = repository;
        this.catalog = catalog;
        this.stock = stock;
        this.shipments = shipments;
    }

    @Transactional
    public Customer createCustomer(String name, String phone, String email, String country, String notes) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Customer name is required");
        }
        long id = repository.insertCustomer(name.strip(), blankToNull(phone), blankToNull(email),
                blankToNull(country), blankToNull(notes));
        return getCustomer(id);
    }

    @Transactional(readOnly = true)
    public Customer getCustomer(long id) {
        return repository.findCustomer(id).orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Customer> listCustomers() {
        return repository.findCustomers();
    }

    /**
     * @param customerId optional: a walk-in buyer in the shop does not need to be recorded
     */
    @Transactional
    public Sale createSale(Long customerId, long locationId, LocalDate saleDate, String currency, String notes,
                           List<NewSaleLine> lines) {
        if (customerId != null) {
            getCustomer(customerId);
        }
        stock.getLocation(locationId);
        String currencyCode = requireCurrency(currency);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("A sale needs at least one line");
        }
        long saleId = repository.insertSale(customerId, locationId, saleDate == null ? LocalDate.now() : saleDate,
                currencyCode, blankToNull(notes));

        int lineNo = 1;
        for (NewSaleLine line : lines) {
            try {
                addLine(saleId, lineNo, line, locationId, currencyCode);
            } catch (BusinessRuleException | NotFoundException e) {
                throw new BusinessRuleException("Line " + lineNo + ": " + e.getMessage());
            }
            lineNo++;
        }
        return assemble(repository.findSale(saleId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Sale getSale(String publicId) {
        return repository.findSaleByPublicId(publicId)
                .map(this::assemble)
                .orElseThrow(() -> new NotFoundException("Sale " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Sale> listSales() {
        return repository.findSales().stream().map(this::assemble).toList();
    }

    private void addLine(long saleId, int lineNo, NewSaleLine line, long locationId, String currency) {
        if (line == null) {
            throw new BusinessRuleException("line is empty");
        }
        Money unitPrice = requirePrice(line.unitPrice(), currency);
        int warrantyDays = requireWarrantyDays(line.warrantyDays());
        String warrantyTerms = blankToNull(line.warrantyTerms());

        if (line.unitId() != null && !line.unitId().isBlank()) {
            if (line.quantity() != null && line.quantity() != 1) {
                throw new BusinessRuleException("a unit line always sells exactly 1 piece");
            }
            InventoryUnit unit = stock.sellUnit(line.unitId().strip(), locationId);
            if (line.productId() != null && !line.productId().equals(unit.productId())) {
                throw new BusinessRuleException("unit " + unit.publicId() + " is not product " + line.productId());
            }
            Money cost = landed(unit.unitCost(), shipments.shippingCostPerPieceOfUnit(unit.id()));
            repository.insertLine(saleId, lineNo, unit.productId(), unit.id(), 1, unitPrice.amount(),
                    cost == null ? null : cost.amount(), cost == null ? null : code(cost), warrantyDays, warrantyTerms);
            return;
        }

        if (line.productId() == null) {
            throw new BusinessRuleException("give a unitId, or a productId with a quantity");
        }
        Product product = catalog.getProduct(line.productId());
        if (catalog.getCategory(product.categoryId()).trackingMode() == TrackingMode.UNIT) {
            throw new BusinessRuleException("product " + product.sku() + " is tracked per item: "
                    + "choose which unit (U-...) to sell");
        }
        if (line.quantity() == null || line.quantity() < 1) {
            throw new BusinessRuleException("quantity must be at least 1");
        }

        List<LotTaken> taken = stock.takeFromLots(product.id(), locationId, line.quantity());
        List<Money> pieceCosts = new ArrayList<>();
        for (LotTaken take : taken) {
            pieceCosts.add(landed(take.lot().unitCost(), shipments.shippingCostPerPieceOfLot(take.lot().id())));
        }
        Money lineCost = totalCost(taken, pieceCosts);
        long lineId = repository.insertLine(saleId, lineNo, product.id(), null, line.quantity(), unitPrice.amount(),
                lineCost == null ? null : lineCost.amount(), lineCost == null ? null : code(lineCost),
                warrantyDays, warrantyTerms);
        for (int i = 0; i < taken.size(); i++) {
            Money pieceCost = pieceCosts.get(i);
            repository.insertAllocation(lineId, taken.get(i).lot().id(), taken.get(i).quantity(),
                    pieceCost == null ? null : pieceCost.amount(), pieceCost == null ? null : code(pieceCost));
        }
    }

    /** Purchase cost plus every shipping share, or empty when they are in different currencies. */
    private static Money landed(Money purchaseCost, List<Money> shippingShares) {
        Money total = purchaseCost;
        for (Money share : shippingShares) {
            if (!share.currency().equals(total.currency())) {
                return null;
            }
            total = total.plus(share);
        }
        return total;
    }

    private static Money totalCost(List<LotTaken> taken, List<Money> pieceCosts) {
        Money total = null;
        for (int i = 0; i < taken.size(); i++) {
            Money pieceCost = pieceCosts.get(i);
            if (pieceCost == null || (total != null && !total.currency().equals(pieceCost.currency()))) {
                return null;
            }
            Money part = pieceCost.times(taken.get(i).quantity());
            total = total == null ? part : total.plus(part);
        }
        return total;
    }

    private Sale assemble(SaleRepository.SaleRow row) {
        List<SaleLine> lines = new ArrayList<>();
        Money total = Money.zero(row.currency());
        Money profit = Money.zero(row.currency());
        boolean profitKnown = true;
        for (SaleRepository.LineRow line : repository.findLines(row.id())) {
            Money unitPrice = Money.of(line.unitPrice().toPlainString(), row.currency());
            Money lineTotal = unitPrice.times(line.quantity());
            Money cost = line.costAmount() == null ? null
                    : Money.of(line.costAmount().toPlainString(), line.costCurrency());
            Money lineProfit = cost != null && cost.currency().equals(lineTotal.currency())
                    ? lineTotal.minus(cost) : null;
            List<SaleAllocation> allocations = repository.findAllocations(line.id()).stream()
                    .map(a -> new SaleAllocation(a.lotPublicId(), a.quantity(), a.unitCostAmount() == null ? null
                            : Money.of(a.unitCostAmount().toPlainString(), a.unitCostCurrency())))
                    .toList();
            LocalDate warrantyEnd = line.warrantyDays() == 0 ? null : row.saleDate().plusDays(line.warrantyDays());

            lines.add(new SaleLine(line.id(), line.lineNo(), line.productId(), line.unitPublicId(), line.quantity(),
                    unitPrice, lineTotal, cost, lineProfit, allocations, line.warrantyDays(), warrantyEnd,
                    line.warrantyTerms()));
            total = total.plus(lineTotal);
            if (lineProfit == null) {
                profitKnown = false;
            } else {
                profit = profit.plus(lineProfit);
            }
        }
        return new Sale(row.id(), row.publicId(), row.customerId(), row.customerName(), row.locationId(),
                row.saleDate(), row.currency(), row.notes(), lines, total, profitKnown ? profit : null);
    }

    private static int requireWarrantyDays(Integer days) {
        int value = days == null ? 0 : days;
        if (value < 0 || value > MAX_WARRANTY_DAYS) {
            throw new BusinessRuleException("warrantyDays must be between 0 and " + MAX_WARRANTY_DAYS);
        }
        return value;
    }

    private static String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleException("Currency is required");
        }
        try {
            return code(Money.zero(currency.strip().toUpperCase()));
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

    private static String code(Money money) {
        return money.currency().getCurrencyCode();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

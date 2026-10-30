package com.nexus.core.stock;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of stock: warehouses, receiving goods, and the life cycle of individual items. */
@Service
public class StockService {

    private final StockRepository repository;
    private final CatalogService catalog;

    StockService(StockRepository repository, CatalogService catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional
    public Location createLocation(String name, String country) {
        if (name == null || name.isBlank() || country == null || country.isBlank()) {
            throw new BusinessRuleException("Location name and country are required");
        }
        long id = repository.insertLocation(name.trim(), country.trim());
        return getLocation(id);
    }

    @Transactional(readOnly = true)
    public Location getLocation(long id) {
        return repository.findLocation(id).orElseThrow(() -> new NotFoundException("Location " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Location> listLocations() {
        return repository.findLocations();
    }

    /** Puts one individually tracked item into stock, for example one phone with its IMEI. */
    @Transactional
    public InventoryUnit receiveUnit(long productId, long locationId, Money unitCost, LocalDate receivedDate,
                                     Map<String, String> itemValues) {
        Product product = catalog.getProduct(productId);
        Category category = catalog.getCategory(product.categoryId());
        requireTrackingMode(category, TrackingMode.UNIT);
        getLocation(locationId);
        requireValidCost(unitCost);
        Map<String, String> values = catalog.validateItemAttributes(category, itemValues);

        long unitId = repository.insertUnit(productId, locationId, UnitStatus.IN_INVENTORY, unitCost,
                receivedDate == null ? LocalDate.now() : receivedDate);
        values.forEach((key, value) ->
                repository.insertUnitAttribute(unitId, category.field(key).orElseThrow().id(), value));
        repository.insertStatusChange(unitId, null, UnitStatus.IN_INVENTORY);
        return repository.findUnitById(unitId).orElseThrow();
    }

    /** Puts a batch of identical goods into stock, for example 10 pairs of sneakers. */
    @Transactional
    public InventoryLot receiveLot(long productId, long locationId, int quantity, Money unitCost,
                                   LocalDate receivedDate) {
        Product product = catalog.getProduct(productId);
        requireTrackingMode(catalog.getCategory(product.categoryId()), TrackingMode.LOT);
        getLocation(locationId);
        requireValidCost(unitCost);
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be at least 1");
        }
        long lotId = repository.insertLot(productId, locationId, quantity, unitCost,
                receivedDate == null ? LocalDate.now() : receivedDate);
        return repository.findLotById(lotId).orElseThrow();
    }

    /** Moves an item to its next status, refusing steps its life cycle does not allow. */
    @Transactional
    public InventoryUnit changeUnitStatus(String publicId, UnitStatus next) {
        if (next == null) {
            throw new BusinessRuleException("New status is required");
        }
        InventoryUnit unit = repository.lockUnitByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Unit " + publicId + " not found"));
        if (!unit.status().canMoveTo(next)) {
            throw new BusinessRuleException(
                    "Unit " + publicId + " cannot go from " + unit.status() + " to " + next);
        }
        repository.updateUnitStatus(unit.id(), next);
        repository.insertStatusChange(unit.id(), unit.status(), next);
        return repository.findUnitById(unit.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public InventoryUnit getUnit(String publicId) {
        return repository.findUnitByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Unit " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryUnit> listUnits() {
        return repository.findUnits();
    }

    @Transactional(readOnly = true)
    public InventoryLot getLot(String publicId) {
        return repository.findLotByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Lot " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryLot> listLots() {
        return repository.findLots();
    }

    /** Quantity in stock per product and location, units and lots together. */
    @Transactional(readOnly = true)
    public List<StockLevel> stockLevels() {
        return repository.findStockLevels();
    }

    private static void requireTrackingMode(Category category, TrackingMode expected) {
        if (category.trackingMode() != expected) {
            throw new BusinessRuleException(expected == TrackingMode.UNIT
                    ? "Category '" + category.name() + "' is tracked by quantity: receive a lot instead"
                    : "Category '" + category.name() + "' is tracked per item: receive units instead");
        }
    }

    private static void requireValidCost(Money cost) {
        if (cost == null) {
            throw new BusinessRuleException("Cost is required");
        }
        if (cost.isNegative()) {
            throw new BusinessRuleException("Cost cannot be negative");
        }
    }
}

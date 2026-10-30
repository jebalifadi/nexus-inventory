package com.nexus.core.stock;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import com.nexus.core.trash.TrashRepository;
import com.nexus.core.trash.TrashType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of stock: warehouses, receiving goods, and the life cycle of individual items. */
@Service
public class StockService {

    private final StockRepository repository;
    private final CatalogService catalog;
    private final TrashRepository trash;

    StockService(StockRepository repository, CatalogService catalog, TrashRepository trash) {
        this.repository = repository;
        this.catalog = catalog;
        this.trash = trash;
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
        return repository.findLocation(id)
                .orElseThrow(() -> trash.notFound(TrashType.LOCATION, id, "Location " + id));
    }

    @Transactional(readOnly = true)
    public List<Location> listLocations() {
        return repository.findLocations();
    }

    /** Puts one individually tracked item into stock, for example one phone with its IMEI. */
    @Transactional
    public InventoryUnit receiveUnit(long productId, long locationId, Money unitCost, LocalDate receivedDate,
                                     Map<String, String> itemValues) {
        return receiveUnit(productId, locationId, unitCost, receivedDate, itemValues, null);
    }

    /** Same as above, remembering the purchase line that bought the item. */
    @Transactional
    public InventoryUnit receiveUnit(long productId, long locationId, Money unitCost, LocalDate receivedDate,
                                     Map<String, String> itemValues, Long purchaseLineId) {
        Product product = catalog.getProduct(productId);
        Category category = catalog.getCategory(product.categoryId());
        requireTrackingMode(category, TrackingMode.UNIT);
        getLocation(locationId);
        requireValidCost(unitCost);
        Map<String, String> values = catalog.validateItemAttributes(category, itemValues);

        long unitId = repository.insertUnit(productId, locationId, UnitStatus.IN_INVENTORY, unitCost,
                receivedDate == null ? LocalDate.now() : receivedDate, purchaseLineId);
        values.forEach((key, value) ->
                repository.insertUnitAttribute(unitId, category.field(key).orElseThrow().id(), value));
        repository.insertStatusChange(unitId, null, UnitStatus.IN_INVENTORY);
        return repository.findUnitById(unitId).orElseThrow();
    }

    /** Puts a batch of identical goods into stock, for example 10 pairs of sneakers. */
    @Transactional
    public InventoryLot receiveLot(long productId, long locationId, int quantity, Money unitCost,
                                   LocalDate receivedDate) {
        return receiveLot(productId, locationId, quantity, unitCost, receivedDate, null);
    }

    /** Same as above, remembering the purchase line that bought the goods. */
    @Transactional
    public InventoryLot receiveLot(long productId, long locationId, int quantity, Money unitCost,
                                   LocalDate receivedDate, Long purchaseLineId) {
        Product product = catalog.getProduct(productId);
        requireTrackingMode(catalog.getCategory(product.categoryId()), TrackingMode.LOT);
        getLocation(locationId);
        requireValidCost(unitCost);
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be at least 1");
        }
        long lotId = repository.insertLot(productId, locationId, quantity, unitCost,
                receivedDate == null ? LocalDate.now() : receivedDate, purchaseLineId);
        return repository.findLotById(lotId).orElseThrow();
    }

    /** Moves an item to its next status, refusing steps its life cycle does not allow. */
    @Transactional
    public InventoryUnit changeUnitStatus(String publicId, UnitStatus next) {
        if (next == null) {
            throw new BusinessRuleException("New status is required");
        }
        InventoryUnit unit = repository.lockUnitByPublicId(publicId).orElseThrow(() -> unitNotFound(publicId));
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
        return repository.findUnitByPublicId(publicId).orElseThrow(() -> unitNotFound(publicId));
    }

    @Transactional(readOnly = true)
    public List<InventoryUnit> listUnits() {
        return repository.findUnits();
    }

    @Transactional(readOnly = true)
    public InventoryLot getLot(String publicId) {
        return repository.findLotByPublicId(publicId).orElseThrow(() -> lotNotFound(publicId));
    }

    @Transactional(readOnly = true)
    public List<InventoryLot> listLots() {
        return repository.findLots();
    }

    /** Takes a unit out of stock to put it in a shipment. */
    @Transactional
    public InventoryUnit sendUnit(String publicId, long fromLocationId) {
        InventoryUnit unit = repository.lockUnitByPublicId(publicId).orElseThrow(() -> unitNotFound(publicId));
        if (unit.status() != UnitStatus.IN_INVENTORY) {
            throw new BusinessRuleException("Unit " + publicId + " is " + unit.status()
                    + ": only units in inventory can be shipped");
        }
        if (unit.locationId() != fromLocationId) {
            throw new BusinessRuleException("Unit " + publicId + " is not at location " + fromLocationId);
        }
        repository.updateUnitStatus(unit.id(), UnitStatus.IN_TRANSIT);
        repository.insertStatusChange(unit.id(), unit.status(), UnitStatus.IN_TRANSIT);
        return repository.findUnitById(unit.id()).orElseThrow();
    }

    /** Takes a whole lot out of stock to put it in a shipment. */
    @Transactional
    public InventoryLot sendLot(String publicId, long fromLocationId) {
        InventoryLot lot = repository.lockLotByPublicId(publicId).orElseThrow(() -> lotNotFound(publicId));
        if (!"IN_STOCK".equals(lot.status()) || lot.quantityRemaining() == 0) {
            throw new BusinessRuleException("Lot " + publicId + " is " + lot.status()
                    + ": only lots in stock can be shipped");
        }
        if (lot.locationId() != fromLocationId) {
            throw new BusinessRuleException("Lot " + publicId + " is not at location " + fromLocationId);
        }
        repository.updateLot(lot.id(), "IN_TRANSIT", lot.locationId());
        return repository.findLotById(lot.id()).orElseThrow();
    }

    /** A shipped unit arrives: it is in stock again, at its new location. */
    @Transactional
    public void receiveShippedUnit(long unitId, long toLocationId) {
        InventoryUnit unit = repository.findUnitById(unitId).orElseThrow();
        if (unit.status() != UnitStatus.IN_TRANSIT) {
            throw new BusinessRuleException("Unit " + unit.publicId() + " is not in transit");
        }
        repository.moveUnit(unitId, UnitStatus.IN_INVENTORY, toLocationId);
        repository.insertStatusChange(unitId, UnitStatus.IN_TRANSIT, UnitStatus.IN_INVENTORY);
    }

    /** A shipped lot arrives: it is in stock again, at its new location. */
    @Transactional
    public void receiveShippedLot(long lotId, long toLocationId) {
        InventoryLot lot = repository.findLotById(lotId).orElseThrow();
        if (!"IN_TRANSIT".equals(lot.status())) {
            throw new BusinessRuleException("Lot " + lot.publicId() + " is not in transit");
        }
        repository.updateLot(lotId, "IN_STOCK", toLocationId);
    }

    @Transactional(readOnly = true)
    public List<String> unitIdsOfPurchaseLine(long purchaseLineId) {
        return repository.findUnitIdsByPurchaseLine(purchaseLineId);
    }

    @Transactional(readOnly = true)
    public Optional<String> lotIdOfPurchaseLine(long purchaseLineId) {
        return repository.findLotIdByPurchaseLine(purchaseLineId);
    }

    /** Quantity in stock per product and location, units and lots together. */
    @Transactional(readOnly = true)
    public List<StockLevel> stockLevels() {
        return repository.findStockLevels();
    }

    /** Soft deletes a location. Only allowed when it no longer holds any items. */
    @Transactional
    public void deleteLocation(long id, String reason) {
        Location location = getLocation(id);
        long held = repository.countItemsHeldByLocation(id);
        if (held > 0) {
            throw new BusinessRuleException("Location '" + location.name() + "' still holds " + held
                    + " item(s): move, sell or delete them first");
        }
        trash.markDeleted(TrashType.LOCATION, id, reason);
    }

    @Transactional
    public Location restoreLocation(long id) {
        trash.requireDeleted(TrashType.LOCATION, id, "Location " + id);
        trash.restore(TrashType.LOCATION, id);
        return getLocation(id);
    }

    /** Soft deletes a unit. A sold unit belongs to a sale and can never be deleted. */
    @Transactional
    public void deleteUnit(String publicId, String reason) {
        InventoryUnit unit = repository.lockUnitByPublicId(publicId).orElseThrow(() -> unitNotFound(publicId));
        if (unit.status() == UnitStatus.SOLD || unit.status() == UnitStatus.CLOSED) {
            throw new BusinessRuleException("Unit " + publicId + " is sold and cannot be deleted");
        }
        if (unit.status() == UnitStatus.IN_TRANSIT) {
            throw new BusinessRuleException("Unit " + publicId + " is on its way in a shipment and cannot be deleted");
        }
        trash.markDeleted(TrashType.UNIT, unit.id(), reason);
    }

    @Transactional
    public InventoryUnit restoreUnit(String publicId) {
        StockRepository.StockRef ref = repository.findUnitRef(publicId)
                .orElseThrow(() -> new NotFoundException("Unit " + publicId + " not found"));
        requireRestorable("Unit " + publicId, ref);
        trash.restore(TrashType.UNIT, ref.id());
        return getUnit(publicId);
    }

    /** Soft deletes a lot. A lot that already sold some of its goods can never be deleted. */
    @Transactional
    public void deleteLot(String publicId, String reason) {
        InventoryLot lot = repository.lockLotByPublicId(publicId).orElseThrow(() -> lotNotFound(publicId));
        if (lot.quantityRemaining() < lot.quantityReceived()) {
            throw new BusinessRuleException("Lot " + publicId + " has sales and cannot be deleted");
        }
        if ("IN_TRANSIT".equals(lot.status())) {
            throw new BusinessRuleException("Lot " + publicId + " is on its way in a shipment and cannot be deleted");
        }
        trash.markDeleted(TrashType.LOT, lot.id(), reason);
    }

    @Transactional
    public InventoryLot restoreLot(String publicId) {
        StockRepository.StockRef ref = repository.findLotRef(publicId)
                .orElseThrow(() -> new NotFoundException("Lot " + publicId + " not found"));
        requireRestorable("Lot " + publicId, ref);
        trash.restore(TrashType.LOT, ref.id());
        return getLot(publicId);
    }

    private void requireRestorable(String what, StockRepository.StockRef ref) {
        if (!ref.deleted()) {
            throw new BusinessRuleException(what + " is not deleted");
        }
        if (trash.isDeleted(TrashType.PRODUCT, ref.productId())) {
            throw new BusinessRuleException(what + " belongs to a deleted product: restore product "
                    + ref.productId() + " first");
        }
        if (trash.isDeleted(TrashType.LOCATION, ref.locationId())) {
            throw new BusinessRuleException(what + " is in a deleted location: restore location "
                    + ref.locationId() + " first");
        }
    }

    private NotFoundException unitNotFound(String publicId) {
        return trash.notFoundByPublicId(TrashType.UNIT, publicId, "Unit " + publicId);
    }

    private NotFoundException lotNotFound(String publicId) {
        return trash.notFoundByPublicId(TrashType.LOT, publicId, "Lot " + publicId);
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

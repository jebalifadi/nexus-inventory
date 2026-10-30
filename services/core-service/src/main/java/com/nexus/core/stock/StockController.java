package com.nexus.core.stock;

import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for locations, units, lots and stock levels. */
@RestController
@RequestMapping("/api/v1")
class StockController {

    record MoneyJson(String amount, String currency) {

        static MoneyJson of(Money money) {
            return new MoneyJson(money.amount().toPlainString(), money.currency().getCurrencyCode());
        }

        Money toMoney() {
            if (amount == null || currency == null) {
                throw new BusinessRuleException("Amount and currency are required");
            }
            try {
                return Money.of(amount, currency);
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("Invalid amount or currency: " + amount + " " + currency);
            }
        }
    }

    record NewLocation(String name, String country) {
    }

    record ReceiveUnit(Long productId, Long locationId, MoneyJson unitCost, LocalDate receivedDate,
                       Map<String, String> attributes) {
    }

    record ReceiveLot(Long productId, Long locationId, Integer quantity, MoneyJson unitCost,
                      LocalDate receivedDate) {
    }

    record StatusChange(UnitStatus status) {
    }

    record UnitView(String id, Long productId, Long locationId, UnitStatus status, MoneyJson unitCost,
                    LocalDate receivedDate, Map<String, String> attributes) {

        static UnitView of(InventoryUnit unit) {
            return new UnitView(unit.publicId(), unit.productId(), unit.locationId(), unit.status(),
                    MoneyJson.of(unit.unitCost()), unit.receivedDate(), unit.attributes());
        }
    }

    record LotView(String id, Long productId, Long locationId, int quantityReceived, int quantityRemaining,
                   MoneyJson unitCost, LocalDate receivedDate, String status) {

        static LotView of(InventoryLot lot) {
            return new LotView(lot.publicId(), lot.productId(), lot.locationId(), lot.quantityReceived(),
                    lot.quantityRemaining(), MoneyJson.of(lot.unitCost()), lot.receivedDate(), lot.status());
        }
    }

    private final StockService stock;

    StockController(StockService stock) {
        this.stock = stock;
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    Location createLocation(@RequestBody NewLocation request) {
        return stock.createLocation(request.name(), request.country());
    }

    @GetMapping("/locations")
    List<Location> listLocations() {
        return stock.listLocations();
    }

    @DeleteMapping("/locations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLocation(@PathVariable("id") long id,
                        @RequestParam(name = "reason", required = false) String reason) {
        stock.deleteLocation(id, reason);
    }

    @PostMapping("/locations/{id}/restore")
    Location restoreLocation(@PathVariable("id") long id) {
        return stock.restoreLocation(id);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    UnitView receiveUnit(@RequestBody ReceiveUnit request) {
        requireIds(request.productId(), request.locationId());
        return UnitView.of(stock.receiveUnit(request.productId(), request.locationId(),
                toMoney(request.unitCost()), request.receivedDate(), request.attributes()));
    }

    @GetMapping("/units")
    List<UnitView> listUnits() {
        return stock.listUnits().stream().map(UnitView::of).toList();
    }

    @GetMapping("/units/{id}")
    UnitView getUnit(@PathVariable("id") String id) {
        return UnitView.of(stock.getUnit(id));
    }

    @PostMapping("/units/{id}/status")
    UnitView changeStatus(@PathVariable("id") String id, @RequestBody StatusChange request) {
        return UnitView.of(stock.changeUnitStatus(id, request.status()));
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteUnit(@PathVariable("id") String id,
                    @RequestParam(name = "reason", required = false) String reason) {
        stock.deleteUnit(id, reason);
    }

    @PostMapping("/units/{id}/restore")
    UnitView restoreUnit(@PathVariable("id") String id) {
        return UnitView.of(stock.restoreUnit(id));
    }

    @PostMapping("/lots")
    @ResponseStatus(HttpStatus.CREATED)
    LotView receiveLot(@RequestBody ReceiveLot request) {
        requireIds(request.productId(), request.locationId());
        if (request.quantity() == null) {
            throw new BusinessRuleException("quantity is required");
        }
        return LotView.of(stock.receiveLot(request.productId(), request.locationId(), request.quantity(),
                toMoney(request.unitCost()), request.receivedDate()));
    }

    @GetMapping("/lots")
    List<LotView> listLots() {
        return stock.listLots().stream().map(LotView::of).toList();
    }

    @GetMapping("/lots/{id}")
    LotView getLot(@PathVariable("id") String id) {
        return LotView.of(stock.getLot(id));
    }

    @DeleteMapping("/lots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLot(@PathVariable("id") String id,
                   @RequestParam(name = "reason", required = false) String reason) {
        stock.deleteLot(id, reason);
    }

    @PostMapping("/lots/{id}/restore")
    LotView restoreLot(@PathVariable("id") String id) {
        return LotView.of(stock.restoreLot(id));
    }

    @GetMapping("/stock")
    List<StockLevel> stockLevels() {
        return stock.stockLevels();
    }

    private static void requireIds(Long productId, Long locationId) {
        if (productId == null || locationId == null) {
            throw new BusinessRuleException("productId and locationId are required");
        }
    }

    private static Money toMoney(MoneyJson json) {
        if (json == null) {
            throw new BusinessRuleException("unitCost is required");
        }
        return json.toMoney();
    }
}

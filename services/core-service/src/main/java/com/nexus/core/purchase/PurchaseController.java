package com.nexus.core.purchase;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.web.MoneyJson;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for suppliers and purchases. */
@RestController
@RequestMapping("/api/v1")
class PurchaseController {

    record NewSupplier(String name, String country, String contact, String notes) {
    }

    record NewPurchase(Long supplierId, LocalDate purchaseDate, String currency, String notes,
                       List<NewPurchaseLine> lines) {
    }

    record LineView(int lineNo, Long productId, Long locationId, int quantity, MoneyJson unitPrice,
                    MoneyJson lineTotal, List<String> unitIds, String lotId) {

        static LineView of(PurchaseLine line) {
            return new LineView(line.lineNo(), line.productId(), line.locationId(), line.quantity(),
                    MoneyJson.of(line.unitPrice()), MoneyJson.of(line.lineTotal()), line.unitIds(), line.lotId());
        }
    }

    record PurchaseView(String id, Long supplierId, String supplierName, LocalDate purchaseDate, String currency,
                        String notes, List<LineView> lines, MoneyJson total) {

        static PurchaseView of(Purchase purchase) {
            return new PurchaseView(purchase.publicId(), purchase.supplierId(), purchase.supplierName(),
                    purchase.purchaseDate(), purchase.currency(), purchase.notes(),
                    purchase.lines().stream().map(LineView::of).toList(), MoneyJson.of(purchase.total()));
        }
    }

    private final PurchaseService purchases;

    PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    Supplier createSupplier(@RequestBody NewSupplier request) {
        return purchases.createSupplier(request.name(), request.country(), request.contact(), request.notes());
    }

    @GetMapping("/suppliers")
    List<Supplier> listSuppliers() {
        return purchases.listSuppliers();
    }

    @GetMapping("/suppliers/{id}")
    Supplier getSupplier(@PathVariable("id") long id) {
        return purchases.getSupplier(id);
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseView createPurchase(@RequestBody NewPurchase request) {
        if (request.supplierId() == null) {
            throw new BusinessRuleException("supplierId is required");
        }
        return PurchaseView.of(purchases.createPurchase(request.supplierId(), request.purchaseDate(),
                request.currency(), request.notes(), request.lines()));
    }

    @GetMapping("/purchases")
    List<PurchaseView> listPurchases() {
        return purchases.listPurchases().stream().map(PurchaseView::of).toList();
    }

    @GetMapping("/purchases/{id}")
    PurchaseView getPurchase(@PathVariable("id") String id) {
        return PurchaseView.of(purchases.getPurchase(id));
    }
}

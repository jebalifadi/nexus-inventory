package com.nexus.core.warranty;

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

/** REST API for warranty claims. */
@RestController
@RequestMapping("/api/v1/warranty-claims")
class WarrantyController {

    record NewClaim(String saleId, Integer lineNo, Integer quantity, LocalDate claimDate, String issue,
                    String goodwillReason) {
    }

    record Resolution(ClaimResolution resolution, MoneyJson cost, LocalDate resolvedDate, String notes) {
    }

    record Rejection(LocalDate resolvedDate, String notes) {
    }

    record ClaimView(String id, String saleId, int lineNo, String unitId, int quantity, LocalDate claimDate,
                     String issue, boolean withinWarranty, LocalDate warrantyEndDate, String goodwillReason,
                     ClaimStatus status, ClaimResolution resolution, MoneyJson cost, MoneyJson refund,
                     LocalDate resolvedDate, String notes) {

        static ClaimView of(WarrantyClaim c) {
            return new ClaimView(c.publicId(), c.saleId(), c.lineNo(), c.unitId(), c.quantity(), c.claimDate(),
                    c.issue(), c.withinWarranty(), c.warrantyEndDate(), c.goodwillReason(), c.status(),
                    c.resolution(), MoneyJson.of(c.cost()), MoneyJson.of(c.refund()), c.resolvedDate(), c.notes());
        }
    }

    private final WarrantyService warranty;

    WarrantyController(WarrantyService warranty) {
        this.warranty = warranty;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClaimView open(@RequestBody NewClaim request) {
        if (request.saleId() == null || request.lineNo() == null) {
            throw new BusinessRuleException("saleId and lineNo are required");
        }
        return ClaimView.of(warranty.openClaim(request.saleId(), request.lineNo(), request.quantity(),
                request.claimDate(), request.issue(), request.goodwillReason()));
    }

    @GetMapping
    List<ClaimView> list() {
        return warranty.listClaims().stream().map(ClaimView::of).toList();
    }

    @GetMapping("/{id}")
    ClaimView get(@PathVariable("id") String id) {
        return ClaimView.of(warranty.getClaim(id));
    }

    @PostMapping("/{id}/resolve")
    ClaimView resolve(@PathVariable("id") String id, @RequestBody Resolution request) {
        return ClaimView.of(warranty.resolve(id, request.resolution(),
                request.cost() == null ? null : request.cost().toMoney(), request.resolvedDate(), request.notes()));
    }

    @PostMapping("/{id}/reject")
    ClaimView reject(@PathVariable("id") String id, @RequestBody(required = false) Rejection request) {
        return ClaimView.of(warranty.reject(id, request == null ? null : request.resolvedDate(),
                request == null ? null : request.notes()));
    }
}

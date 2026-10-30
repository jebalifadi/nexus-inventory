package com.nexus.core.sale;

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

/** REST API for customers and sales. */
@RestController
@RequestMapping("/api/v1")
class SaleController {

    record NewCustomer(String name, String phone, String email, String country, String notes) {
    }

    record NewSale(Long customerId, Long locationId, LocalDate saleDate, String currency, String notes,
                   List<NewSaleLine> lines) {
    }

    record AllocationView(String lotId, int quantity, MoneyJson unitCost) {

        static AllocationView of(SaleAllocation a) {
            return new AllocationView(a.lotId(), a.quantity(), MoneyJson.of(a.unitCost()));
        }
    }

    record LineView(int lineNo, Long productId, String unitId, int quantity, MoneyJson unitPrice, MoneyJson lineTotal,
                    MoneyJson cost, MoneyJson profit, List<AllocationView> allocations, int warrantyDays,
                    LocalDate warrantyEndDate, String warrantyTerms) {

        static LineView of(SaleLine l) {
            return new LineView(l.lineNo(), l.productId(), l.unitId(), l.quantity(), MoneyJson.of(l.unitPrice()),
                    MoneyJson.of(l.lineTotal()), MoneyJson.of(l.cost()), MoneyJson.of(l.profit()),
                    l.allocations().stream().map(AllocationView::of).toList(), l.warrantyDays(),
                    l.warrantyEndDate(), l.warrantyTerms());
        }
    }

    record SaleView(String id, Long customerId, String customerName, Long locationId, LocalDate saleDate,
                    String currency, String notes, List<LineView> lines, MoneyJson total, MoneyJson profit) {

        static SaleView of(Sale s) {
            return new SaleView(s.publicId(), s.customerId(), s.customerName(), s.locationId(), s.saleDate(),
                    s.currency(), s.notes(), s.lines().stream().map(LineView::of).toList(), MoneyJson.of(s.total()),
                    MoneyJson.of(s.profit()));
        }
    }

    private final SaleService sales;

    SaleController(SaleService sales) {
        this.sales = sales;
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    Customer createCustomer(@RequestBody NewCustomer request) {
        return sales.createCustomer(request.name(), request.phone(), request.email(), request.country(),
                request.notes());
    }

    @GetMapping("/customers")
    List<Customer> listCustomers() {
        return sales.listCustomers();
    }

    @GetMapping("/customers/{id}")
    Customer getCustomer(@PathVariable("id") long id) {
        return sales.getCustomer(id);
    }

    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    SaleView createSale(@RequestBody NewSale request) {
        if (request.locationId() == null) {
            throw new BusinessRuleException("locationId is required");
        }
        return SaleView.of(sales.createSale(request.customerId(), request.locationId(), request.saleDate(),
                request.currency(), request.notes(), request.lines()));
    }

    @GetMapping("/sales")
    List<SaleView> listSales() {
        return sales.listSales().stream().map(SaleView::of).toList();
    }

    @GetMapping("/sales/{id}")
    SaleView getSale(@PathVariable("id") String id) {
        return SaleView.of(sales.getSale(id));
    }
}

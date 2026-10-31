package com.nexus.core.warranty;

import com.nexus.core.expense.ExpenseCategory;
import com.nexus.core.expense.ExpenseService;
import com.nexus.core.money.Money;
import com.nexus.core.payment.PaymentService;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleLine;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of warranty claims.
 *
 * <p>A claim inside the warranty period is accepted; after it, only as goodwill with a reason.
 * Every claim cost becomes a WARRANTY expense on the sale, so the sale's profit shows what the
 * warranty really cost. A refund takes the goods back into stock and gives the money back as a
 * refund payment. The original sale always stays in history.
 */
@Service
public class WarrantyService {

    private final WarrantyClaimRepository repository;
    private final SaleService sales;
    private final ExpenseService expenses;
    private final PaymentService payments;

    WarrantyService(WarrantyClaimRepository repository, SaleService sales, ExpenseService expenses,
                    PaymentService payments) {
        this.repository = repository;
        this.sales = sales;
        this.expenses = expenses;
        this.payments = payments;
    }

    @Transactional
    public WarrantyClaim openClaim(String salePublicId, int lineNo, Integer quantity, LocalDate claimDate,
                                   String issue, String goodwillReason) {
        Sale sale = sales.getSale(salePublicId);
        SaleLine line = sale.lines().stream()
                .filter(l -> l.lineNo() == lineNo)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Sale " + salePublicId + " has no line " + lineNo));
        if (issue == null || issue.isBlank()) {
            throw new BusinessRuleException("Describe the issue");
        }
        int pieces = quantity == null ? 1 : quantity;
        int stillSold = line.quantity() - line.returnedQuantity();
        if (pieces < 1 || pieces > stillSold) {
            throw new BusinessRuleException("A claim can cover 1 to " + stillSold + " piece(s) of line " + lineNo);
        }
        LocalDate date = claimDate == null ? LocalDate.now() : claimDate;
        if (date.isBefore(sale.saleDate())) {
            throw new BusinessRuleException("A claim cannot come before the sale (" + sale.saleDate() + ")");
        }
        boolean within = line.warrantyEndDate() != null && !date.isAfter(line.warrantyEndDate());
        String goodwill = goodwillReason == null || goodwillReason.isBlank() ? null : goodwillReason.strip();
        if (!within && goodwill == null) {
            throw new BusinessRuleException((line.warrantyEndDate() == null
                    ? "Line " + lineNo + " was sold without warranty"
                    : "The warranty of line " + lineNo + " ended on " + line.warrantyEndDate())
                    + ": give a goodwill reason to accept the claim anyway");
        }
        long id = repository.insert(line.id(), pieces, date, issue.strip(), within, goodwill);
        return toClaim(repository.findById(id).orElseThrow());
    }

    /**
     * Closes a claim. {@code cost} (optional) is what the business spent on it, for example the repair.
     * For REFUNDED, the goods come back into stock and the customer gets back what they paid for them.
     */
    @Transactional
    public WarrantyClaim resolve(String claimPublicId, ClaimResolution resolution, Money cost, LocalDate resolvedDate,
                                 String notes) {
        if (resolution == null || resolution == ClaimResolution.REJECTED) {
            throw new BusinessRuleException("Choose REPAIRED, REPLACED or REFUNDED (or reject the claim)");
        }
        WarrantyClaimRepository.ClaimRow claim = lockOpen(claimPublicId);
        LocalDate date = resolvedDate == null ? LocalDate.now() : resolvedDate;
        if (cost != null && cost.isNegative()) {
            throw new BusinessRuleException("Cost cannot be negative");
        }
        if (cost != null && cost.amount().signum() > 0) {
            expenses.addExpense(ExpenseCategory.WARRANTY, cost, date, "Warranty claim " + claimPublicId,
                    null, claim.salePublicId());
        }

        Money refund = null;
        if (resolution == ClaimResolution.REFUNDED) {
            Money paid = sales.getSale(claim.salePublicId()).paid();
            Money returnedValue = sales.returnGoods(claim.salePublicId(), claim.lineNo(), claim.quantity());
            refund = returnedValue.amount().compareTo(paid.amount()) <= 0 ? returnedValue : paid;
            if (refund.amount().signum() > 0) {
                payments.recordRefund(claim.salePublicId(), refund, date, "Refund for claim " + claimPublicId);
            }
        }
        repository.close(claim.id(), ClaimStatus.RESOLVED, resolution, cost == null ? null : cost.amount(),
                cost == null ? null : cost.currency().getCurrencyCode(), refund == null ? null : refund.amount(),
                date, blank(notes));
        return toClaim(repository.findById(claim.id()).orElseThrow());
    }

    @Transactional
    public WarrantyClaim reject(String claimPublicId, LocalDate resolvedDate, String notes) {
        WarrantyClaimRepository.ClaimRow claim = lockOpen(claimPublicId);
        repository.close(claim.id(), ClaimStatus.REJECTED, ClaimResolution.REJECTED, null, null, null,
                resolvedDate == null ? LocalDate.now() : resolvedDate, blank(notes));
        return toClaim(repository.findById(claim.id()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public WarrantyClaim getClaim(String publicId) {
        return repository.findByPublicId(publicId)
                .map(WarrantyService::toClaim)
                .orElseThrow(() -> new NotFoundException("Claim " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<WarrantyClaim> listClaims() {
        return repository.findAll().stream().map(WarrantyService::toClaim).toList();
    }

    private WarrantyClaimRepository.ClaimRow lockOpen(String publicId) {
        long id = repository.lock(publicId).orElseThrow(() -> new NotFoundException("Claim " + publicId + " not found"));
        WarrantyClaimRepository.ClaimRow claim = repository.findById(id).orElseThrow();
        if (claim.status() != ClaimStatus.OPEN) {
            throw new BusinessRuleException("Claim " + publicId + " is already " + claim.status());
        }
        return claim;
    }

    private static WarrantyClaim toClaim(WarrantyClaimRepository.ClaimRow row) {
        LocalDate warrantyEnd = row.warrantyDays() == 0 ? null : row.saleDate().plusDays(row.warrantyDays());
        Money cost = row.costAmount() == null ? null : Money.of(row.costAmount().toPlainString(), row.costCurrency());
        Money refund = row.refundAmount() == null ? null
                : Money.of(row.refundAmount().toPlainString(), row.saleCurrency());
        return new WarrantyClaim(row.id(), row.publicId(), row.salePublicId(), row.lineNo(), row.unitPublicId(),
                row.quantity(), row.claimDate(), row.issue(), row.withinWarranty(), warrantyEnd, row.goodwillReason(),
                row.status(), row.resolution(), cost, refund, row.resolvedDate(), row.notes());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

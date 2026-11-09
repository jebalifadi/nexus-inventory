package com.nexus.core.report;

import com.nexus.core.money.Money;
import java.util.List;

/**
 * What sold in a period, per product and per category. Profit here is what each line earned, before
 * general costs and costs of the sale, so it adds up to more than the dashboard's profit.
 *
 * @param products every product sold, most pieces first
 * @param categories every category sold, highest profit first
 */
public record SalesAnalytics(String currency, List<ProductSales> products, List<CategorySales> categories,
                             List<MissingRate> missingRates) {

    public SalesAnalytics {
        products = List.copyOf(products);
        categories = List.copyOf(categories);
        missingRates = List.copyOf(missingRates);
    }

    /**
     * @param quantity      pieces sold, minus pieces returned
     * @param unknownProfit whether some of its sales are left out of the profit for lack of a rate
     */
    public record ProductSales(long productId, String sku, String name, String categoryName, int quantity,
                               Money revenue, Money profit, boolean unknownProfit) {
    }

    public record CategorySales(String categoryName, int quantity, Money revenue, Money profit,
                                boolean unknownProfit) {
    }
}

package com.nexus.core.stock;

import com.nexus.core.catalog.ProductStock;
import org.springframework.stereotype.Component;

/** Answers the catalog's question "is this product still in stock?" from the stock tables. */
@Component
class StockProductStock implements ProductStock {

    private final StockRepository repository;

    StockProductStock(StockRepository repository) {
        this.repository = repository;
    }

    @Override
    public long itemsHeld(long productId) {
        return repository.countItemsHeldByProduct(productId);
    }
}

package com.nexus.core.sale;

import com.nexus.core.money.Money;
import java.util.List;

/**
 * Extra costs that lower a sale's profit: expenses on a sold unit (a repair) and on the sale
 * itself (platform fees). The expense module implements it, so sales do not depend on it.
 */
public interface ExtraCosts {

    List<Money> forUnit(long unitId);

    List<Money> forSale(long saleId);
}

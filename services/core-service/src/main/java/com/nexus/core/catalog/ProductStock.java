package com.nexus.core.catalog;

/**
 * Tells the catalog how many items of a product are still held (in stock or on the way).
 * The stock module implements it, so the catalog does not depend on stock code.
 */
public interface ProductStock {

    long itemsHeld(long productId);
}

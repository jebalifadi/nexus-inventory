package com.nexus.core.trash;

/** The kinds of records that can be soft deleted, with the table that stores them. */
public enum TrashType {
    CATEGORY("categories", "name"),
    PRODUCT("products", "name"),
    LOCATION("locations", "name"),
    UNIT("inventory_units", "public_id"),
    LOT("inventory_lots", "public_id");

    final String table;
    final String nameColumn;

    TrashType(String table, String nameColumn) {
        this.table = table;
        this.nameColumn = nameColumn;
    }
}

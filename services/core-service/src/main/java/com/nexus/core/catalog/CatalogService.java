package com.nexus.core.catalog;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.trash.TrashRepository;
import com.nexus.core.trash.TrashType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the catalog: owner-defined categories and the products in them. */
@Service
public class CatalogService {

    private final CatalogRepository repository;
    private final ProductStock productStock;
    private final TrashRepository trash;

    CatalogService(CatalogRepository repository, ProductStock productStock, TrashRepository trash) {
        this.repository = repository;
        this.productStock = productStock;
        this.trash = trash;
    }

    @Transactional
    public Category createCategory(String name, TrackingMode trackingMode, List<FieldDefinition> fields) {
        List<CategoryField> validated = CategoryRules.validate(name, trackingMode, fields);
        long categoryId = repository.insertCategory(CategoryRules.validateName(name), trackingMode);
        int position = 0;
        for (CategoryField field : validated) {
            long fieldId = repository.insertField(categoryId, field, position++);
            int choicePosition = 0;
            for (String choice : field.choices()) {
                repository.insertChoice(fieldId, choice, choicePosition++);
            }
        }
        return getCategory(categoryId);
    }

    @Transactional(readOnly = true)
    public Category getCategory(long id) {
        return repository.findCategory(id)
                .orElseThrow(() -> trash.notFound(TrashType.CATEGORY, id, "Category " + id));
    }

    /**
     * Renames a category. Its fields and tracking mode stay as they are: existing products and stock
     * depend on them.
     */
    @Transactional
    public Category renameCategory(long id, String name) {
        return updateCategory(id, name, null);
    }

    /**
     * Renames a category and, when {@code nameFields} is given, chooses which product fields form the
     * product names, in order; the names of its products are rebuilt. Fields and tracking mode stay.
     */
    @Transactional
    public Category updateCategory(long id, String name, List<String> nameFields) {
        Category category = getCategory(id);
        repository.updateCategoryName(id, CategoryRules.validateName(name));
        if (nameFields != null) {
            repository.updateNameFields(id, CategoryRules.validateNameFields(category, nameFields));
            Category updated = getCategory(id);
            for (long productId : repository.findProductIdsByCategory(id)) {
                String productName = ProductRules.buildName(updated, getProduct(productId).attributes());
                repository.updateProductName(productId,
                        productName.isEmpty() ? getProduct(productId).sku() : productName);
            }
        }
        return getCategory(id);
    }

    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return repository.findAllCategoryIds().stream().map(this::getCategory).toList();
    }

    @Transactional
    public Product createProduct(long categoryId, String sku, Map<String, String> attributes) {
        Category category = getCategory(categoryId);
        String validSku = ProductRules.validateSku(sku);
        Map<String, String> values = ProductRules.validateAttributes(category, attributes);
        String name = ProductRules.buildName(category, values);
        long productId = repository.insertProduct(categoryId, validSku, name.isEmpty() ? validSku : name);
        values.forEach((key, value) ->
                repository.insertAttribute(productId, category.field(key).orElseThrow().id(), value));
        return getProduct(productId);
    }

    /**
     * Changes a product's SKU and product-level values, checked like a new product; the name is
     * rebuilt from the new values. The category cannot change. Purchases, sales and stock refer to
     * the product by id, so they show the new SKU and name.
     */
    @Transactional
    public Product updateProduct(long id, String sku, Map<String, String> attributes) {
        Product product = getProduct(id);
        Category category = getCategory(product.categoryId());
        String validSku = ProductRules.validateSku(sku);
        Map<String, String> values = ProductRules.validateAttributes(category, attributes);
        String name = ProductRules.buildName(category, values);
        repository.updateProduct(id, validSku, name.isEmpty() ? validSku : name);
        repository.deleteAttributes(id);
        values.forEach((key, value) ->
                repository.insertAttribute(id, category.field(key).orElseThrow().id(), value));
        return getProduct(id);
    }

    @Transactional(readOnly = true)
    public Product getProduct(long id) {
        return repository.findProduct(id)
                .orElseThrow(() -> trash.notFound(TrashType.PRODUCT, id, "Product " + id));
    }

    /** Validates the values of one physical item of a product's category. */
    public Map<String, String> validateItemAttributes(Category category, Map<String, String> values) {
        return ProductRules.validateItemAttributes(category, values);
    }

    @Transactional(readOnly = true)
    public List<Product> listProducts(Long categoryId) {
        List<Long> ids = categoryId == null
                ? repository.findProductIds()
                : repository.findProductIdsByCategory(categoryId);
        return ids.stream().map(this::getProduct).toList();
    }

    /** Soft deletes a category. Only allowed when it has no products left. */
    @Transactional
    public void deleteCategory(long id, String reason) {
        Category category = getCategory(id);
        long products = repository.countActiveProducts(id);
        if (products > 0) {
            throw new BusinessRuleException("Category '" + category.name() + "' still has " + products
                    + " product(s): delete them first");
        }
        trash.markDeleted(TrashType.CATEGORY, id, reason);
    }

    @Transactional
    public Category restoreCategory(long id) {
        trash.requireDeleted(TrashType.CATEGORY, id, "Category " + id);
        trash.restore(TrashType.CATEGORY, id);
        return getCategory(id);
    }

    /** Soft deletes a product. Only allowed when none of its items are still held; sold items stay. */
    @Transactional
    public void deleteProduct(long id, String reason) {
        Product product = getProduct(id);
        long held = productStock.itemsHeld(id);
        if (held > 0) {
            throw new BusinessRuleException("Product " + product.sku() + " still has " + held
                    + " item(s) in stock or on the way: sell or delete them first");
        }
        trash.markDeleted(TrashType.PRODUCT, id, reason);
    }

    @Transactional
    public Product restoreProduct(long id) {
        trash.requireDeleted(TrashType.PRODUCT, id, "Product " + id);
        long categoryId = repository.findCategoryIdOfProduct(id).orElseThrow();
        if (trash.isDeleted(TrashType.CATEGORY, categoryId)) {
            throw new BusinessRuleException("Product " + id + " belongs to a deleted category: restore category "
                    + categoryId + " first");
        }
        trash.restore(TrashType.PRODUCT, id);
        return getProduct(id);
    }
}

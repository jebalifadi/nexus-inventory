package com.nexus.core.catalog;

import com.nexus.core.shared.NotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the catalog: owner-defined categories and the products in them. */
@Service
public class CatalogService {

    private final CatalogRepository repository;

    CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Category createCategory(String name, TrackingMode trackingMode, List<FieldDefinition> fields) {
        List<CategoryField> validated = CategoryRules.validate(name, trackingMode, fields);
        long categoryId = repository.insertCategory(name.trim(), trackingMode);
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
                .orElseThrow(() -> new NotFoundException("Category " + id + " not found"));
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

    @Transactional(readOnly = true)
    public Product getProduct(long id) {
        return repository.findProduct(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
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
}

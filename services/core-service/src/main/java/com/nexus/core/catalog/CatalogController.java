package com.nexus.core.catalog;

import com.nexus.core.shared.BusinessRuleException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for categories and products. */
@RestController
@RequestMapping("/api/v1")
class CatalogController {

    /** {@code required} and {@code inName} may be left out: missing means no. */
    record NewField(String label, FieldType type, FieldScope scope, Boolean required, List<String> choices,
                    Boolean inName) {
    }

    record NewCategory(String name, TrackingMode trackingMode, List<NewField> fields) {
    }

    record NewProduct(Long categoryId, String sku, Map<String, String> attributes) {
    }

    /** {@code nameFields}: field keys forming the product name, in order; leave it out to keep them. */
    record CategoryChange(String name, List<String> nameFields) {
    }

    record ProductChange(String sku, Map<String, String> attributes) {
    }

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    Category createCategory(@RequestBody NewCategory request) {
        List<FieldDefinition> fields = request.fields() == null ? List.of() : request.fields().stream()
                .map(f -> new FieldDefinition(f.label(), f.type(), f.scope(), Boolean.TRUE.equals(f.required()),
                        f.choices(), Boolean.TRUE.equals(f.inName())))
                .toList();
        return catalog.createCategory(request.name(), request.trackingMode(), fields);
    }

    @GetMapping("/categories")
    List<Category> listCategories() {
        return catalog.listCategories();
    }

    @GetMapping("/categories/{id}")
    Category getCategory(@PathVariable("id") long id) {
        return catalog.getCategory(id);
    }

    /** Renames a category and optionally changes its name fields; fields and tracking mode cannot change. */
    @PutMapping("/categories/{id}")
    Category updateCategory(@PathVariable("id") long id, @RequestBody CategoryChange request) {
        return catalog.updateCategory(id, request.name(), request.nameFields());
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable("id") long id,
                        @RequestParam(name = "reason", required = false) String reason) {
        catalog.deleteCategory(id, reason);
    }

    @PostMapping("/categories/{id}/restore")
    Category restoreCategory(@PathVariable("id") long id) {
        return catalog.restoreCategory(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    Product createProduct(@RequestBody NewProduct request) {
        if (request.categoryId() == null) {
            throw new BusinessRuleException("categoryId is required");
        }
        return catalog.createProduct(request.categoryId(), request.sku(), request.attributes());
    }

    @GetMapping("/products")
    List<Product> listProducts(@RequestParam(name = "categoryId", required = false) Long categoryId) {
        return catalog.listProducts(categoryId);
    }

    @GetMapping("/products/{id}")
    Product getProduct(@PathVariable("id") long id) {
        return catalog.getProduct(id);
    }

    /** Changes a product's SKU and product fields; its category cannot change. */
    @PutMapping("/products/{id}")
    Product updateProduct(@PathVariable("id") long id, @RequestBody ProductChange request) {
        return catalog.updateProduct(id, request.sku(), request.attributes());
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteProduct(@PathVariable("id") long id,
                       @RequestParam(name = "reason", required = false) String reason) {
        catalog.deleteProduct(id, reason);
    }

    @PostMapping("/products/{id}/restore")
    Product restoreProduct(@PathVariable("id") long id) {
        return catalog.restoreProduct(id);
    }
}

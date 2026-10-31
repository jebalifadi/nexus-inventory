package com.nexus.core.catalog;

import com.nexus.core.shared.BusinessRuleException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Validates a new category and turns the owner's field definitions into category fields. */
final class CategoryRules {

    private CategoryRules() {
    }

    private static final int MAX_NAME_LENGTH = 100;

    static List<CategoryField> validate(String name, TrackingMode trackingMode, List<FieldDefinition> definitions) {
        validateName(name);
        if (trackingMode == null) {
            throw new BusinessRuleException("Tracking mode is required (UNIT or LOT)");
        }
        List<CategoryField> fields = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (FieldDefinition definition : definitions == null ? List.<FieldDefinition>of() : definitions) {
            CategoryField field = toField(definition);
            if (!keys.add(field.key())) {
                throw new BusinessRuleException("Two fields would have the same key '" + field.key() + "'");
            }
            fields.add(field);
        }
        return fields;
    }

    /** Checks a category name and returns it trimmed. */
    static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Category name is required");
        }
        String value = name.trim();
        if (value.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleException("Category name is too long (max " + MAX_NAME_LENGTH + " characters)");
        }
        return value;
    }

    private static CategoryField toField(FieldDefinition definition) {
        if (definition.label() == null || definition.label().isBlank()) {
            throw new BusinessRuleException("Every field needs a label");
        }
        String label = definition.label().trim();
        if (definition.type() == null || definition.scope() == null) {
            throw new BusinessRuleException("Field '" + label + "' needs a type and a scope");
        }
        List<String> choices = cleanChoices(label, definition.choices());
        if (definition.type() == FieldType.CHOICE && choices.isEmpty()) {
            throw new BusinessRuleException("Choice field '" + label + "' needs at least one choice");
        }
        if (definition.type() != FieldType.CHOICE && !choices.isEmpty()) {
            throw new BusinessRuleException("Only choice fields can have choices: '" + label + "'");
        }
        return new CategoryField(null, FieldKeys.fromLabel(label), label, definition.type(),
                definition.scope(), definition.required(), choices);
    }

    private static List<String> cleanChoices(String label, List<String> choices) {
        if (choices == null) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String choice : choices) {
            if (choice == null || choice.isBlank()) {
                throw new BusinessRuleException("Field '" + label + "' has an empty choice");
            }
            String value = choice.trim();
            if (cleaned.contains(value)) {
                throw new BusinessRuleException("Field '" + label + "' lists the choice '" + value + "' twice");
            }
            cleaned.add(value);
        }
        return cleaned;
    }
}

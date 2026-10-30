package com.nexus.core.catalog;

import com.nexus.core.shared.BusinessRuleException;
import java.text.Normalizer;
import java.util.Locale;

/** Derives stable internal keys from the labels owners type, for example "Akku %" becomes "akku". */
final class FieldKeys {

    private FieldKeys() {
    }

    static String fromLabel(String label) {
        String key = label.trim().toLowerCase(Locale.ROOT).replace("ß", "ss");
        key = Normalizer.normalize(key, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        key = key.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (key.isEmpty()) {
            throw new BusinessRuleException("Field label must contain letters or digits: '" + label + "'");
        }
        return key.length() > 64 ? key.substring(0, 64) : key;
    }
}

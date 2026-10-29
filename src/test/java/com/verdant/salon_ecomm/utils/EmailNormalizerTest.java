package com.verdant.salon_ecomm.utils;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void trimsAndLowerCases() {
        assertThat(EmailNormalizer.normalize("  Jane.Doe@Example.COM \t")).isEqualTo("jane.doe@example.com");
    }

    @Test
    void nullStaysNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }

    @Test
    void alreadyNormalizedIsUnchanged() {
        assertThat(EmailNormalizer.normalize("jane@x.com")).isEqualTo("jane@x.com");
    }

    // Locale.ROOT: under a Turkish default locale, String.toLowerCase() would turn "I" into a dotless "ı".
    @Test
    void ignoresTheServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(EmailNormalizer.normalize("IVAN@X.COM")).isEqualTo("ivan@x.com");
        } finally {
            Locale.setDefault(original);
        }
    }
}

package com.verdant.salon_ecomm.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingTest {

    @Test
    void emailKeepsFirstLetterAndDomain() {
        assertThat(PiiMasking.maskEmail("jane.doe@gmail.com")).isEqualTo("j***@gmail.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingEmailIsNone(String email) {
        assertThat(PiiMasking.maskEmail(email)).isEqualTo("none");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-at-sign", "@domain-only.com"})
    void malformedEmailRevealsNothing(String email) {
        assertThat(PiiMasking.maskEmail(email)).isEqualTo("***");
    }

    @Test
    void phoneKeepsLastFourDigitsOnly() {
        assertThat(PiiMasking.maskPhone("+1 (555) 123-4567")).isEqualTo("***4567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingPhoneIsNone(String phone) {
        assertThat(PiiMasking.maskPhone(phone)).isEqualTo("none");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "12-3", "abc"})
    void shortPhoneRevealsNothing(String phone) {
        assertThat(PiiMasking.maskPhone(phone)).isEqualTo("***");
    }
}

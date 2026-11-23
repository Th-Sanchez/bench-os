package com.benchos.api.customer.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    @Test
    void normalizesFormattedPhoneNumbersToDigitsOnly() {
        assertThat(Customer.normalizePhone("(11) 99999-0001")).isEqualTo("11999990001");
        assertThat(Customer.normalizePhone("+55 (11) 3333-4444")).isEqualTo("551133334444");
        assertThat(Customer.normalizePhone("  11 98765 4321 ")).isEqualTo("11987654321");
    }

    @Test
    void rejectsPhoneNumbersOutsideAllowedDigitRange() {
        assertThatThrownBy(() -> Customer.normalizePhone("---"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Customer.normalizePhone("1234567"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Customer.normalizePhone("123456789012345678901"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void normalizesOptionalFieldsAndSupportsSoftDelete() {
        Customer customer = new Customer(UUID.randomUUID(), "  Maria Silva  ", "11999990001", "   ", "  VIP  ");

        assertThat(customer.getName()).isEqualTo("Maria Silva");
        assertThat(customer.getDocument()).isNull();
        assertThat(customer.getNotes()).isEqualTo("VIP");
        assertThat(customer.isDeleted()).isFalse();

        customer.softDelete();
        assertThat(customer.isDeleted()).isTrue();
        assertThat(customer.getDeletedAt()).isNotNull();
    }
}

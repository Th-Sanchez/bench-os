package com.benchos.api.shared.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    @Test
    void generatesValidVersion7UuidsInMonotonicOrder() {
        UUID first = UuidV7Generator.next();
        UUID second = UuidV7Generator.next();

        assertThat(first.version()).isEqualTo(7);
        assertThat(second.version()).isEqualTo(7);
        assertThat(first).isNotEqualTo(second);
        assertThat(second.toString()).isGreaterThan(first.toString());
    }
}

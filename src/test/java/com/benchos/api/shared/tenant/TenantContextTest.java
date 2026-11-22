package com.benchos.api.shared.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storesAndClearsTenantIdInCurrentThread() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.setCurrentTenant(tenantId);

        assertThat(TenantContext.getCurrentTenant()).contains(tenantId);
        assertThat(TenantContext.requireCurrentTenant()).isEqualTo(tenantId);

        TenantContext.clear();

        assertThat(TenantContext.getCurrentTenant()).isEmpty();
        assertThatThrownBy(TenantContext::requireCurrentTenant)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> TenantContext.setCurrentTenant(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

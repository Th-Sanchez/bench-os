package com.benchos.api.order.internal.entity;

import com.benchos.api.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemPhotoTest {

    @Test
    void validatesSupportedImageContentTypes() {
        assertThat(ItemPhoto.validateAndNormalizeContentType("IMAGE/JPEG")).isEqualTo("image/jpeg");
        assertThat(ItemPhoto.validateAndNormalizeContentType("  image/png ")).isEqualTo("image/png");
        assertThat(ItemPhoto.validateAndNormalizeContentType("image/webp")).isEqualTo("image/webp");

        assertThatThrownBy(() -> ItemPhoto.validateAndNormalizeContentType("application/pdf"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ItemPhoto.validateAndNormalizeContentType("text/html"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void generatesAndValidatesTenantScopedStorageKeys() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        String storageKey = ItemPhoto.generateStorageKey(tenantId, orderId, itemId, "image/jpeg");
        assertThat(storageKey)
                .startsWith("tenants/" + tenantId + "/orders/" + orderId + "/items/" + itemId + "/")
                .endsWith(".jpg");

        assertThatCode(() -> ItemPhoto.validateStorageKeyForItem(tenantId, orderId, itemId, storageKey, "image/jpeg"))
                .doesNotThrowAnyException();

        // Cross-tenant key rejected
        assertThatThrownBy(() -> ItemPhoto.validateStorageKeyForItem(otherTenantId, orderId, itemId, storageKey, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class);

        // Extension mismatch rejected
        assertThatThrownBy(() -> ItemPhoto.validateStorageKeyForItem(tenantId, orderId, itemId, storageKey, "image/png"))
                .isInstanceOf(BusinessRuleException.class);

        // Path traversal rejected
        String traversalKey = "tenants/" + tenantId + "/orders/" + orderId + "/items/" + itemId + "/../secret.jpg";
        assertThatThrownBy(() -> ItemPhoto.validateStorageKeyForItem(tenantId, orderId, itemId, traversalKey, "image/jpeg"))
                .isInstanceOf(BusinessRuleException.class);
    }
}

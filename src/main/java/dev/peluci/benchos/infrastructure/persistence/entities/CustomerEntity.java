package dev.peluci.benchos.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = @UniqueConstraint(
                columnNames = { "phone", "tenant_id" }
        )
)
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity extends BaseJPAEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column
    private String email;
}

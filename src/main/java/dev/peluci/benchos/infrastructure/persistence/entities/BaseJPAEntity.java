package dev.peluci.benchos.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor
public abstract class BaseJPAEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    protected Boolean active = true;

    @CreatedDate
    @Column(name = "dt_insert", nullable = false, updatable = false)
    protected LocalDateTime dtInsert;

    @LastModifiedDate
    @Column(name = "dt_update", nullable = false)
    protected LocalDateTime dtUpdate;
    @Column(name = "dt_delete")
    protected LocalDateTime dtDelete;

}

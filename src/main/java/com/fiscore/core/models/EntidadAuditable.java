package com.fiscore.core.models;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Rastro de autoría común a las entidades de negocio.
 *
 * Spring Data rellena los cuatro campos solo; el autor lo resuelve el
 * AuditorAware de {@link com.fiscore.core.config.AuditoriaConfig}.
 *
 * Las entidades que heredan de aquí llevan @EqualsAndHashCode(callSuper = false)
 * a propósito: dos registros son el mismo por su identidad de negocio, no por
 * quién los tocó por última vez.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class EntidadAuditable {

    @CreatedBy
    @Column(name = "creado_por", length = 60, updatable = false)
    private String creadoPor;

    @CreatedDate
    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @LastModifiedBy
    @Column(name = "modificado_por", length = 60)
    private String modificadoPor;

    @LastModifiedDate
    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;
}

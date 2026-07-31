package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "perfil_funcionario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerfilFuncionario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true, nullable = false)
    private Usuario usuario;

    @Column(name = "voucher_vr", precision = 10, scale = 2)
    private BigDecimal voucherVr;

    // Métodos utilitários
    public boolean hasVoucherVr() {
        return voucherVr != null && voucherVr.compareTo(BigDecimal.ZERO) > 0;
    }
}
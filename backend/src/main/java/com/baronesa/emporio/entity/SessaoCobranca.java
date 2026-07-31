package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TipoCobranca;
import com.baronesa.emporio.enums.StatusCobranca;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessao_cobrancas")
public class SessaoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sessao_mesa_id", nullable = false)
    private Long sessaoMesaId;

    @Column(name = "sessao_convidado_id", nullable = false)
    private Long sessaoConvidadoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCobranca tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "evento_id")
    private Long eventoId;

    @Column(nullable = false)
    private Boolean isento = false;

    @Column(name = "motivo_isencao", length = 255)
    private String motivoIsencao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status = StatusCobranca.ATIVA;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por", length = 255)
    private String criadoPor;

    // Constructors
    public SessaoCobranca() {}

    public SessaoCobranca(Long sessaoMesaId, Long sessaoConvidadoId, TipoCobranca tipo, BigDecimal valor, 
                         Long eventoId, Boolean isento, String motivoIsencao, String criadoPor) {
        this.sessaoMesaId = sessaoMesaId;
        this.sessaoConvidadoId = sessaoConvidadoId;
        this.tipo = tipo;
        this.valor = valor;
        this.eventoId = eventoId;
        this.isento = isento != null ? isento : false;
        this.motivoIsencao = motivoIsencao;
        this.criadoPor = criadoPor;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessaoMesaId() {
        return sessaoMesaId;
    }

    public void setSessaoMesaId(Long sessaoMesaId) {
        this.sessaoMesaId = sessaoMesaId;
    }

    public Long getSessaoConvidadoId() {
        return sessaoConvidadoId;
    }

    public void setSessaoConvidadoId(Long sessaoConvidadoId) {
        this.sessaoConvidadoId = sessaoConvidadoId;
    }

    public TipoCobranca getTipo() {
        return tipo;
    }

    public void setTipo(TipoCobranca tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public void setEventoId(Long eventoId) {
        this.eventoId = eventoId;
    }

    public Boolean getIsento() {
        return isento;
    }

    public void setIsento(Boolean isento) {
        this.isento = isento;
    }

    public String getMotivoIsencao() {
        return motivoIsencao;
    }

    public void setMotivoIsencao(String motivoIsencao) {
        this.motivoIsencao = motivoIsencao;
    }

    public StatusCobranca getStatus() {
        return status;
    }

    public void setStatus(StatusCobranca status) {
        this.status = status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public void setCriadoPor(String criadoPor) {
        this.criadoPor = criadoPor;
    }
}
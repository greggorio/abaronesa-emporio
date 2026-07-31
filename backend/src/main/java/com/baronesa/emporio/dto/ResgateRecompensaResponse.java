package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public class ResgateRecompensaResponse {
    private Long resgateId;
    private Long clienteId;
    private Long recompensaId;
    private Integer pontosDebitados;
    private Integer saldoAnterior;
    private Integer saldoAtual;
    private LocalDateTime dataHora;
    
    public ResgateRecompensaResponse(Long resgateId, Long clienteId, Long recompensaId, 
                                   Integer pontosDebitados, Integer saldoAnterior, 
                                   Integer saldoAtual, LocalDateTime dataHora) {
        this.resgateId = resgateId;
        this.clienteId = clienteId;
        this.recompensaId = recompensaId;
        this.pontosDebitados = pontosDebitados;
        this.saldoAnterior = saldoAnterior;
        this.saldoAtual = saldoAtual;
        this.dataHora = dataHora;
    }
    
    // getters e setters
    public Long getResgateId() { return resgateId; }
    public void setResgateId(Long resgateId) { this.resgateId = resgateId; }
    
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    
    public Long getRecompensaId() { return recompensaId; }
    public void setRecompensaId(Long recompensaId) { this.recompensaId = recompensaId; }
    
    public Integer getPontosDebitados() { return pontosDebitados; }
    public void setPontosDebitados(Integer pontosDebitados) { this.pontosDebitados = pontosDebitados; }
    
    public Integer getSaldoAnterior() { return saldoAnterior; }
    public void setSaldoAnterior(Integer saldoAnterior) { this.saldoAnterior = saldoAnterior; }
    
    public Integer getSaldoAtual() { return saldoAtual; }
    public void setSaldoAtual(Integer saldoAtual) { this.saldoAtual = saldoAtual; }
    
    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
}
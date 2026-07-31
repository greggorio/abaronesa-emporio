package com.baronesa.emporio.dto;

public class ResgateRecompensaRequest {
    private Long clienteId;
    private Long recompensaId;
    private String observacao;
    
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    
    public Long getRecompensaId() { return recompensaId; }
    public void setRecompensaId(Long recompensaId) { this.recompensaId = recompensaId; }
    
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
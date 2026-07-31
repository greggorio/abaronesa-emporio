package com.baronesa.emporio.dto;

import java.time.LocalDateTime;
import java.util.List;

public class DashboardGamificacaoResponse {
    
    private KPIs kpis;
    private Rankings rankings;
    
    public DashboardGamificacaoResponse(KPIs kpis, Rankings rankings) {
        this.kpis = kpis;
        this.rankings = rankings;
    }
    
    public static class KPIs {
        private int recompensasAtivas;
        private int participantesComPontos;
        private int adesoesUltimos30Dias;
        private int pontosEmitidosUltimos30Dias;
        private int saldoTotalAtivo;
        private int pontosResgatadosUltimos30Dias;
        private double taxaResgate;
        
        public KPIs(int recompensasAtivas, int participantesComPontos, int adesoesUltimos30Dias, 
                   int pontosEmitidosUltimos30Dias, int saldoTotalAtivo, int pontosResgatadosUltimos30Dias, double taxaResgate) {
            this.recompensasAtivas = recompensasAtivas;
            this.participantesComPontos = participantesComPontos;
            this.adesoesUltimos30Dias = adesoesUltimos30Dias;
            this.pontosEmitidosUltimos30Dias = pontosEmitidosUltimos30Dias;
            this.saldoTotalAtivo = saldoTotalAtivo;
            this.pontosResgatadosUltimos30Dias = pontosResgatadosUltimos30Dias;
            this.taxaResgate = taxaResgate;
        }
        
        // getters e setters
        public int getRecompensasAtivas() { return recompensasAtivas; }
        public void setRecompensasAtivas(int recompensasAtivas) { this.recompensasAtivas = recompensasAtivas; }
        
        public int getParticipantesComPontos() { return participantesComPontos; }
        public void setParticipantesComPontos(int participantesComPontos) { this.participantesComPontos = participantesComPontos; }
        
        public int getAdesoesUltimos30Dias() { return adesoesUltimos30Dias; }
        public void setAdesoesUltimos30Dias(int adesoesUltimos30Dias) { this.adesoesUltimos30Dias = adesoesUltimos30Dias; }
        
        public int getPontosEmitidosUltimos30Dias() { return pontosEmitidosUltimos30Dias; }
        public void setPontosEmitidosUltimos30Dias(int pontosEmitidosUltimos30Dias) { this.pontosEmitidosUltimos30Dias = pontosEmitidosUltimos30Dias; }
        
        public int getSaldoTotalAtivo() { return saldoTotalAtivo; }
        public void setSaldoTotalAtivo(int saldoTotalAtivo) { this.saldoTotalAtivo = saldoTotalAtivo; }
        
        public int getPontosResgatadosUltimos30Dias() { return pontosResgatadosUltimos30Dias; }
        public void setPontosResgatadosUltimos30Dias(int pontosResgatadosUltimos30Dias) { this.pontosResgatadosUltimos30Dias = pontosResgatadosUltimos30Dias; }
        
        public double getTaxaResgate() { return taxaResgate; }
        public void setTaxaResgate(double taxaResgate) { this.taxaResgate = taxaResgate; }
    }
    
    public static class Rankings {
        private List<ClientePontos> topPontuadoresUltimos30Dias;
        private List<ClienteSaldo> topSaldosAtuais;
        private List<UltimoResgate> ultimosResgates;
        private List<TopRecompensaResgatada> topRecompensasResgatadasUltimos30Dias;
        
        public Rankings(List<ClientePontos> topPontuadoresUltimos30Dias, List<ClienteSaldo> topSaldosAtuais, List<UltimoResgate> ultimosResgates, List<TopRecompensaResgatada> topRecompensasResgatadasUltimos30Dias) {
            this.topPontuadoresUltimos30Dias = topPontuadoresUltimos30Dias;
            this.topSaldosAtuais = topSaldosAtuais;
            this.ultimosResgates = ultimosResgates;
            this.topRecompensasResgatadasUltimos30Dias = topRecompensasResgatadasUltimos30Dias;
        }
        
        // getters e setters
        public List<ClientePontos> getTopPontuadoresUltimos30Dias() { return topPontuadoresUltimos30Dias; }
        public void setTopPontuadoresUltimos30Dias(List<ClientePontos> topPontuadoresUltimos30Dias) { this.topPontuadoresUltimos30Dias = topPontuadoresUltimos30Dias; }
        
        public List<ClienteSaldo> getTopSaldosAtuais() { return topSaldosAtuais; }
        public void setTopSaldosAtuais(List<ClienteSaldo> topSaldosAtuais) { this.topSaldosAtuais = topSaldosAtuais; }
        
        public List<UltimoResgate> getUltimosResgates() { return ultimosResgates; }
        public void setUltimosResgates(List<UltimoResgate> ultimosResgates) { this.ultimosResgates = ultimosResgates; }
        
        public List<TopRecompensaResgatada> getTopRecompensasResgatadasUltimos30Dias() { return topRecompensasResgatadasUltimos30Dias; }
        public void setTopRecompensasResgatadasUltimos30Dias(List<TopRecompensaResgatada> topRecompensasResgatadasUltimos30Dias) { this.topRecompensasResgatadasUltimos30Dias = topRecompensasResgatadasUltimos30Dias; }
    }
    
    public static class UltimoResgate {
        private Long clienteId;
        private String clienteNome;
        private Long recompensaId;
        private String recompensaNome;
        private Integer pontos;
        private LocalDateTime dataHora;
        
        public UltimoResgate(Long clienteId, String clienteNome, Long recompensaId, String recompensaNome, Integer pontos, LocalDateTime dataHora) {
            this.clienteId = clienteId;
            this.clienteNome = clienteNome;
            this.recompensaId = recompensaId;
            this.recompensaNome = recompensaNome;
            this.pontos = pontos;
            this.dataHora = dataHora;
        }
        
        // getters e setters
        public Long getClienteId() { return clienteId; }
        public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
        
        public String getClienteNome() { return clienteNome; }
        public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
        
        public Long getRecompensaId() { return recompensaId; }
        public void setRecompensaId(Long recompensaId) { this.recompensaId = recompensaId; }
        
        public String getRecompensaNome() { return recompensaNome; }
        public void setRecompensaNome(String recompensaNome) { this.recompensaNome = recompensaNome; }
        
        public Integer getPontos() { return pontos; }
        public void setPontos(Integer pontos) { this.pontos = pontos; }
        
        public LocalDateTime getDataHora() { return dataHora; }
        public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
    }
    
    public static class ClientePontos {
        private Long clienteId;
        private String nome;
        private int pontos;
        
        public ClientePontos(Long clienteId, String nome, int pontos) {
            this.clienteId = clienteId;
            this.nome = nome;
            this.pontos = pontos;
        }
        
        // getters e setters
        public Long getClienteId() { return clienteId; }
        public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
        
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        
        public int getPontos() { return pontos; }
        public void setPontos(int pontos) { this.pontos = pontos; }
    }
    
    public static class ClienteSaldo {
        private Long clienteId;
        private String nome;
        private int saldo;
        
        public ClienteSaldo(Long clienteId, String nome, int saldo) {
            this.clienteId = clienteId;
            this.nome = nome;
            this.saldo = saldo;
        }
        
        // getters e setters
        public Long getClienteId() { return clienteId; }
        public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
        
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        
        public int getSaldo() { return saldo; }
        public void setSaldo(int saldo) { this.saldo = saldo; }
    }
    
    public static class TopRecompensaResgatada {
        private Long recompensaId;
        private String nome;
        private Long totalResgates;
        private Integer pontosTotalResgatado;
        
        public TopRecompensaResgatada(Long recompensaId, String nome, Long totalResgates, Integer pontosTotalResgatado) {
            this.recompensaId = recompensaId;
            this.nome = nome;
            this.totalResgates = totalResgates;
            this.pontosTotalResgatado = pontosTotalResgatado;
        }
        
        // getters e setters
        public Long getRecompensaId() { return recompensaId; }
        public void setRecompensaId(Long recompensaId) { this.recompensaId = recompensaId; }
        
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        
        public Long getTotalResgates() { return totalResgates; }
        public void setTotalResgates(Long totalResgates) { this.totalResgates = totalResgates; }
        
        public Integer getPontosTotalResgatado() { return pontosTotalResgatado; }
        public void setPontosTotalResgatado(Integer pontosTotalResgatado) { this.pontosTotalResgatado = pontosTotalResgatado; }
    }
    
    // getters e setters
    public KPIs getKpis() { return kpis; }
    public void setKpis(KPIs kpis) { this.kpis = kpis; }
    
    public Rankings getRankings() { return rankings; }
    public void setRankings(Rankings rankings) { this.rankings = rankings; }
}
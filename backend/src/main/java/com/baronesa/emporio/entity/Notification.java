package com.baronesa.emporio.entity;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String titulo;
    
    @Column(nullable = false, length = 1000)
    private String mensagem;
    
    @Column(nullable = false)
    private String tipo; // GERAL, INDIVIDUAL, ROLE
    
    @Column(name = "user_id")
    private Long userId; // Para notificações individuais
    
    private String role; // Para notificações por role (ADMIN, ATENDENTE, etc.)
    
    @Column(nullable = false)
    private String importancia; // BAIXA, MEDIA, ALTA, URGENTE
    
    @Column(name = "data_criacao", nullable = false)
    private Timestamp dataCriacao;
    
    @Column(name = "data_expiracao")
    private String dataExpiracao;
    
    private String link;
    
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    
    @Column(nullable = false)
    private Boolean ativo = true;
}
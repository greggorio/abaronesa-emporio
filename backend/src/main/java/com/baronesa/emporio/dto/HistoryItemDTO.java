package com.baronesa.emporio.dto;

import lombok.Data;

@Data
public class HistoryItemDTO {
    private String id;
    private String acao;
    private String data;
    private String usuario;
}
package com.baronesa.emporio.dto;

import lombok.Data;

import java.util.List;

@Data
public class TarefaValidadeRequest {
    private String observacao;
    private List<TarefaValidadeItemRequest> itens;
}

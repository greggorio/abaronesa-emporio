package com.baronesa.emporio.dto;

import lombok.Data;

@Data
public class CommentDTO {
    private String id;
    private String text;
    private String id_usuario;
    private String data;
    private String anexo;
    private String tipo_anexo;
}
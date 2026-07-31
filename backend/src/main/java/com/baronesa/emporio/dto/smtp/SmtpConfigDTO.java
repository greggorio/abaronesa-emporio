package com.baronesa.emporio.dto.smtp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfigDTO {
    private String servidor;
    private Integer porta;
    private String seguranca; // tls, ssl, none
    private String emailRemetente;
    private String nomeRemetente;
    private String usuario;
    private String senha;
}
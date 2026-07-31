package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidadeDashboardDTO {
    private Integer vencido;
    private Integer critico;
    private Integer atencao;
    private Integer semVidaUtil;
    private Integer ok;
    private Integer total;
}
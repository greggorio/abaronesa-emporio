package com.baronesa.website.dto;

import com.baronesa.website.enums.EventoStatus;
import com.baronesa.website.enums.GeneroMusical;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoResponseDTO {

    private Long id;
    private String titulo;
    private String descricao;
    private LocalDateTime dataEvento;
    private LocalDateTime dataHoraFim;
    private BigDecimal preco;
    private Boolean gratuito;
    private String banda;
    private GeneroMusical genero;
    private String generoDescricao;
    private String imagemUrl;
    private Boolean ativo;
    private EventoStatus status;
    private String statusDescricao;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}

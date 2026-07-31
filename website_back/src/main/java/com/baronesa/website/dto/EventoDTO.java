package com.baronesa.website.dto;

import com.baronesa.website.enums.EventoStatus;
import com.baronesa.website.enums.GeneroMusical;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoDTO {

    @NotBlank(message = "Título é obrigatório")
    private String titulo;

    private String descricao;

    @NotNull(message = "Data do evento é obrigatória")
    private LocalDateTime dataEvento;

    private LocalDateTime dataHoraFim;

    private BigDecimal preco;

    @NotNull(message = "Indicar se o evento é gratuito é obrigatório")
    private Boolean gratuito = false;

    private String banda;

    @NotNull(message = "Gênero musical é obrigatório")
    private GeneroMusical genero;

    private String imagemUrl;

    private Boolean ativo = true;

    private EventoStatus status = EventoStatus.AGENDADO;
}

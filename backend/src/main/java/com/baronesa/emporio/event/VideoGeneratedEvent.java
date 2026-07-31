package com.baronesa.emporio.event;

import com.baronesa.emporio.entity.ProductSignage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Evento disparado quando um vídeo de produto é gerado com sucesso
 * Usado para trigger da sincronização automática com signage-api
 */
@Getter
public class VideoGeneratedEvent extends ApplicationEvent {
    
    private final ProductSignage productSignage;
    
    public VideoGeneratedEvent(Object source, ProductSignage productSignage) {
        super(source);
        this.productSignage = productSignage;
    }
}

package com.baronesa.emporio.enums;

/**
 * Modos de geração de imagem IA para signage
 */
public enum AiImageMode {
    /**
     * Recorte do produto com fundo transparente (PNG)
     */
    CUTOUT_TRANSPARENT,
    
    /**
     * Produto isolado com sombra suave
     */
    ISOLATED_SHADOW,
    
    /**
     * Produto em contexto de cena (fundo gerado)
     */
    SCENE_CONTEXT;
    
    /**
     * Retorna o modo padrão
     */
    public static AiImageMode getDefault() {
        return CUTOUT_TRANSPARENT;
    }
}
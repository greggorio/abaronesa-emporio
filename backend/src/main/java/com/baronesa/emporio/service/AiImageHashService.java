package com.baronesa.emporio.service;

import com.baronesa.emporio.enums.AiImageMode;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

@Service
public class AiImageHashService {
    
    /**
     * Calcula hash determinístico para cache de imagem IA
     * 
     * @param originalImageHash Hash da imagem original do produto
     * @param templateId ID do template
     * @param aiMode Modo de geração
     * @param promptVersion Versão do prompt
     * @param outputSize Tamanho da saída (ex: 1024x1024)
     * @param revision Número de revisão (0 para geração normal, incrementado no force)
     * @return Hash SHA256
     */
    public String calculateHash(String originalImageHash, 
                                String templateId, 
                                AiImageMode aiMode,
                                String promptVersion, 
                                String outputSize,
                                int revision) {
        String data = String.join("|",
            originalImageHash,
            templateId,
            aiMode != null ? aiMode.name() : AiImageMode.getDefault().name(),
            promptVersion,
            outputSize,
            String.valueOf(revision)
        );
        
        return DigestUtils.sha256Hex(data);
    }
    
    /**
     * Calcula hash para geração normal (revision = 0)
     */
    public String calculateHash(String originalImageHash,
                                String templateId,
                                AiImageMode aiMode,
                                String promptVersion,
                                String outputSize) {
        return calculateHash(originalImageHash, templateId, aiMode, promptVersion, outputSize, 0);
    }
}
package com.baronesa.emporio.nfe.service;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.OrigemVenda;
import com.baronesa.emporio.nfe.config.ModeloFiscalConfig;
import com.baronesa.emporio.nfe.model.Venda;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decide se a venda deve ser emitida como NFe (55) ou NFCe (65).
 * Regras:
 *  - Configuração forçada (nfe_modelo) prevalece.
 *  - Cliente PJ (CNPJ) → NFe.
 *  - Demais casos → NFCe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModeloFiscalDetector {

    private final ConfigManager configManager;

    public ModeloFiscalConfig detectarModelo(Venda venda) {
        // 1) Config forçada
        String modeloConfig = configManager.getConfig("nfe_modelo", "");
        if (!modeloConfig.isBlank()) {
            try {
                int codigo = Integer.parseInt(modeloConfig.trim());
                ModeloFiscalConfig modelo = ModeloFiscalConfig.porCodigo(codigo);
                log.info("Modelo fiscal forçado pela configuração: {}", modelo);
                return modelo;
            } catch (Exception e) {
                log.warn("Valor inválido para nfe_modelo: {}", modeloConfig);
            }
        }

        // 2) Regras de negócio
        if (isClientePJ(venda.getCliente())) {
            return ModeloFiscalConfig.NFE;
        }

        if (!isNFCeConfigurado()) {
            log.warn("NFCe não configurado (CSC ausente). Prosseguindo com NFCe por regra de negócio.");
        }

        return ModeloFiscalConfig.NFCE;
    }

    private boolean isClientePJ(Usuario cliente) {
        if (cliente == null || cliente.getPerfilCliente() == null) return false;
        String cnpj = cliente.getPerfilCliente().getCnpj();
        return cnpj != null && !cnpj.trim().isEmpty();
    }

    private boolean isNFCeConfigurado() {
        String tokenCSC = configManager.getConfig("nfe_token_csc", "");
        String idCSC = configManager.getConfig("nfe_id_csc", "");
        return !tokenCSC.isBlank() && !idCSC.isBlank();
    }
}

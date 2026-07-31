package com.baronesa.emporio.constants;

/**
 * Constantes utilizadas no módulo de Nota Fiscal Eletrônica
 */
public final class NfeConstants {

    private NfeConstants() {
        // Classe utilitária, não deve ser instanciada
    }

    // Configurações NFe
    public static final String NFE_AMBIENTE = "nfe_ambiente";
    public static final String NFE_SERIE = "nfe_serie";
    public static final String NFE_NUMERO = "nfe_numero";
    public static final String NFE_MODELO = "nfe_modelo";

    // Configurações NFCe
    public static final String NFCE_AMBIENTE = "nfce_ambiente";
    public static final String NFCE_SERIE = "nfce_serie";
    public static final String NFCE_NUMERO = "nfce_numero";
    public static final String NFCE_TOKEN_CSC = "nfe_token_csc"; // Mantém o nome original
    public static final String NFCE_ID_CSC = "nfe_id_csc"; // Mantém o nome original
    public static final String NFCE_IMPRIMIR_AUTOMATICO = "nfce_imprimir_automatico";
    public static final String NFCE_VIA_CONSUMIDOR = "nfce_via_consumidor";
    public static final String NFCE_VIA_ESTABELECIMENTO = "nfce_via_estabelecimento";
    public static final String NFCE_MODO_IMPRESSAO = "nfce_modo_impressao";
    public static final String NFCE_LARGURA_BOBINA = "nfce_largura_bobina";
    public static final String NFCE_NOME_IMPRESSORA = "nfce_nome_impressora";

    // URLs por estado
    public static final String NFCE_URL_CONSULTA_PREFIX = "nfce_url_consulta_";
    public static final String NFCE_URL_CONSULTA_HOMOLOG_PREFIX = "nfce_url_consulta_homolog_";

    // Valores padrão
    public static final String DEFAULT_AMBIENTE = "2"; // Homologação
    public static final String DEFAULT_SERIE = "1";
    public static final String DEFAULT_NUMERO = "1";
    public static final String DEFAULT_MODELO_NFE = "55";
    public static final String DEFAULT_MODELO_NFCE = "65";
    public static final String DEFAULT_LARGURA_BOBINA = "80";
    public static final String DEFAULT_MODO_IMPRESSAO = "TERMICA";

    // Limites NFCe
    public static final double LIMITE_NFCE_SEM_CPF = 10000.00; // Valor máximo sem identificação
}

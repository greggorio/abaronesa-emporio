#!/bin/bash

# Script para execução dos testes de UI na ordem correta e geração de relatório
# Autores: Qwen Code Assistant
# Data: Fevereiro 2026

set -e  # Sai imediatamente se um comando retornar um código diferente de zero

echo "Iniciando execução dos testes de UI para o módulo Contas a Pagar..."
echo "Data/Hora: $(date)"
echo ""

# Diretório base dos testes
TEST_DIR="./"
LOG_DIR="./logs"
REPORT_DIR="./reports"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Cria diretórios se não existirem
mkdir -p "$LOG_DIR"
mkdir -p "$REPORT_DIR"

echo "Diretório de testes: $TEST_DIR"
echo "Diretório de logs: $LOG_DIR"
echo "Diretório de relatórios: $REPORT_DIR"
echo ""

# Arquivo de relatório
REPORT_FILE="$REPORT_DIR/resultados_testes_$TIMESTAMP.md"

# Inicializa o arquivo de relatório
cat > "$REPORT_FILE" << EOF
# Relatório de Execução dos Testes - Módulo Contas a Pagar

**Data/Hora de Execução:** $(date)

## Visão Geral

Este relatório apresenta os resultados da execução dos testes automatizados para o módulo de Contas a Pagar e suas entidades dependentes no sistema ERP Bakery.

## Configuração dos Testes

- **URL Base:** http://localhost:8084
- **Credenciais:** root@localhost / 123456
- **Ambiente:** Local de desenvolvimento

## Resultados Detalhados

EOF

# Função para executar um teste e registrar resultado
executar_teste() {
    local teste=$1
    local descricao=$2
    local log_file="$LOG_DIR/${teste%.mjs}_$TIMESTAMP.log"
    
    echo "Executando: $descricao"
    echo "Arquivo: $teste"
    echo "Log: $log_file"
    
    if node "$TEST_DIR/$teste" 2>&1 | tee "$log_file"; then
        echo "✓ Teste $teste executado com sucesso!"
        RESULTADO="✅ Sucesso"
        DETALHES="Execução concluída sem erros"
    else
        echo "✗ Erro ao executar o teste $teste"
        RESULTADO="❌ Falha"
        DETALHES="Erro detectado durante a execução - verifique o log: $log_file"
    fi
    
    # Adiciona resultado ao relatório
    cat >> "$REPORT_FILE" << EOF

### $descricao

- **Arquivo:** \`$teste\`
- **Resultado:** $RESULTADO
- **Detalhes:** $DETALHES
- **Log:** [$log_file](./$log_file)

EOF

    echo ""
}

# FASE 1: Cadastro de Dados Mestre
echo "==================================="
echo "FASE 1: Cadastro de Dados Mestre"
echo "==================================="
echo ""

executar_teste "categoria-despesa/TC01_categoria_despesa_cadastro_positivo.mjs" \
    "TC01 - Cadastro de categoria de despesa com dados válidos"

executar_teste "fornecedor/TC02_fornecedor_cadastro_positivo.mjs" \
    "TC02 - Cadastro de fornecedor com dados válidos"

# FASE 2: Cadastro de Contas a Pagar
echo "==================================="
echo "FASE 2: Cadastro de Contas a Pagar"
echo "==================================="
echo ""

executar_teste "contas-pagar/TC03_conta_pagar_cadastro_positivo.mjs" \
    "TC03 - Cadastro de conta a pagar com dados válidos"

executar_teste "contas-pagar/TC04_conta_pagar_cadastro_valores_invalidos.mjs" \
    "TC04 - Cadastro de conta a pagar com valores inválidos (cenario negativo)"

executar_teste "contas-pagar/TC05_conta_pagar_cadastro_campos_obrigatorios_vazios.mjs" \
    "TC05 - Cadastro de conta a pagar com campos obrigatórios vazios (cenario negativo)"

# FASE 3: Processamento de Pagamentos
echo "==================================="
echo "FASE 3: Processamento de Pagamentos"
echo "==================================="
echo ""

executar_teste "contas-pagar/TC06_pagamento_parcela_sucesso.mjs" \
    "TC06 - Pagamento de parcela com sucesso"

executar_teste "contas-pagar/TC07_pagamento_parcela_sem_data_forma.mjs" \
    "TC07 - Tentativa de pagamento sem data/forma (cenario negativo)"

# FASE 4: Atualização de Registros
echo "==================================="
echo "FASE 4: Atualização de Registros"
echo "==================================="
echo ""

executar_teste "contas-pagar/TC08_atualizacao_conta_pagar.mjs" \
    "TC08 - Atualização de conta a pagar existente"

executar_teste "fornecedor/TC09_atualizacao_fornecedor.mjs" \
    "TC09 - Atualização de fornecedor existente"

# FASE 5: Testes de Exclusão (Executados no Final)
echo "==================================="
echo "FASE 5: Testes de Exclusão"
echo "==================================="
echo ""

executar_teste "contas-pagar/TC10_exclusao_parcela_pago_erro.mjs" \
    "TC10 - Tentativa de exclusão de parcela paga (cenario negativo)"

executar_teste "contas-pagar/TC11_exclusao_conta_com_parcelas_pagas_erro.mjs" \
    "TC11 - Tentativa de exclusão de conta com parcelas pagas (cenario negativo)"

executar_teste "contas-pagar/TC12_exclusao_conta_pagar_sucesso.mjs" \
    "TC12 - Exclusão de conta a pagar sem parcelas pagas"

executar_teste "fornecedor/TC13_exclusao_fornecedor_com_contas_associadas_erro.mjs" \
    "TC13 - Tentativa de exclusão de fornecedor com contas associadas (cenario negativo)"

executar_teste "fornecedor/TC14_exclusao_fornecedor_sucesso.mjs" \
    "TC14 - Exclusão de fornecedor sem contas associadas"

executar_teste "categoria-despesa/TC15_exclusao_categoria_com_contas_associadas_erro.mjs" \
    "TC15 - Tentativa de exclusão de categoria com contas associadas (cenario negativo)"

executar_teste "categoria-despesa/TC16_exclusao_categoria_sucesso.mjs" \
    "TC16 - Exclusão de categoria de despesa sem contas associadas"

# Adiciona resumo ao final do relatório
TOTAL_TESTES=16
SUCESSOS=$(grep -c "✅ Sucesso" "$REPORT_FILE" || echo 0)
FALHAS=$((TOTAL_TESTES - SUCESSOS))

cat >> "$REPORT_FILE" << EOF

## Resumo Geral

- **Total de Testes Executados:** $TOTAL_TESTES
- **Testes com Sucesso:** $SUCESSOS
- **Testes com Falha:** $FALHAS
- **Taxa de Sucesso:** $(( (SUCESSOS * 100) / TOTAL_TESTES ))%

EOF

if [ $FALHAS -eq 0 ]; then
    cat >> "$REPORT_FILE" << EOF

🎉 **Parabéns!** Todos os testes foram executados com sucesso!
EOF
else
    cat >> "$REPORT_FILE" << EOF

⚠️ **Atenção:** Foram detectadas falhas em alguns testes. Consulte os detalhes acima.
EOF
fi

cat >> "$REPORT_FILE" << EOF

---

Relatório gerado automaticamente pelo script de execução de testes.
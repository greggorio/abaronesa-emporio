# Relatório de Execução dos Testes - Módulo Contas a Pagar

**Data/Hora de Execução:** Fri Feb 13 07:48:59 PM -03 2026

## Visão Geral

Este relatório apresenta os resultados da execução dos testes automatizados para o módulo de Contas a Pagar e suas entidades dependentes no sistema ERP Bakery.

## Configuração dos Testes

- **URL Base:** http://localhost:8084
- **Credenciais:** root@localhost / 123456
- **Ambiente:** Local de desenvolvimento

## Resultados Detalhados


### TC01 - Cadastro de categoria de despesa com dados válidos

- **Arquivo:** `categoria-despesa/TC01_categoria_despesa_cadastro_positivo.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/categoria-despesa/TC01_categoria_despesa_cadastro_positivo_20260213_194859.log
- **Log:** [./logs/categoria-despesa/TC01_categoria_despesa_cadastro_positivo_20260213_194859.log](././logs/categoria-despesa/TC01_categoria_despesa_cadastro_positivo_20260213_194859.log)


### TC02 - Cadastro de fornecedor com dados válidos

- **Arquivo:** `fornecedor/TC02_fornecedor_cadastro_positivo.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/fornecedor/TC02_fornecedor_cadastro_positivo_20260213_194859.log
- **Log:** [./logs/fornecedor/TC02_fornecedor_cadastro_positivo_20260213_194859.log](././logs/fornecedor/TC02_fornecedor_cadastro_positivo_20260213_194859.log)


### TC03 - Cadastro de conta a pagar com dados válidos

- **Arquivo:** `contas-pagar/TC03_conta_pagar_cadastro_positivo.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC03_conta_pagar_cadastro_positivo_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC03_conta_pagar_cadastro_positivo_20260213_194859.log](././logs/contas-pagar/TC03_conta_pagar_cadastro_positivo_20260213_194859.log)


### TC04 - Cadastro de conta a pagar com valores inválidos (cenario negativo)

- **Arquivo:** `contas-pagar/TC04_conta_pagar_cadastro_valores_invalidos.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC04_conta_pagar_cadastro_valores_invalidos_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC04_conta_pagar_cadastro_valores_invalidos_20260213_194859.log](././logs/contas-pagar/TC04_conta_pagar_cadastro_valores_invalidos_20260213_194859.log)


### TC05 - Cadastro de conta a pagar com campos obrigatórios vazios (cenario negativo)

- **Arquivo:** `contas-pagar/TC05_conta_pagar_cadastro_campos_obrigatorios_vazios.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC05_conta_pagar_cadastro_campos_obrigatorios_vazios_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC05_conta_pagar_cadastro_campos_obrigatorios_vazios_20260213_194859.log](././logs/contas-pagar/TC05_conta_pagar_cadastro_campos_obrigatorios_vazios_20260213_194859.log)


### TC06 - Pagamento de parcela com sucesso

- **Arquivo:** `contas-pagar/TC06_pagamento_parcela_sucesso.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC06_pagamento_parcela_sucesso_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC06_pagamento_parcela_sucesso_20260213_194859.log](././logs/contas-pagar/TC06_pagamento_parcela_sucesso_20260213_194859.log)


### TC07 - Tentativa de pagamento sem data/forma (cenario negativo)

- **Arquivo:** `contas-pagar/TC07_pagamento_parcela_sem_data_forma.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC07_pagamento_parcela_sem_data_forma_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC07_pagamento_parcela_sem_data_forma_20260213_194859.log](././logs/contas-pagar/TC07_pagamento_parcela_sem_data_forma_20260213_194859.log)


### TC08 - Atualização de conta a pagar existente

- **Arquivo:** `contas-pagar/TC08_atualizacao_conta_pagar.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC08_atualizacao_conta_pagar_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC08_atualizacao_conta_pagar_20260213_194859.log](././logs/contas-pagar/TC08_atualizacao_conta_pagar_20260213_194859.log)


### TC09 - Atualização de fornecedor existente

- **Arquivo:** `fornecedor/TC09_atualizacao_fornecedor.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/fornecedor/TC09_atualizacao_fornecedor_20260213_194859.log
- **Log:** [./logs/fornecedor/TC09_atualizacao_fornecedor_20260213_194859.log](././logs/fornecedor/TC09_atualizacao_fornecedor_20260213_194859.log)


### TC10 - Tentativa de exclusão de parcela paga (cenario negativo)

- **Arquivo:** `contas-pagar/TC10_exclusao_parcela_pago_erro.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC10_exclusao_parcela_pago_erro_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC10_exclusao_parcela_pago_erro_20260213_194859.log](././logs/contas-pagar/TC10_exclusao_parcela_pago_erro_20260213_194859.log)


### TC11 - Tentativa de exclusão de conta com parcelas pagas (cenario negativo)

- **Arquivo:** `contas-pagar/TC11_exclusao_conta_com_parcelas_pagas_erro.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC11_exclusao_conta_com_parcelas_pagas_erro_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC11_exclusao_conta_com_parcelas_pagas_erro_20260213_194859.log](././logs/contas-pagar/TC11_exclusao_conta_com_parcelas_pagas_erro_20260213_194859.log)


### TC12 - Exclusão de conta a pagar sem parcelas pagas

- **Arquivo:** `contas-pagar/TC12_exclusao_conta_pagar_sucesso.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/contas-pagar/TC12_exclusao_conta_pagar_sucesso_20260213_194859.log
- **Log:** [./logs/contas-pagar/TC12_exclusao_conta_pagar_sucesso_20260213_194859.log](././logs/contas-pagar/TC12_exclusao_conta_pagar_sucesso_20260213_194859.log)


### TC13 - Tentativa de exclusão de fornecedor com contas associadas (cenario negativo)

- **Arquivo:** `fornecedor/TC13_exclusao_fornecedor_com_contas_associadas_erro.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/fornecedor/TC13_exclusao_fornecedor_com_contas_associadas_erro_20260213_194859.log
- **Log:** [./logs/fornecedor/TC13_exclusao_fornecedor_com_contas_associadas_erro_20260213_194859.log](././logs/fornecedor/TC13_exclusao_fornecedor_com_contas_associadas_erro_20260213_194859.log)


### TC14 - Exclusão de fornecedor sem contas associadas

- **Arquivo:** `fornecedor/TC14_exclusao_fornecedor_sucesso.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/fornecedor/TC14_exclusao_fornecedor_sucesso_20260213_194859.log
- **Log:** [./logs/fornecedor/TC14_exclusao_fornecedor_sucesso_20260213_194859.log](././logs/fornecedor/TC14_exclusao_fornecedor_sucesso_20260213_194859.log)


### TC15 - Tentativa de exclusão de categoria com contas associadas (cenario negativo)

- **Arquivo:** `categoria-despesa/TC15_exclusao_categoria_com_contas_associadas_erro.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/categoria-despesa/TC15_exclusao_categoria_com_contas_associadas_erro_20260213_194859.log
- **Log:** [./logs/categoria-despesa/TC15_exclusao_categoria_com_contas_associadas_erro_20260213_194859.log](././logs/categoria-despesa/TC15_exclusao_categoria_com_contas_associadas_erro_20260213_194859.log)


### TC16 - Exclusão de categoria de despesa sem contas associadas

- **Arquivo:** `categoria-despesa/TC16_exclusao_categoria_sucesso.mjs`
- **Resultado:** ❌ Falha
- **Detalhes:** Erro detectado durante a execução - verifique o log: ./logs/categoria-despesa/TC16_exclusao_categoria_sucesso_20260213_194859.log
- **Log:** [./logs/categoria-despesa/TC16_exclusao_categoria_sucesso_20260213_194859.log](././logs/categoria-despesa/TC16_exclusao_categoria_sucesso_20260213_194859.log)


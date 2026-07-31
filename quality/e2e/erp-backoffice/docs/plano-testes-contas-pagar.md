# Plano de Testes - Módulo Contas a Pagar

## Visão Geral
Este documento descreve a estratégia e sequência de testes para o módulo de Contas a Pagar e suas entidades dependentes no sistema ERP Bakery.

## Fases do Projeto de Testes

### Fase 1: Testes de UI e Validação de Mensagens de Erro
Nesta fase, vamos nos restringir a testes de UI, com foco especial em validar se as respostas enviadas pelo backend em casos de erro estão sendo enviadas de forma adequada (mensagem de erro clara no error.message). Devemos ser capazes de identificar quais rotinas do backend precisam ser melhoradas para informar mais claramente o motivo do erro.

### Fase 2: Correlação entre Requisições e Logs do Backend
Após termos confiança de que as mensagens de erro provenientes de validação estão sendo enviadas corretamente pelo backend, vamos implementar um processo onde ao nos depararmos com um erro inesperado, iremos correlacionar a requisição com o log do backend. Nessa fase, de um lado você envia a requisição problemática e do outro lado captura a exception no log.

## Entidades Envolvidas
1. **Categoria Despesa** - Entidade mestre para classificação de despesas
2. **Fornecedor** - Entidade mestre para cadastro de fornecedores
3. **Conta a Pagar** - Entidade principal do módulo
4. **Parcelas** - Subentidade de Conta a Pagar

## Ordem de Execução dos Testes (Fase 1)

### Fase 1A: Cadastro de Dados Mestre
1. `TC01_categoria_despesa_cadastro_positivo.mjs`
   - Cenário: Cadastrar nova categoria de despesa com dados válidos
   - Pré-condição: Sistema acessível, usuário autenticado
   - Resultado esperado: Categoria cadastrada com sucesso

2. `TC02_fornecedor_cadastro_positivo.mjs`
   - Cenário: Cadastrar novo fornecedor com dados válidos
   - Pré-condição: Sistema acessível, usuário autenticado
   - Resultado esperado: Fornecedor cadastrado com sucesso

### Fase 1B: Cadastro de Contas a Pagar
3. `TC03_conta_pagar_cadastro_positivo.mjs`
   - Cenário: Cadastrar nova conta a pagar com dados válidos
   - Pré-condição: Categorias e fornecedores cadastrados
   - Resultado esperado: Conta a pagar cadastrada com sucesso e parcelas geradas

4. `TC04_conta_pagar_cadastro_valores_invalidos.mjs`
   - Cenário: Tentar cadastrar conta a pagar com valores inválidos
   - Pré-condição: Sistema acessível, usuário autenticado
   - Resultado esperado: Validação de campos exibida com mensagem clara, cadastro não realizado

5. `TC05_conta_pagar_cadastro_campos_obrigatorios_vazios.mjs`
   - Cenário: Tentar cadastrar conta a pagar sem preencher campos obrigatórios
   - Pré-condição: Sistema acessível, usuário autenticado
   - Resultado esperado: Validação de campos obrigatórios exibida com mensagem clara, cadastro não realizado

### Fase 1C: Processamento de Pagamentos
6. `TC06_pagamento_parcela_sucesso.mjs`
   - Cenário: Realizar pagamento de parcela de conta a pagar
   - Pré-condição: Conta a pagar com parcelas pendentes cadastrada
   - Resultado esperado: Parcela marcada como paga, movimento financeiro registrado

7. `TC07_pagamento_parcela_sem_data_forma.mjs`
   - Cenário: Tentar realizar pagamento sem informar data ou forma de pagamento
   - Pré-condição: Conta a pagar com parcelas pendentes cadastrada
   - Resultado esperado: Mensagem de erro clara e específica exibida

### Fase 1D: Atualização de Registros
8. `TC08_atualizacao_conta_pagar.mjs`
   - Cenário: Atualizar dados de uma conta a pagar existente
   - Pré-condição: Conta a pagar cadastrada
   - Resultado esperado: Dados atualizados com sucesso

9. `TC09_atualizacao_fornecedor.mjs`
   - Cenário: Atualizar dados de um fornecedor existente
   - Pré-condição: Fornecedor cadastrado
   - Resultado esperado: Dados do fornecedor atualizados com sucesso

### Fase 1E: Testes de Exclusão (Executados no Final)
10. `TC10_exclusao_parcela_pago_erro.mjs`
    - Cenário: Tentar excluir parcela já paga
    - Pré-condição: Conta a pagar com parcela paga cadastrada
    - Resultado esperado: Mensagem de erro clara e específica exibida

11. `TC11_exclusao_conta_com_parcelas_pagas_erro.mjs`
    - Cenário: Tentar excluir conta com parcelas pagas
    - Pré-condição: Conta a pagar com parcelas pagas cadastrada
    - Resultado esperado: Mensagem de erro clara e específica exibida

12. `TC12_exclusao_conta_pagar_sucesso.mjs`
    - Cenário: Excluir conta a pagar sem parcelas pagas
    - Pré-condição: Conta a pagar sem parcelas pagas cadastrada
    - Resultado esperado: Conta excluída com sucesso

13. `TC13_exclusao_fornecedor_com_contas_associadas_erro.mjs`
    - Cenário: Tentar excluir fornecedor com contas associadas
    - Pré-condição: Fornecedor com contas a pagar associadas cadastrado
    - Resultado esperado: Mensagem de erro clara e específica impedindo exclusão

14. `TC14_exclusao_fornecedor_sucesso.mjs`
    - Cenário: Excluir fornecedor sem contas associadas
    - Pré-condição: Fornecedor sem contas a pagar associadas cadastrado
    - Resultado esperado: Fornecedor excluído com sucesso

15. `TC15_exclusao_categoria_com_contas_associadas_erro.mjs`
    - Cenário: Tentar excluir categoria com contas associadas
    - Pré-condição: Categoria de despesa com contas a pagar associadas cadastrada
    - Resultado esperado: Mensagem de erro clara e específica impedindo exclusão

16. `TC16_exclusao_categoria_sucesso.mjs`
    - Cenário: Excluir categoria de despesa sem contas associadas
    - Pré-condição: Categoria de despesa sem contas a pagar associadas cadastrada
    - Resultado esperado: Categoria excluída com sucesso

## Estratégia de Dados de Teste

### Dados Positivos
- Categorias: "Despesa Teste Automatizado", "Manutenção Equipamentos", etc.
- Fornecedores: "Fornecedor Teste Automatizado", CNPJ: 12345678901234
- Contas a Pagar: Descrição "Conta teste automatizado", Valor: R$ 1.000,00, 2 parcelas

### Dados Negativos
- Campos vazios para obrigatoriedade
- Valores inválidos para campos numéricos
- Datas inválidas
- CNPJs inválidos

## Considerações de Execução (Fase 1)

1. Os testes devem ser executados na ordem especificada para garantir dependências
2. Os testes de exclusão devem ser executados apenas após todos os testes funcionais positivos e negativos
3. É importante manter os dados de teste consistentes entre os testes
4. Após a execução completa, deve-se considerar a limpeza dos dados de teste se necessário
5. Em todos os testes negativos, deve-se validar que as mensagens de erro são claras e específicas
6. Identificar quaisquer mensagens de erro genéricas ou confusas que precisem ser melhoradas no backend

## Critérios de Sucesso (Fase 1)

- Todos os testes positivos devem passar
- Todos os testes negativos devem falhar conforme esperado (validações funcionando)
- Nenhum teste deve afetar os resultados de outros testes
- As validações de integridade referencial devem funcionar corretamente
- As mensagens de erro exibidas devem ser claras e específicas
- Quaisquer mensagens de erro inadequadas devem ser registradas para melhoria posterior no backend

## Planejamento da Fase 2

Após completar a Fase 1 com sucesso e garantir que as mensagens de erro estão sendo tratadas adequadamente no frontend, a Fase 2 introduzirá:

1. Monitoramento dos logs do backend durante a execução dos testes
2. Correlação entre requisições problemáticas e exceptions registradas no log
3. Análise mais profunda de exceções não tratadas ou mal tratadas
4. Melhorias no tratamento de exceções no backend com base nas descobertas
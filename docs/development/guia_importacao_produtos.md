# Guia de Importação de Produtos (XLS + CSV)

## 1. Contexto e objetivo

- O desafio imediato é importar duas fontes conhecidas:
  1. A planilha `Produtos_Espresso_11.25.xlsx` entregue pelo cliente.
  2. O CSV `backend/outputs/relatorio_produtos.csv` (gerado atualmente pela rotina de exportação no backend).
- A meta é documentar um fluxo simples e seguro que permita validar, prever e registrar esses produtos no sistema sem overengineering, facilitando tarefas futuras de extensão.
- O documento serve como referência para alinhar você (Product Owner), eu (Codex) e Qwen (implementador de código).

## 2. Fontes de dados e layout mínimo

| Formato | Arquivo | Colunas principais obrigatórias |
|---------|---------|----------------------------------|
| XLSX | `Produtos_Espresso_11.25.xlsx` | `Código`, `Descrição`, `Custo`, `Un.`, `Ativo`, `Grupo`, `Código de Barras`, `Estoque Minimo`, `Localização`, `NCM` |
| CSV | `backend/outputs/relatorio_produtos.csv` | `codigo`, `descricao`, `unid`, `estoque`, `grupo`, `subgrupo`, `setor`, `vr_venda`, `vr_custo` |

## 3. Regras de transformação e normalização

1. **Deduplicação**: identificar produtos duplicados por `Código`/`codigo` (campo `codigoInterno` no backend). Reimportações devem pular duplicatas salvas (logar/relatar).
2. **Normalização numérica**: aceitar vírgula como separador decimal (ofuscado pelo XLS e CSV) e garantir `BigDecimal` (ex.: `2,2000000000000002` deve virar `2.20`).
3. **Unidade**: traduzir `Un.` ou `PC` para `UN`/`PC`, com fallback na unidade base `UNIDADE`.
4. **Atributos obrigatórios do payload**:
   - `nome`/`descricao`: mapear `Descrição`.
   - `codigoInterno`: usar `Código`.
   - `precoVenda`: usar `Custo` ou `vr_venda`.
   - `categoriaId`: aplicar categoria padrão (ex.: `1`) quando não houver mapeamento rápido.
   - Flags: `ativo` = verdadeiro se `Ativo` for `1`, caso contrário `false`. `tipoPrecificacao` fixo em `SIMPLES`.
   - Campos fiscais como `ncm` podem ser preenchidos quando presentes.

## 4. Fluxo e checklist mínimo

1. Carregar arquivo (XLS ou CSV).
2. Parsear linhas respeitando o layout acima, aplicar normalizações e detectar linhas inválidas (preço ausente, código inválido etc.).
3. Mostrar preview/resumo (quantas linhas válidas, inválidas, duplicadas previstos).
4. Confirmar importação: o backend chama `POST /api/produtos` para cada registro válido (ou faz lote se conveniente), ignorando duplicatas.
5. Retornar relatório simples (ex.: `120 criados, 3 ignorados por duplicata, 2 inválidos`).

## 5. Critérios de aceitação (mínimos)

1. O XLS `Produtos_Espresso_11.25.xlsx` gera pré-visualização com nome, código e preço exatamente como ficarão no banco.
2. O CSV gerado por `backend/scripts/export_produtos_csv.py` importa sem perda de informação em `nome`, `descricao`, `precoVenda`, `categoria` e `unidade`.
3. Importações repetidas com o mesmo `Código` não geram duplicatas nem falha (apenas logam e continuam).

## 6. Implementação orientada

| Item | Descrição |
|------|-----------|
| Endpoint | Criar `/api/produtos/import/planilha` (ou similar) que aceite multipart/form-data com arquivo e query `tipo=xls\|csv`. |
| Parser XLS | Usar `openpyxl` (ou biblioteca Java equivalente) para ler a planilha. Mapear colunas fixas. |
| Parser CSV | Aceitar `backend/outputs/relatorio_produtos.csv` com cabeçalho padrão. |
| Normalização | Converter preços para `BigDecimal`, unificar unidades, normalizar `ativo`. |
| Deduplicação | Ignorar/registrar produtos já cadastrados com mesmo `codigoInterno`. |
| Categoria | Aplicar `categoriaId=1` (ou outra padrão) se `Grupo` não corresponder a categoria conhecida. |
| Resposta | Retornar JSON com contagem (`criadas`, `ignoradas`, `inválidas`) e lista das primeiras 5 linhas problemáticas. |

## 7. Pré-requisitos

1. Ambiente backend compilado (Spring Boot).
2. Acesso a `Produtos_Espresso_11.25.xlsx`.
3. `backend/outputs/relatorio_produtos.csv` gerado (rodar `backend/scripts/export_produtos_csv.py` se necessário).
4. Credenciais válidas para autenticar em `/api/auth/login` e chamar `/api/produtos`.

## 8. Checklist de entrega

- [ ] Endpoint documentado.
- [ ] Parser XLS validado com a planilha do cliente.
- [ ] Parser CSV validado com `backend/outputs/relatorio_produtos.csv`.
- [ ] Preview/resumo disponibilizado ao front antes de confirmar.
- [ ] Duplicatas por código foram ignoradas sem falha.
- [ ] Logs/resumo estão acessíveis.
- [ ] Documentação atualizada com exemplo de chamada.

## 9. Testes sugeridos

1. Subir XLS → conferir preview → confirmar → validar API retorna `criadas` e `ignoradas`.
2. Subir CSV → validar `precoVenda`, `codigoInterno`, `unidade`.
3. Reenviar um arquivo já importado → garantir retorno mostra `ignoradas`.
4. Validar no banco se produtos foram criados com `ncm`, `ativo`, `codigoInterno`.

## 10. Comunicação e documentação

- Registrar no git (ou changelog) a nova rota e os parâmetros aceitos.
- Adicionar nota rápida no README do backend sobre como acionar a rotina.
- Notificar Product Owner com resultado do teste para fechar a iteração.

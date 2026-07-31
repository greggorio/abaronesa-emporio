# Funcionalidade de Importação de Produtos - Frontend

## Descrição
Este componente fornece uma UI para fazer upload de arquivos XLS/XLSX contendo produtos e gerar um preview do resultado da importação.

## Localização
O componente está acessível através do Painel de Controle, na aba "Desenvolvimento", como "Importação de Produtos".

## Instruções de Uso
1. Acesse o Painel de Controle no menu de configurações
2. Vá até a aba "Desenvolvimento"
3. Clique no card "Importação de Produtos"
4. Selecione um arquivo XLS ou XLSX contendo produtos
5. O sistema gerará automaticamente um preview com:
   - Total de linhas
   - Linhas válidas
   - Linhas duplicadas
   - Linhas inválidas
   - Tabela com até 50 primeiros produtos válidos contendo: nome, código, custo, venda, margem, unidade, ativo, NCM, grupo e ID da categoria
   - Painel de categorias detectadas mostrando os grupos do arquivo e seu status (encontrado/não encontrado)
6. Após verificar o preview, clique no botão "Confirmar importação" para executar a importação efetiva dos produtos

## Funcionalidades
- Upload de arquivos XLS/XLSX
- Pré-visualização automática em tempo real
- Indicadores de status (total, válidos, duplicados, inválidos)
- Tabela de preview com até 50 registros contendo: nome, código, custo, venda, margem, unidade, ativo, NCM, grupo e ID da categoria
- Painel de categorias detectadas mostrando quais grupos do XLS já existem no sistema
- Validação de campos obrigatórios
- Formatação adequada de valores monetários
- Exibição de erros (se houver)

## Componentes
- `ProdutoImportPreview.vue`: Componente principal de upload e preview
- Integrado ao `PainelControle.vue`: Card e rota de acesso

## Observações
- O componente faz upload do arquivo para o backend e exibe o preview retornado pela API em `/api/produtos/import/preview`
- Requer autenticação JWT para funcionar
- Após o preview, é possível confirmar a importação real dos produtos através do botão "Confirmar importação"
- O endpoint de confirmação é `/api/produtos/import/confirm` e retorna um resumo da operação
- O campo "Grupo" do XLS é usado para criar ou reutilizar categorias automaticamente; quando vazio, usa a categoria padrão "Geral"
- O custo do XLS está sendo usado tanto em `precoCusto` quanto em `precoVenda` tanto no preview quanto na importação efetiva
- O painel de categorias detectadas permite identificar rapidamente quais grupos do arquivo já possuem categorias correspondentes no sistema

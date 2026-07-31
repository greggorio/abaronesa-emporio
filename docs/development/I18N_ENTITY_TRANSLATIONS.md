# Internacionalização de entidades no backend do ERP

Este documento descreve em detalhes como o ERP (bakery) suporta respostas de APIs com traduções para campos armazenados na base principal. Ele deve servir como guia sempre que usarmos o mesmo padrão para outras entidades ou endpoints.

## Contexto
- As tabelas principais (`categoria`, `produto`, `produto_sku`, etc.) armazenam os dados originais (português) e valores essenciais como preço, ordem, status.
- Foi necessário que certas informações (nome, descrição, variação, título, legenda) fossem exibidas em outros idiomas quando o `Accept-Language` for usado.
- A solução foi criar uma tabela dedicada (`entity_translation`) e um serviço que supre traduções ativas quando presentes, mantendo os dados originais como fallback.

## Fluxo de internacionalização
1. **Requisição com idioma**: o frontend envia o header `Accept-Language` (ex.: `Pt-BR`, `En-US`) no request. Isso já ocorria nos endpoints `/api/public/cardapio/v2` e agora também ocorre em `/api/public/cardapio` e demais APIs que precisam de textos traduzidos.
2. **Locale no Spring**: o Spring resolve esse header para uma instância de `java.util.Locale` disponível via `LocaleContextHolder.getLocale()` durante o request.
3. **TranslationService**:
   - Recebe `entityType` (ex: "PRODUCT"), `entityId`, `field` (ex: "nome"), `sourceText` e `Locale`.
   - Se o locale for PT*, retorna imediatamente o texto original (não traduz).
   - Caso contrário, busca na `entity_translation` uma linha ativa (`status` em OK ou MANUAL) com os mesmos `entityType/entityId/field/locale`.
   - Se encontrada e com texto válido, retorna `translatedText`; caso contrário, volta para `sourceText`.
   - Isso garante que não é necessário repetir lógica de tradução em cada serviço.
4. **CardapioService & outros serviços**:
   - Ao montar DTOs (`CardapioCategoriaDTO`, `CardapioProdutoDTO`, `CardapioCategoriaV2DTO`, `CardapioProdutoV2DTO`, `CardapioSkuDTO`, etc.) o serviço chama o `translationService.translate(...)` nos campos textuais (nome, descrição, variação, etc.).
   - A tradução ocorre antes de serializar a resposta, então a API devolve o texto correto com base no locale atual.
5. **Suporte ao fallback**:
   - Se não houver tradução ou o locale não for suportado, o texto original da entidade é retornado.
   - Isso permite manter o conteúdo no idioma original enquanto novas traduções são produzidas.

## Estrutura da tabela `entity_translation`
- `entity_type` (ex: "PRODUCT", "CATEGORY", "SKU").
- `entity_id` (FK para a entidade principal).
- `field` (texto especificando qual campo foi traduzido: "nome", "descricao", "variacao", etc.).
- `locale` (ex: `en-US`, `es-ES`).
- `source_text` e `source_hash` (texto original e hash, usados para detectar mudanças de base).
- `translated_text` (resultado final a ser usado pela API).
- `status` (enum `TranslationStatus` com valores `OK`, `MANUAL`, `PENDING`.<br>
  - O `TranslationService` só considera traduções com status `OK` ou `MANUAL` como válidas; `PENDING` indica que está aguardando revisão.
- `provider`, `createdAt`, `updatedAt` para auditoria.

## Mecanismos auxiliares
- `TranslationService.markSourceChanged(...)`: ao atualizar um entidade (categoria/produto/sku) ele recalcula hash e marca traduções existentes como `PENDING`, além de criar entradas `PENDING` para os `target-locales` configurados. Dessa forma, o time de tradução sabe que o texto fonte mudou e precisa ser reavaliado.
- `TranslationService.ensureAllTargetLocalesPresent()`: função utilizada em jobs para garantir que, ao adicionar um novo locale, todos os campos já tenham uma entrada com status `PENDING` pronta para ser preenchida.
- `EntityTranslationRepository.findOneActive(...)`: busca a tradução ativa com os filtros de tipo, campo, locale e status.

## Como aplicar para novas entidades
1. Identificar os campos textuais que precisam de tradução (ex.: `titulo`, `descricao`, `legend`).
2. Garantir que toda alteração nesses campos invoque `translationService.markSourceChanged(entityType, entityId, field, newValue)`.<br>
   - Isso é normalmente feito na camada de serviço (`ProdutoService`, `CategoriaService`, etc.).
3. Ao construir DTOs retornados por APIs públicas, chamar `translationService.translate(...)` com o locale atual (obtido de `LocaleContextHolder`).
4. Se necessário, adicionar traduções manualmente na tabela `entity_translation` (via admin ou scripts) com `status = OK` ou `MANUAL` e o `locale` desejado.

## Exemplos práticos

- No `/api/public/cardapio` os DTOs usam `translationService.translate(...)`, portanto o header `Accept-Language` controla qual texto sai na resposta.
- No `/api/public/cardapio/v2` o mesmo padrão já existia: SKUs, categorias e produtos chamam `translate(...)` no momento da montagem.
- O endpoint `/api/public/cardapio` carrega categorias/produtos e usa `LocaleContextHolder` para saber o locale atual (definido pelo header `Accept-Language`).
- Cada produto/categoria passa por `translationService.translate(...)` ao construir o DTO:
  - `CardapioProdutoDTO.nome` e `.descricao`
  - `CardapioCategoriaDTO.nome`
- Como o header `Accept-Language` está sendo propagado nos requests, qualquer cliente que forneça `en-US`, `es-ES` ou outro locale configurado recebe as strings traduzidas em vez das originais.

## Produção de traduções pendentes

- Há um job chamado `TRANSLATION_SYNC` (seed em `JobDefinitionSeeder`) registrado com cron `0 0 * * * *` e executado diariamente pela agenda gerenciada em `JobSchedulerService`.
- Durante cada execução (`TranslationJobService.processPendingTranslations()`):
  1. `translationService.ensureAllTargetLocalesPresent()` garante que existam linhas `PENDING` para todos os `translation.target-locales`, mesmo que o texto fonte já esteja traduzido em outro locale.
  2. O job itera sobre todas as traduções `PENDING` e chama `translateWithProvider()`.
     - Se o OpenAI estiver configurado (`OpenAiConfigService.isEnabled()`), usa o serviço real; caso contrário, marca o provider como `SIMULATION` e copia o texto fonte.
  3. Cada registro processado vira `TranslationStatus.OK` ou `FAILED` se houver erro.
  4. A execução grava o número de registros processados e loga falhas.
- Essa rotina permite manter os dados traduzidos para qualquer nova entidade ou texto alterado sem intervenção manual.

## Coordenando atualizações

1. Sempre que um campo que precisa de tradução for editado (nome/descrição/etc), invoque `TranslationService.markSourceChanged("ENTITY", id, field, newText);`.
2. Verifique qual `translation.target-locales` está configurado (atualmente `en-US,es-ES`) e modifique `application.properties` se precisar suportar mais idiomas.
3. A tradução é disparada automaticamente pelo job `TRANSLATION_SYNC`, portanto o workflow para adicionar novos campos traduzidos é:
   - Persistir o texto original no ERP.
   - Executar `markSourceChanged(...)`.
   - Esperar o job agendado ou rodar manualmente via `/jobs/{id}/run` para popular `translated_text`.

## Conclusão
Sempre que for necessário que uma API publique dados traduzíveis, basta:
1. Enviar o `Accept-Language` do cliente.
2. No backend, obter o locale via `LocaleContextHolder` e passar cada campo textual para `translationService.translate(...)`.
3. Garantir que as traduções estejam registradas na `entity_translation` com status `OK` ou `MANUAL`.

Esse padrão já está implementado no `/api/public/cardapio` (após a mudança), em `/api/public/cardapio/v2` e em outros serviços que usam o `translationService`. Podemos usar este documento como referência para qualquer nova entidade que precise desse comportamento.

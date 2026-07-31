# Plano de execucao - i18n MesaPage (Fase 1)

Este plano detalha a implementacao incremental de i18n para a rota `/m/{slug}`
no frontend do espresso, com infraestrutura preparada no backend via
`messages*.properties`. A Fase 1 nao altera respostas de API; foca apenas em
strings estaticas no `MesaPage.tsx`.

## Etapa 1 - Inventario e design das chaves i18n

Objetivo: mapear todas as strings estaticas do MesaPage e definir o padrao de
chaves, com foco inicial em toasts e validacoes.

Steps:
- Varredura do arquivo `espresso_front/src/pages/MesaPage.tsx` para listar
  strings estaticas por secao (toasts, dialogs, forms, buttons, aria, hints).
- Definir convencao de chaves (`mesa.*`, `mesa.toast.*`, `mesa.dialog.*`,
  `mesa.form.*`, `mesa.aria.*`) e placeholders para interpolacao.
- Criar uma lista validada de chaves para o Bloco 1 (toasts/validacoes).

Criterio de teste:
- Todas as mensagens do Bloco 1 possuem chave unica e mapeamento definido.

## Etapa 2 - Infra i18n do backend (espresso_back)

Objetivo: preparar o backend com `MessageSource`, `LocaleResolver` e
`messages*.properties` sem alterar respostas de API.

Steps:
- Criar `I18nConfig` com `MessageSource` e `LocaleResolver` (prioridade:
  `?lang=`, `Accept-Language`, fallback `pt-BR`).
- Adicionar `LocaleChangeInterceptor` e registrar no `WebMvcConfigurer`.
- Adicionar propriedades `spring.messages.*` no `application.properties`.
- Criar `messages.properties` e `messages_en_US.properties` com chaves de
  teste (nao usadas ainda).
- (Opcional) Criar `I18nService` para facilitar o uso futuro.

Criterio de teste:
- Resolver locale via `?lang=en-US` em ambiente local (log ou endpoint simples,
  sem mudar payloads atuais).

## Etapa 3 - Infra i18n local do frontend (MesaPage-only)

Objetivo: habilitar i18n no MesaPage sem afetar outras paginas.

Steps:
- Criar `espresso_front/src/i18n/mesa.pt-BR.json` e `mesa.en-US.json`.
- Criar `espresso_front/src/i18n/useMesaI18n.ts` com resolucao de idioma:
  `?lang=` -> localStorage por mesa (`mesa:lang:{slug}`) -> navegador -> `pt-BR`.
- Adicionar helpers de formatacao (moeda/data) usando o locale ativo.
- Aplicar um primeiro uso do `t(...)` no MesaPage para validar alternancia.

Criterio de teste:
- Ao acessar `/m/{slug}?lang=en-US`, pelo menos um texto do MesaPage muda.

## Etapa 4 - Bloco 1 MesaPage: toasts e validacoes

Objetivo: migrar mensagens de toast e validacoes para i18n.

Steps:
- Substituir textos de `toast(...)` por `t(...)`.
- Manter interpolacoes (ex.: slug e valores dinamicos) com placeholders.
- Cobrir validacoes de nome, CPF, telefone e fluxos de erro/sucesso.

Criterio de teste:
- Disparar toasts de validacao/erro em PT e EN e verificar textos.

## Etapa 5 - Bloco 2 MesaPage: dialogos

Objetivo: migrar titulos e descricoes dos dialogos para i18n.

Steps:
- Traduzir `DialogTitle` e `DialogDescription` de:
  - chamar garcom
  - confirmar pedido
  - compartilhar mesa
  - localizacao
  - sessao assistida
- Ajustar placeholders dentro dos dialogos.

Criterio de teste:
- Abrir cada dialogo em PT e EN e validar textos.

## Etapa 6 - Bloco 3 MesaPage: forms e placeholders

Objetivo: migrar labels, placeholders e textos auxiliares.

Steps:
- Traduzir placeholders e labels de inputs (nome, CPF, telefone, cartao).
- Traduzir mensagens de ajuda e textos auxiliares do formulario.

Criterio de teste:
- Navegar nos formularios e validar textos no idioma ativo.

## Etapa 7 - Bloco 4 MesaPage: botoes e CTAs

Objetivo: migrar textos de botoes e acoes principais.

Steps:
- Traduzir textos de botoes (ex.: confirmar, cancelar, enviar pedido).
- Ajustar CTAs em seccoes de pagamento e carrinho.

Criterio de teste:
- Testar fluxos de envio de pedido/pagamento com idioma ativo.

## Etapa 8 - Bloco 5 MesaPage: textos auxiliares e aria-labels

Objetivo: migrar headings, empty states e ARIA labels.

Steps:
- Traduzir headings, textos informativos e estados vazios.
- Traduzir `aria-label` e textos de acessibilidade.

Criterio de teste:
- Revisar visualmente e validar acessibilidade basica em PT e EN.

## Etapa 9 - Revisao final

Objetivo: garantir consistencia e cobertura total das strings estaticas do MesaPage.

Steps:
- Checar strings literais restantes no MesaPage e justificar remember/skip.
- Revisar consistencia das chaves e fallbacks.
- Registrar pendencias para a Fase 2 (traducoes via API e descricao de produtos).

Criterio de teste:
- MesaPage sem strings estaticas nao traduzidas (exceto conteudo de API).


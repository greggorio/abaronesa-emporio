# Eventos — Especificação do Domínio

## Definição

Módulo que governa a agenda de shows, happy hours e festas temáticas do estabelecimento. Responde a três perguntas operacionais: qual evento está agendado, quando e onde acontece, e qual a taxa de entrada.

O domínio canônico vive no `espresso_back` — entidade `Evento` com `EventoStatus` e `GeneroMusical` como enums, soft delete via `ativo=false`, validação de sobreposição de horário. O `backend` bakery consome via REST client (`EventoEspressoService`) para alimentar o dashboard de faturamento, a notificação pré-evento e do dia, e o cálculo de couvert artístico debitado em `Pagamento`.

## Escopo

**Inclui:**
- Criação, edição e exclusão lógica de eventos
- Campos: `titulo`, `descricao`, `dataEvento`, `dataHoraFim`, `preco`, `gratuito`, `banda`, `genero`, `imagemUrl`, `ativo`, `status`
- Listagem pública de eventos futuros e realizados
- Consulta de detalhes por identificador
- Listagem administrativa com paginação e busca (por título, banda ou gênero)
- Consulta para dashboard por período (`hoje`, `7d`, `30d`)
- Validação de datas e prevenção de conflito de horário
- Enums `EventoStatus` (`AGENDADO`, `REALIZADO`, `CANCELADO`) e `GeneroMusical` (`ROCK`, `METAL`, `ACUSTICO`, `SERTANEJO`, `MPB`, `BLUES`, `JAZZ`, `OUTRO`) como valores controlados
- Notificações pré-evento e do dia para convidados e equipe, disparadas pelo `backend` bakery
- Couvert artístico — o `preco` do evento ativo no instante do pagamento é materializado em `Pagamento.valorCouvert`

**Não inclui:**
- Gestão de reservas de mesas para eventos
- Integração com sistema de bilheteria externo
- Operação de fila, cozinha ou salão do evento (pertence a `consumo-digital/`)
- Engajamento ao vivo dentro do evento (pertence a `quiz/`)
- Pontuação, gamificação e recompensas (pertence a `fidelizacao/`)

## Modelo de dados

Entidade `Evento` (JPA, `espresso_back`, tabela `evento`):

| Campo | Tipo | Observação |
|-------|------|-----------|
| `id` | Long | PK auto-incremento (`BIGSERIAL`) |
| `titulo` | String(200) | obrigatório |
| `descricao` | TEXT | opcional |
| `dataEvento` | LocalDateTime | obrigatório — início do evento |
| `dataHoraFim` | LocalDateTime | opcional — fim do evento (default = `dataEvento` no conflito) |
| `preco` | BigDecimal(10,2) | valor de entrada (couvert) |
| `gratuito` | Boolean | default `false` — quando `true`, dispensa cobrança de couvert |
| `banda` | String(200) | opcional — atração principal |
| `genero` | GeneroMusical (enum) | obrigatório |
| `imagemUrl` | String(500) | banner / capa do evento |
| `ativo` | Boolean | default `true` — flag de soft delete |
| `status` | EventoStatus (enum) | default `AGENDADO` |
| `criadoEm` | LocalDateTime | `@PrePersist` |
| `atualizadoEm` | LocalDateTime | `@PreUpdate` |

### Enums

`EventoStatus`: `AGENDADO`, `REALIZADO`, `CANCELADO`.

`GeneroMusical`: `ROCK`, `METAL`, `ACUSTICO`, `SERTANEJO`, `MPB`, `BLUES`, `JAZZ`, `OUTRO`.

## Endpoints

Base: `/api/eventos` (`EventoController`).

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/proximos` | público | Próximos 4 eventos futuros ordenados por data |
| `GET` | `/realizados` | público | Eventos com `status=REALIZADO` |
| `GET` | `/dashboard?periodo=` | público | Eventos do período (`hoje`/`7d`/`30d`, default `30d`) |
| `GET` | `/{id}` | público | Detalhe por ID |
| `GET` | `?status=` | ADMIN/SYSTEM/FUNCIONARIO | Lista com filtro opcional por status |
| `GET` | `/admin?page=&size=&search=&status=` | ADMIN/SYSTEM/FUNCIONARIO | Lista paginada com busca (título, banda, gênero) |
| `POST` | `/` | ADMIN/SYSTEM | Criar — valida conflito de horário, retorna 201 |
| `PUT` | `/{id}` | ADMIN/SYSTEM | Atualizar — valida conflito de horário |
| `DELETE` | `/{id}` | ADMIN/SYSTEM | Soft delete (`ativo=false`), retorna 204 |

## Regras

- **Soft delete** — `DELETE` marca `ativo=false`; o registro nunca é removido fisicamente.
- **Conflito de horário** — `criar` e `atualizar` chamam `validarConflitoHorario`, que rejeita com 400 quando a data é inválida (ou `dataHoraFim` é anterior ao início) e com 409 quando há sobreposição com outro evento ativo. Implementado em `existsEventoSobreposto` no repository.
- **Couvert artístico** — o `preco` (em conjunto com `gratuito=false`) é o valor cobrado por ingresso quando há um evento ativo no momento do pagamento. O `backend` bakery consome o `preco` via `EventoEspressoService` e o materializa em `Pagamento.valorCouvert`. A captura é momentânea — alterações posteriores no evento não retroagem em pagamentos antigos.
- **Busca administrativa** — a query `searchEventos` faz `LIKE` em `titulo`, `banda` e `genero`; combina com filtro de `status` e paginação.
- **Status vs ativo** — `status` é a máquina de estado do ciclo de vida; `ativo` é a flag de soft delete. Os dois são ortogonais.
- **Visibilidade pública** — o app do cliente só vê eventos com `ativo=true` e `dataEvento` futura; a home distingue `proximos` (futuros) de `realizados` (`status=REALIZADO`).
- **`dataHoraFim`** — quando ausente, é tratado como igual a `dataEvento` na checagem de conflito. Quando presente e anterior ao início, a operação é rejeitada com 400.
- **Couvert não retroativo** — o `valorCouvert` no `Pagamento` é uma coluna própria; se o evento mudar de preço depois, pagamentos antigos preservam o valor original.
- **Flag `gratuito`** — quando `true`, impede a cobrança de couvert mesmo que `preco` esteja preenchido.
- **Local único** — a entidade não carrega `local` nem `capacidade`; a versão atual trata o local como sendo o próprio estabelecimento.

## Migrations

- `V1__baseline.sql` — cria a tabela `evento` com os campos básicos (`id`, `titulo`, `descricao`, `data_evento`, `banda`, `genero`, `imagem_url`, `ativo`, `status`, `criado_em`, `atualizado_em`) e os índices `idx_evento_ativo`, `idx_evento_data`, `idx_evento_status`.
- `V2__seed_data.sql` — insere o evento "Workshop para iniciantes" (id=1, banda "Banda SupraSumo", gênero ROCK).
- `V3__add_event_fields.sql` — adiciona `data_hora_fim`, `preco`, `gratuito`.

## Distribuição entre backends

O módulo atravessa dois backends, sem replicação de dados:

- **`espresso_back`** (Villa Custom, porta 8085) — fonte da verdade: entidade `Evento`, enums, repository, service e controller mapeado em `/api/eventos/**`. Endpoints públicos são `permitAll`; os administrativos são protegidos com `@PreAuthorize` para `ADMIN`, `SYSTEM` e `FUNCIONARIO`.
- **`backend` bakery** (porta 8080) — consumidor REST:
  - `EventoEspressoService` — cliente HTTP para `/api/eventos/proximos` e `/api/eventos/dashboard`. Fornece `buscarValorCouvertAtivo()` (filtra por janela temporal e `gratuito=false`) e `buscarDadosFaturamentoPorEvento(periodo)` (cruzando eventos do `espresso_back` com pagamentos locais).
  - `EventoDashboardController` — `GET /api/dashboard/eventos?periodo=hoje|7d|30d`, consumido pelo `frontend/PainelEventos.vue`.
  - `EventNotificationService` + `EventNotificationScheduler` — enviam push pré-evento e do dia via `EspressoNotificationClient`, com dedupe em `event_notification_log` por `(eventoId, ano, tipo)`.
  - `JobDefinitionSeeder` + `JobRegistry` — registram os cron jobs `EVENT_PRE` (todos os dias 09:10) e `EVENT_DAY` (todos os dias 09:15).
  - `AdminMesaController` — consome `buscarValorCouvertAtivo()` para materializar o couvert em `Pagamento.valorCouvert` nos endpoints `/pagamentos-couvert` e em splits de pagamento.

A base URL do `espresso_back` é resolvida em `ConfigManager` pela chave `espresso-api.app.base-url`, com fallback em `eventos.app.base-url` e na env `ESPRESSO_API_BASE_URL` (default `http://localhost:8085`).

## Integrações

| Módulo | Natureza |
|--------|----------|
| `clientes/area-do-cliente` | Leitura: `espresso_front/EventosCliente.tsx` consome `/api/eventos/proximos` para a tela `/areacliente/eventos` |
| `consumo-digital/pagamentos` | Escrita: `AdminMesaController` materializa o couvert em `Pagamento.valorCouvert` quando há evento ativo |
| `consumo-digital/mesa-digital` | Leitura: o couvert é cobrado dentro da sessão de mesa |
| `dashboard` | Leitura: `PainelEventos.vue` consome `/api/dashboard/eventos?periodo=` com faturamento real |
| `quiz` | Fronteira conceitual: quiz acontece dentro de um evento mas não governa a agenda |
| Notificações push | Envio broadcast pré-evento e do dia via `EspressoNotificationClient`, parametrizado por `site_evento_*` no `ConfigManager` |

## Decisões de domínio

- **Exclusão lógica** — `ativo=false`, nunca física.
- **Couvert é captura momentânea** — materializado no instante do pagamento; alterações posteriores no `preco` do evento não retroagem.
- **Fonte única de couvert** — o `preco` do evento é a única fonte; não há tabela local de couvert no ERP.
- **Gênero e status como enum** — `VARCHAR(50)` com `@Enumerated(EnumType.STRING)`, não campos livres.
- **Sem local/capacidade** — o estabelecimento é o único local; não há suporte a múltiplos locais ou limite de público.

## Status de implementação

**EM_DESENVOLVIMENTO**. Cadastro, listagem, paginação admin e validação de conflito de horário estão operacionais. Refinamentos de regras de negócio — consistência entre `status` e datas, tratamento de eventos `CANCELADO`, e cobertura de testes — continuam em evolução. Não há suíte de testes automatizados para o módulo.

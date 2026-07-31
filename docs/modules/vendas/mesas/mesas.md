# Mesas — Especificação

## Entidades

### Mesa (`Mesa.java`, 42 linhas)

Tabela `mesa`. Representa um ponto de consumo físico no salão.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `slug` | `String` | `unique`, ex: "mesa-01" |
| `rotulo` | `String` | Nome de exibição, ex: "Mesa 01" |
| `referencia` | `String` | Código auxiliar, ex: "A1" |
| `ativo` | `Boolean` | |
| `criadoEm` | `LocalDateTime` | |
| `atualizadoEm` | `LocalDateTime` | |

### SessaoMesa (`SessaoMesa.java`, 45 linhas)

Tabela `sessao_mesa`. Uma visita de consumo em uma mesa. Cada mesa pode ter apenas uma sessão `OPEN` por vez.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` | PK |
| `mesa` | `@ManyToOne(LAZY)` → `Mesa` | |
| `status` | `StatusSessao` | `OPEN` / `CLOSED` |
| `abertaEm` | `LocalDateTime` | |
| `fechadaEm` | `LocalDateTime` | |
| `observacoes` | `String` | |
| `selfCheckoutLiberado` | `Boolean` | Se cliente pode pagar sozinho |
| `selfCheckoutResolvidoEm` | `LocalDateTime` | |

### SessaoConvidado (`SessaoConvidado.java`, 53 linhas)

Tabela `sessao_convidado`. Um cliente participando de uma sessão.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` | PK |
| `sessaoMesa` | `@ManyToOne(LAZY)` → `SessaoMesa` | |
| `usuario` | `@ManyToOne(LAZY)` → `Usuario` | |
| `guestToken` | `String` | `unique`, UUID |
| `nomeExibicao` | `String` | Apelido na mesa |
| `deviceFingerprint` | `String` | Identificador do dispositivo |
| `entrouEm` | `LocalDateTime` | |
| `saiuEm` | `LocalDateTime` | |
| `host` | `Boolean` | É o anfitrião da mesa |

### SessaoCobranca (`SessaoCobranca.java`, 155 linhas)

Tabela `sessao_cobranca`. Cobranças automáticas incidentes sobre a sessão.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` | PK |
| `sessaoMesaId` | `Long` | |
| `sessaoConvidadoId` | `Long` | nullable (global ou por convidado) |
| `tipo` | `TipoCobranca` | `COUVERT_ARTISTICO` |
| `valor` | `BigDecimal` | |
| `eventoId` | `Long` | nullable, para eventos especiais |
| `isento` | `Boolean` | |
| `motivoIsencao` | `String` | |
| `status` | `StatusCobranca` | `ATIVA` / `CANCELADA` |

## Repositórios

### MesaRepository
- `findBySlug(String slug)`
- `existsBySlug(String slug)`

### SessaoMesaRepository
- `findFirstByMesaAndStatusOrderByAbertaEmDesc(Mesa, StatusSessao)` — última sessão de uma mesa
- `countByStatus(StatusSessao)` — total de sessões abertas

### SessaoConvidadoRepository
- `findByGuestToken(String token)`
- `findFirstByUsuario_IdAndSessaoMesa_Status(Long userId, StatusSessao status)` — sessão ativa do usuário
- `countBySessaoMesa_Id(Long sessaoMesaId)` — total de convidados na sessão

### SessaoCobrancaRepository
- `findBySessaoMesaIdAndStatus(Long, StatusCobranca)`
- `sumValorBySessaoMesaIdAndStatusAndNotIsento(Long, StatusCobranca)` — soma dos valores não isentos

## Serviços

### MesaService (87 linhas)

CRUD completo: `criar`, `editar`, `deletar`, `buscarPorId`, `listarOptions`, `atualizarReferencia`.

### SessaoMesaService (264 linhas)

| Método | Descrição |
|--------|-----------|
| `obterOuAbrirSessaoMesa(mesaSlug, usuario)` | Busca sessão OPEN da mesa ou cria nova |
| `criarConvidado(sessaoMesa, CriarConvidadoRequest)` | Gera guestToken UUID único, cria convidado na sessão |
| `fecharSessao(sessaoMesaId)` | Marca sessão como CLOSED, seta fechadaEm |
| `iniciarSessaoAssistida(mesaSlug, usuario)` | Staff abre sessão para cliente |
| `adicionarConvidadoExtra(sessaoMesaId, request)` | Staff adiciona convidado extra |
| `moverSessaoMesa(sessaoMesaId, novaMesaSlug)` | Transfere sessão entre mesas |
| `obterOuCriarSessaoEBalcaoGuest(usuario)` | Sessão especial para balcão expresso |

## Controllers

### MesaController (102 linhas) — `@RequestMapping("/api/mesas")`

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/mesas/list` | Lista paginada (BaseListController) |
| `GET` | `/api/mesas/form-config` | Config dinâmica |
| `GET` | `/api/mesas/options` | Dropdown (id, rotulo, referencia) |
| `GET` | `/api/mesas/{id}` | Buscar por ID |
| `POST` | `/api/mesas` | Criar |
| `PUT` | `/api/mesas/{id}` | Atualizar |
| `DELETE` | `/api/mesas/{id}` | Excluir |
| `PATCH` | `/api/mesas/{mesaSlug}/referencia` | Atualizar referência |

### SessaoMesaController (155 linhas)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/mesas/me/ativa` | Sessão ativa do usuário logado |
| `GET` | `/api/mesas/{mesaSlug}/sessao` | Verificar se mesa tem sessão ativa |
| `POST` | `/api/mesas/{mesaSlug}/convidados` | Criar convidado (entrada na mesa) |
| `POST` | `/api/mesas/sessoes/{sessaoMesaId}/fechar` | Fechar sessão |

## DTOs

| DTO | Campos |
|-----|--------|
| `MesaDTO` | `id, slug, rotulo, referencia, ativo, criadoEm, atualizadoEm` |
| `MesaRequest` | `slug, rotulo, referencia, ativo` |
| `MesaOptionDTO` | `value` (id), `label` (rotulo), `referencia` |
| `MesaAdminOptionDTO` | `id, slug, rotulo, referencia` |
| `CriarConvidadoRequest` | `nomeExibicao, userToken` |
| `CriarConvidadoResponse` | `sessaoConvidadoId, sessaoMesaId, guestToken, host` |

## Fluxo de entrada do cliente (QR code)

1. Cliente escaneia QR code da mesa → PWA carrega `MesaPage.tsx`
2. Frontend chama `GET /api/mesas/{mesaSlug}/sessao` para verificar sessão ativa
3. Se não há sessão ativa, o backend cria via `obterOuAbrirSessaoMesa`
4. Cliente informa nome → `POST /api/mesas/{mesaSlug}/convidados` cria `SessaoConvidado` com `guestToken` UUID
5. `guestToken` é armazenado no `localStorage` do dispositivo e enviado como header nas requisições seguintes
6. Outros convidados podem entrar na mesma mesa escaneando o mesmo QR code, gerando novos `guestToken`s

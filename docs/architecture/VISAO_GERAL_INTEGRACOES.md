# Visão Geral de Integrações — ERP ↔ Site ↔ App

> **Documento de Arquitetura** — Como as funcionalidades transversais conectam o ecossistema

---

## Visão Geral

O ecossistema Bakery é composto por **dois frontends** e **dois backends** que compartilham dados e funcionalidades:

### Frontends

| Frontend | Tecnologia | Propósito | Caminho |
|----------|------------|-----------|---------|
| **ERP Frontend** | Quasar/Vue 3 | Backoffice administrativo | `/frontend/` |
| **Digital Experience** | React + Capacitor | Site, app, mesa digital | `/espresso_front/` |

### Backends

| Backend | Tecnologia | Propósito | Caminho |
|---------|------------|-----------|---------|
| **ERP Backend** | Java/Spring | Núcleo transacional, cadastros | `/backend/` |
| **Digital Experience** | Java/Spring | Experiência do cliente, quiz | `/espresso_back/` |

### Diagrama Completo

```
┌─────────────────────────────────────────────────────────────────┐
│                    ERP (bakery/backend)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │  Gamificação│  │  Quiz (IA)  │  │   Eventos (híbrido)     │ │
│  │  (pontos,   │  │  (geração   │  │   (agenda, couvert,     │ │
│  │  rewards)   │  │  perguntas) │  │    notificações)        │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │  i18n/Trad. │  │   Cardápio  │  │      Clientes           │ │
│  │  (OpenAI,   │  │  (produtos, │  │   (OAuth2, perfil,      │ │
│  │  jobs)      │  │  categorias)│  │    histórico)           │ │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘ │
│  ┌─────────────┐  ┌─────────────┐                              │
│  │   Delivery  │  │   Temas     │                              │
│  │  (pedidos,  │  │  (tradução  │                              │
│  │  KDS, Uber) │  │  por tema)  │                              │
│  └─────────────┘  └─────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
           │                    │                    │
           │ JWT + APIs REST    │ JWT + APIs REST    │ JWT + APIs REST
           ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  ERP Frontend    │  │  Site (espresso) │  │ App (espresso)   │
│  (Quasar/Vue)    │  │  (React PWA)     │  │ (React+Capacitor)│
│  - Dashboard     │  │  - Hero          │  │  - Push notifs.  │
│  - Produção      │  │  - Quem Somos    │  │  - Área do cliente│
│  - Validade      │  │  - Eventos       │  │  - Delivery      │
│  - Pedidos Compra│  │  - Galeria       │  │  - Gamificação   │
│  - Admin         │  │  - Quiz (ao vivo)│  │  - Quiz          │
│  - Mobile        │  │  - i18n (PT/EN/ES│  │  - i18n          │
└──────────────────┘  └──────────────────┘  └──────────────────┘
           │
           ▼
┌──────────────────┐
│  Mesa Digital    │
│  (React PWA)     │
│  - QR Code       │
│  - Cardápio      │
│  - Self-checkout │
│  - Pagamento     │
│  - i18n          │
└──────────────────┘
```

---

## Princípios de Integração

### 1. **ERP como Fonte da Verdade**

| Domínio | Fonte da Verdade | Serviços ERP | Serviços espresso_back |
|---------|------------------|--------------|------------------------|
| Produtos e Cardápio | `bakery/backend` (tabela `produtos`) | `CardapioService`, `CategoriaService` | `CategoryService` |
| Clientes e Usuários | `bakery/backend` (tabela `clientes`, `usuarios`) | `ClienteService`, `AuthenticationService` | `ClienteRefService` |
| Gamificação (pontos, rewards) | `bakery/backend` (tabela `movimento_pontos`, `rewards`) | `GamificacaoService`, `DashboardGamificacaoService` | `RewardService` |
| Quiz | **Híbrido** | `QuizGenerationService` (IA) | `QuizSessionService`, `QuizGameService`, `QuestionService` |
| Eventos | **Híbrido** | `EventoEspressoService` | `EventoService` |
| Temas (cores, fontes) | `espresso_back` (tabela `themes`) | `ThemeTranslationMapper` | `ThemeService`, `ThemeNotificationService` |
| Internacionalização (i18n) | **Híbrido** | `TranslationService`, `TranslationJobService` | `I18nService` |

### 2. **Autenticação Compartilhada (JWT)**

- Login ocorre no ERP (`/api/auth/login`)
- JWT é compartilhado entre ERP e Site/App
- Header: `Authorization: Bearer <TOKEN>`
- User ID: `X-User-ID` header

**Documento relacionado:** `docs/api-reference/authentication/jwt.md`

### 3. **APIs REST como Contrato**

- ERP expõe APIs para Site/App consumir
- Site/App **não acessa banco do ERP diretamente**
- Contratos documentados em `docs/api-reference/endpoints/`

---

## Funcionalidades Transversais

### 1. Gamificação

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - Configurar regras (pontos por real)<br>- Cadastrar recompensas<br>- Ver relatórios de resgates |
| **Site/App (Experiência)** | - Exibir saldo de pontos<br>- Mostrar recompensas disponíveis<br>- Permitir resgate |

**Fluxo:**

```
1. Cliente faz pedido no Delivery (App)
   ↓
2. ERP calcula pontos (GamificacaoService)
   ↓
3. ERP grava em movimento_pontos
   ↓
4. App consulta /api/gamificacao/saldo
   ↓
5. Cliente vê pontos atualizados
```

**Documentos relacionados:**
- `docs/modules/fidelizacao/gamificacao/README.md`

---

### 2. Quiz Ao Vivo

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - Importar perguntas (CSV/JSON)<br>- **Gerar perguntas com IA (`QuizGenerationService`)**<br>- Categorizar perguntas |
| **espresso_back** | - **Gerenciar sessões ao vivo (`QuizSessionService`)**<br>- **Lógica do jogo (`QuizGameService`)**<br>- Banco de perguntas (`QuestionService`) |
| **Site (Experiência)** | - Página do Quiz (ao vivo)<br>- Ranking em tempo real<br>- WebSocket para sincronização |

**Fluxo:**

```
1. Admin importa perguntas via ERP (ou gera com IA)
   ↓
2. ERP: QuizGenerationService cria perguntas via OpenAI
   ↓
3. Perguntas salvas em espresso_back (QuestionService)
   ↓
4. Admin cria sessão no Site (/quiz/admin)
   ↓
5. espresso_back: QuizSessionService gerencia sessão
   ↓
6. Clientes entram via QR Code
   ↓
7. WebSocket sincroniza perguntas/respostas (QuizGameService)
   ↓
8. Ranking final exibido no Site
```

**Documentos relacionados:**
- `docs/modules/quiz/README.md`

---

### 3. Eventos

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - Cadastrar eventos (`EventoEspressoService`)<br>- Definir couvert artístico<br>- Agendar notificações<br>- Integração com gamificação |
| **espresso_back** | - Exibir eventos no site (`EventoService`)<br>- Página de eventos |
| **Site (Experiência)** | - Página de Eventos<br>- Exibir couvert na conta<br>- Link para reservas |

**Fluxo:**

```
1. Admin cadastra evento no ERP
   ↓
2. ERP: EventoEspressoService salva evento (com valor_couvert)
   ↓
3. espresso_back: EventoService sincroniza eventos
   ↓
4. Site consulta /api/eventos
   ↓
5. Cliente vê eventos na página
   ↓
6. No dia, ERP lança couvert nas contas ativas (via EventoEspressoService)
```

**Documento relacionado:** `docs/modules/eventos/README.md`

---

### 4. Internacionalização (i18n)

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - **`TranslationService`**: Tradução de entidades (produtos, categorias)<br>- **`TranslationJobService`**: Job agendado que chama OpenAI para traduções pendentes<br>- **`ThemeTranslationMapper`**: Mapeamento de traduções por tema |
| **espresso_back** | - **`I18nService`**: Consumo de traduções para UI<br>- Tradução em tempo real baseada no `Accept-Language` |
| **Site/App (Experiência)** | - Exibir cardápio/traduzido (PT/EN/ES)<br>- Seletor de idioma na UI |

**Fluxo:**

```
1. Produto é cadastrado/atualizado no ERP
   ↓
2. ERP: TranslationService.markSourceChanged() detecta mudança
   ↓
3. Cria registros PENDING em entity_translation para en-US, es-ES
   ↓
4. Job agendado (TranslationJobService) processa pendentes
   ↓
5. Chama OpenAI API para traduzir texto
   ↓
6. Tradução salva com status OK/MANUAL
   ↓
7. Site/App consulta /api/public/cardapio com Accept-Language: en-US
   ↓
8. espresso_back: I18nService retorna texto traduzido
   ↓
9. Cliente vê cardápio em inglês
```

**Tabela de Dados:**

```sql
-- entity_translation (ERP)
entity_type     | entity_id | field       | locale  | source_text      | translated_text | status
----------------|-----------|-------------|---------|------------------|-----------------|--------
PRODUCT         | 123       | nome        | en-US   | Frango a Passarinho | Chicken Wings | OK
PRODUCT         | 123       | descricao   | en-US   | Porção de frango... | Crispy chicken... | OK
CATEGORY        | 45        | nome        | es-ES   | Petiscos          | Bocadillos | OK
```

**Documentos relacionados:**
- `docs/development/I18N_ENTITY_TRANSLATIONS.md`
- `docs/development/PLANO_I18N_MESA_FASE1.md`
- `docs/development/DELIVERY_I18N_INVENTORY.md`

---

### 5. Temas White-Label

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - `ThemeTranslationMapper`: Mapeia traduções por tema<br>- `ThemeTranslationSeedService`: Seed de temas |
| **espresso_back** | - `ThemeService`: Gerencia temas (cores, fontes, assets)<br>- `ThemeNotificationService`: Notifica mudanças |
| **Site/App (Experiência)** | - Configurador de temas<br>- Upload de assets<br>- Agendamento sazonal |

**Fluxo:**

```
1. Admin configura tema no Site
   ↓
2. espresso_back: ThemeService salva tema (cores, fontes, assets)
   ↓
3. Site aplica tema dinamicamente (CSS variables)
   ↓
4. espresso_back: ThemeNotificationService notifica mudança
   ↓
5. App Factory gera APK com tema (Capacitor)
```

**Documento relacionado:** `docs/modules/temas/README.md`

---

### 6. Cardápio

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - `CardapioService`: Cardápio completo<br>- `CategoriaService`: Categorias<br>- Controlar disponibilidade |
| **espresso_back** | - `CategoryService`: Sincronização de categorias |
| **Site/App/Mesa (Experiência)** | - Exibir cardápio<br>- Permitir pedidos<br>- Traduzir (i18n) |

**Fluxo:**

```
1. Admin cadastra produto no ERP
   ↓
2. ERP: CardapioService salva produto, categoria
   ↓
3. Site/App consulta /api/public/cardapio
   ↓
4. Cliente vê cardápio (com i18n se disponível)
```

**Documentos relacionados:**
- `docs/modules/produtos/README.md`
- `docs/development/I18N_ENTITY_TRANSLATIONS.md`

---

### 7. Delivery

| Camada | Responsabilidade |
|--------|------------------|
| **ERP (Backoffice)** | - `DeliveryOrderService`: Pedidos de delivery<br>- `DeliveryKdsService`: KDS para delivery<br>- `DeliveryUberStatusService`: Status Uber |
| **espresso_back** | - Integração com frontend de delivery |
| **Site/App (Experiência)** | - Cardápio de delivery<br>- Carrinho e checkout<br>- Rastreamento de pedido |

**Fluxo:**

```
1. Cliente faz pedido no delivery (App/Site)
   ↓
2. espresso_back: Cria pedido no ERP via API
   ↓
3. ERP: DeliveryOrderService processa pedido
   ↓
4. ERP: DeliveryKdsService envia para KDS
   ↓
5. Cozinha prepara pedido
   ↓
6. Uber é acionado (se entrega)
   ↓
7. Cliente acompanha status em tempo real
```

**Documentos relacionados:**
- `docs/modules/consumo-digital/delivery/README.md`
- `docs/integrations/uber-direct/uber_webhooks_sandbox.md`

---

## Contratos de API Principais

### Autenticação

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "cliente@exemplo.com",
  "password": "senha123"
}

← 200 OK
{
  "accessToken": "eyJhbGc...",
  "user": { "id": 123, "email": "..." }
}
```

### Gamificação

```http
GET /api/gamificacao/saldo
Authorization: Bearer <TOKEN>

← 200 OK
{
  "pontos": 1250,
  "proximaRecompensa": {
    "nome": "Drink Grátis",
    "pontosNecessarios": 2000,
    "faltam": 750
  }
}
```

### Cardápio

```http
GET /api/public/cardapio
Accept-Language: en-US

← 200 OK
[
  {
    "id": 1,
    "nome": "Frango a Passarinho",
    "nomeTraduzido": "Chicken Wings",
    "preco": 29.90,
    "categoria": "Petiscos"
  }
]
```

### Eventos

```http
GET /api/eventos/proximos

← 200 OK
[
  {
    "id": 42,
    "nome": "Noite do Rock",
    "data": "2026-04-15",
    "couvertArtistico": 15.00,
    "temCover": true
  }
]
```

---

## Webhooks e Eventos em Tempo Real

### WebSocket (Quiz Ao Vivo)

```
Site (espresso_front) ↔ espresso_back (WebSocket)
├── /ws/quiz/{sessionId}
│   ├── Mensagem: pergunta
│   ├── Mensagem: ranking
│   └── Mensagem: resultado
```

### Webhooks (Pagamentos)

```
Gateway (MercadoPago) → ERP (bakery/backend)
└── POST /api/webhooks/mercadopago
    ├── Evento: payment.updated
    └── Ação: atualizar status do pedido
```

---

## Segurança e Permissões

### Níveis de Acesso

| Papel | ERP | Site | App |
|-------|-----|------|-----|
| **Admin** | Acesso total | Configurar temas | N/A |
| **Garçom** | Pedidos, mesas | N/A | N/A |
| **Cliente** | N/A | Ver cardápio, quiz | Área do cliente, delivery |

### Validação de Token

```java
// espresso_back: JwtAuthenticationFilter
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    String token = extractToken(request);
    if (token != null) {
        Claims claims = parseToken(token);  // Valida com chave compartilhada
        Long userId = claims.get("userId", Long.class);
        // CustomUserPrincipal criado com userId
    }
}
```

**Chave JWT compartilhada:** `integration.system-token-secret`

---

## Monitoramento e Observabilidade

### Logs de Integração

| Sistema | Onde Logar |
|---------|------------|
| ERP → Site | `backend/logs/integracao-site.log` |
| Site → ERP | `espresso_back/logs/erp-api.log` |
| Webhooks | `backend/logs/webhooks.log` |

### Métricas Importantes

- Latência de APIs ERP → Site (< 200ms p95)
- Taxa de erro de autenticação (< 1%)
- Tempo de resposta do WebSocket (< 50ms)

---

## Troubleshooting

### Problema: Token JWT inválido no Site

**Causas possíveis:**
1. Chave JWT diferente entre ERP e Site
2. Token expirado
3. Clock skew entre servidores

**Solução:**
```bash
# Verificar chave JWT
grep integration.system-token-secret backend/src/main/resources/application.properties
grep integration.system-token-secret espresso_back/src/main/resources/application.properties

# Devem ser idênticas
```

### Problema: Pontos de gamificação não aparecem

**Causas possíveis:**
1. `GamificacaoService` não está sendo chamado
2. API `/api/gamificacao/saldo` com erro
3. Token sem `X-User-ID` header

**Solução:**
```bash
# Verificar logs do ERP
tail -f backend/logs/gamificacao.log

# Testar API
curl -H "Authorization: Bearer <TOKEN>" \
     -H "X-User-ID: 123" \
     http://localhost:8080/api/gamificacao/saldo
```

---

## Documentos Relacionados

| Documento | Descrição |
|-----------|-----------|
| `docs/modules/fidelizacao/gamificacao/README.md` | Submodulo de gamificacao |
| `docs/modules/quiz/` | Módulo de quiz |
| `docs/modules/eventos/` | Módulo de eventos |
| `docs/modules/temas/` | Módulo de temas white-label |
| `docs/integrations/push-notifications/README.md` | Push notifications |
| `docs/api-reference/authentication/` | Autenticação e JWT |
| `docs/development/I18N_ENTITY_TRANSLATIONS.md` | Internacionalização |

---

**Última atualização**: Março 2026

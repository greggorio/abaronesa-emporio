# MesaPage i18n — Etapa 1: inventário

Este documento registra o inventário inicial de strings estáticas que aparecem em `espresso_front/src/pages/MesaPage.tsx` e formaliza o padrão de chaves para a implantação do i18n na Fase 1 (toasts e validações). O foco do Bloco 1 são as mensagens que o guest vê ao abrir a mesa, validar dados ou lidar com falhas.

## 1. Convenção de chaves
- Prefixo geral: `mesa.*`.
- Toasts: `mesa.toast.<domínio>.<evento>.(title|description)` (ex.: `mesa.toast.sessionAlreadyOpen.description`).
- Diálogos, formulários, labels e aria seguem `mesa.dialog.*`, `mesa.form.*`, `mesa.aria.*` quando chegar a vez.
- Placeholders devem usar `{{...}}`; os valores dinâmicos esperados neste bloco são `{{mesaSlugAnterior}}`, `{{novaMesaSlug}}`, `{{errorMessage}}`, `{{pago}}` e `{{restante}}`.

## 2. Inventário do Bloco 1 — Toasts e validações
### 2.1 Toasts
#### Localização
| Chave | Campo | Texto atual | Observações |
| --- | --- | --- | --- |
| `mesa.toast.location.prompt.title` | title | `Informe sua posição` | disparado antes de salvar `locationReference`.
| `mesa.toast.location.prompt.description` | description | `Conte onde você está para registrarmos a referência.` | |
| `mesa.toast.location.saved.title` | title | `Referência salva` | confirma sucesso.
| `mesa.toast.location.saved.description` | description | `Sua localização foi registrada com sucesso.` | |
| `mesa.toast.location.saveError.title` | title | `Erro ao salvar` | |
| `mesa.toast.location.saveError.description` | description | `{{errorMessage}}` (fallback: `Não foi possível salvar a referência. Tente novamente.`) | valor vem de `error?.response?.data?.error?.message`.

#### Sessão / mesa
| Chave | Campo | Texto atual | Observações |
| --- | --- | --- | --- |
| `mesa.toast.sessionAlreadyOpen.title` | title | `Sessão já aberta` | usada em dois fluxos (nova sessão x reabertura).
| `mesa.toast.sessionAlreadyOpen.description` | description | `Você já está participando da mesa {{mesaSlugAnterior}}. Encerre ou volte para ela antes de abrir outra.` | `findOtherActiveGuestSlug()` injeta slug.
| `mesa.toast.sessionCheckFail.title` | title | `Aviso` | quando não dá para verificar a sessão.
| `mesa.toast.sessionCheckFail.description` | description | `Não foi possível verificar a sessão da mesa. Tente novamente.` | |
| `mesa.toast.sessionClosed.title` | title | `Mesa encerrada` | aparece em dois pontos ao sair da sessão.
| `mesa.toast.sessionClosed.description` | description | `Esta sessão foi encerrada. Inicie uma nova para continuar.` | |
| `mesa.toast.mesaMoved.title` | title | `Mesa movida` | boleta de redirecionamento.
| `mesa.toast.mesaMoved.description` | description | `Sua mesa agora é {{novaMesaSlug}}. Vamos redirecionar.` | |
| `mesa.toast.genericError.title` | title | `Erro` | usado em `callToastError`, o description vem da API.

#### Pagamento e checkout
| Chave | Campo | Texto atual | Observações |
| --- | --- | --- | --- |
| `mesa.toast.payment.failed.title` | title | `Pagamento não concluído` | |
| `mesa.toast.payment.failed.description` | description | `Não foi possível confirmar o pagamento.` | |
| `mesa.toast.payment.success.title` | title | `💰 Pagamento registrado` | |
| `mesa.toast.payment.success.description` | description | `Pago: R$ {{pago}} | Restante: R$ {{restante}}` | os valores vêm de `totalPago` e `devidoRestante`.
| `mesa.toast.checkoutReady.title` | title | `Pagamento liberado` | |
| `mesa.toast.checkoutReady.description` | description | `Você já pode pagar pelo app.` | |
| `mesa.toast.checkout.requested.title` | title | `Fechamento solicitado` | |
| `mesa.toast.checkout.requested.description` | description | `O garçom foi avisado. Aguarde a liberação do pagamento.` | |
| `mesa.toast.checkout.requestError.title` | title | `Erro` | |
| `mesa.toast.checkout.requestError.description` | description | `Não foi possível solicitar o fechamento. Tente novamente.` | |

#### Pedidos e carrinho
| Chave | Campo | Texto atual | Observações |
| --- | --- | --- | --- |
| `mesa.toast.identify.title` | title | `Entre na mesa` | fluxos antes de enviar pedido.
| `mesa.toast.identify.description` | description | `Você precisa se identificar para enviar pedidos.` | |
| `mesa.toast.emptyCart.title` | title | `Carrinho vazio` | |
| `mesa.toast.emptyCart.description` | description | `Adicione itens antes de enviar seu pedido.` | |
| `mesa.toast.order.sent.title` | title | `Pedido enviado` | |
| `mesa.toast.order.sent.description` | description | `Seu pedido foi enviado para a cozinha.` | |
| `mesa.toast.order.sendError.title` | title | `Erro ao enviar` | |
| `mesa.toast.order.sendError.description` | description | `{{errorMessage}}` (fallback: `Não foi possível enviar o pedido. Tente novamente.`) | |
| `mesa.toast.order.itemCanceled.title` | title | `❌ Item cancelado` | |
| `mesa.toast.order.itemCanceled.description` | description | `Um item do seu pedido foi cancelado. Entre em contato com o garçom para mais informações.` | |
| `mesa.toast.login.failure.title` | title | `Não foi possível entrar` | |
| `mesa.toast.login.failure.description` | description | `{{errorMessage}}` (fallback: `Falha ao criar convidado`) | gera o `errMsg` do cadastro.

#### Chamado de garçom e compartilhamento
| Chave | Campo | Texto atual | Observações |
| --- | --- | --- | --- |
| `mesa.toast.waiter.called.title` | title | `🔔 Garçom chamado` | |
| `mesa.toast.waiter.called.description` | description | `Um garçom será atendido em breve.` | |
| `mesa.toast.waiter.callError.title` | title | `Erro` | |
| `mesa.toast.waiter.callError.description` | description | `Não foi possível chamar o garçom. Tente novamente.` | |
| `mesa.toast.share.linkCopied.title` | title | `Link copiado!` | |
| `mesa.toast.share.linkCopied.description` | description | `O link da mesa foi copiado para a área de transferência.` | |

### 2.2 Validações específicas
- `mesa.toast.validation.title`: `Validação` (compartilhado por todos os toasts abaixo).
- `mesa.toast.validation.name.description`: `Informe seu nome (mínimo 2 caracteres) para continuar.`
- `mesa.toast.validation.cpf.description`: `CPF inválido. Por favor, digite um CPF válido.`
- `mesa.toast.validation.phone.description`: `Telefone inválido. Por favor, digite um telefone válido (mínimo 5 dígitos).`
- `mesa.toast.validation.pending.description`: `Preencha corretamente o CPF e/ou telefone antes de continuar.` (usado antes de `handleGoogleLoginOnMesa`).

## 3. Próximos blocos e strings restantes
- `DialogTitle`/`DialogDescription`: chamam garçom, confirmar pedido, compartilhar mesa, localização, sessão assistida; serão tratados na Etapa 5.
- Formulários e placeholders (nome, CPF, telefone, cartão) ainda têm label/hint literais; entram no Bloco 3.
- Botões (`confirmar`, `cancelar`, `enviar pedido`) estão próximos ao Bloco 4.
- Textos auxiliares, headings e `aria-label` serão avaliados no Bloco 5.

## 4. Validação da etapa
- Bloco 1 mapeado com chaves únicas e placeholders definidos acima.
- Listas de chaves foram organizadas por domínio e já podem ser usadas para criar os arquivos `mesa.pt-BR.json` / `mesa.en-US.json` na Etapa 3.

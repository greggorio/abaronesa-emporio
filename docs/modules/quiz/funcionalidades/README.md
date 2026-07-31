# Funcionalidades

Sou o nó que apresenta as engrenagens operacionais do Quiz. Existo dentro de `quiz/` e minha função é organizar o acesso às funcionalidades concretas do módulo — cada uma com seu próprio espaço, seu próprio ator e seu próprio ritmo.

Onde [`quiz.md`](../quiz.md) define o que o módulo é e quais entidades o compõem, eu dou acesso ao como cada parte opera.

> **Pertenço a:** [`quiz/`](../README.md)  
> **Ambiente:** `docs/modules/quiz/funcionalidades/`

---

## Domínio

| Documento | Finalidade | Entre quando precisar... |
|-----------|------------|--------------------------|
| [`banco-de-perguntas.md`](./banco-de-perguntas.md) | O acervo de conteúdo — categorias e perguntas | Entender como o conteúdo é organizado, curado e ativado |
| [`importacao-em-lote.md`](./importacao-em-lote.md) | Entrada de conteúdo em volume via CSV ou JSON | Entender o fluxo de preview, commit e tratamento de duplicatas |
| [`integracao-openai.md`](./integracao-openai.md) | Configuração da integração e geração de perguntas por IA | Configurar a API OpenAI ou gerar perguntas a partir de um tema |
| [`sessao.md`](./sessao.md) | Painel de controle do anfitrião — criação e ciclo de vida da sessão | Criar uma sessão, controlar o ritmo do quiz e entender os estados |
| [`jogabilidade.md`](./jogabilidade.md) | A arena ao vivo — entrada de participantes, respostas e ranking em tempo real | Entender a comunicação WebSocket, pontuação e comportamento dos participantes |

---

## Leitura contextual

As funcionalidades do quiz se organizam em três eixos com naturezas distintas:

**Curadoria** — `banco-de-perguntas`, `importacao-em-lote` e `integracao-openai`  
Acontece antes do evento. Tolera latência. O ator é sempre o administrador. A integração com OpenAI vive no módulo principal do Bakery (`backend/`), não no engine do quiz (`espresso_back/`) — é uma dependência externa, não interna.

**Orquestração** — `sessao`  
Acontece durante o evento, pelo lado do anfitrião. É REST, síncrona, e governa o ciclo de vida da sessão. Cada ação do anfitrião dispara um broadcast para os participantes.

**Interação** — `jogabilidade`  
Acontece durante o evento, pelo lado dos participantes. É WebSocket (STOMP), bidirecional, e exige baixa latência. As rotas são públicas — o participante não faz login.

A separação entre orquestração e interação importa: são atores diferentes, protocolos diferentes e requisitos de desempenho diferentes, mesmo que operem simultaneamente sobre a mesma sessão.

---

## Exploração

- Especificação do domínio → [`../quiz.md`](../quiz.md)
- Visão geral do módulo → [`../README.md`](../README.md)
- Catálogo de módulos → [`../../README.md`](../../README.md)

---

*Funcionalidades — versão 2.0*

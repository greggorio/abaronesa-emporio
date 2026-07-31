# Jogabilidade

Sou a arena ao vivo. Existo durante a sessão ativa — do momento em que o primeiro participante escaneia o QR Code até a última resposta ser processada. Aqui o quiz acontece em tempo real: entradas, perguntas, respostas, e o ranking que muda a cada rodada.

> **Pertenço a:** [`funcionalidades/`](./README.md)

---

## O que acontece aqui

O participante escaneia o QR Code e escolhe um apelido. A entrada é confirmada por canal individual; os demais recebem uma notificação de que alguém chegou.

Quando o anfitrião inicia o quiz, a primeira pergunta chega via WebSocket para todos. Cada participante tem o tempo configurado para responder. Quem acerta **primeiro** ganha os pontos — os demais recebem zero naquela rodada. Após o primeiro acerto, todos recebem o evento `question-locked` com o nome do vencedor e a opção correta revelada.

O ranking é recalculado e transmitido para todos após cada resposta processada. A pontuação considera a velocidade: responder em menos de cinco segundos vale mais; responder tarde demais penaliza levemente.

Quem entra com o quiz já em andamento não fica com a tela vazia — o sistema reemite a pergunta atual e o leaderboard imediatamente ao conectar.

---

## Leitura contextual

As rotas de jogo são **públicas** — o participante não faz login. O código de sessão de oito caracteres é a única barreira de acesso. Isso é intencional: o cliente do bar não tem conta no sistema.

Apelidos são únicos por sessão — o segundo participante a tentar um nickname já em uso recebe erro imediatamente.

O participante é identificado internamente pelo `connectionId` (ID da sessão WebSocket), não pelo apelido. Ao desconectar, o campo `active` do jogador passa para `false` — ele sai do ranking, mas seus dados persistem na sessão.

O registro de vencedor por pergunta é mantido em memória (`ConcurrentHashMap` em `QuizGameService`). Reiniciar o servidor durante uma sessão ativa apaga esse estado — as perguntas passam a aceitar respostas novamente como se ninguém tivesse acertado.

---

## 1. Protocolo WebSocket

| Parâmetro | Valor |
|-----------|-------|
| Endpoint | `/ws` |
| Fallback | SockJS habilitado |
| Prefixo de destino (cliente → servidor) | `/app` |
| Prefixo de tópico (broadcast) | `/topic` |
| Prefixo de fila (individual) | `/queue` |

---

## 2. Entrada na sessão

**Destino:** `/app/quiz/join`  
**Payload:** `{ "nickname": "...", "sessionCode": "..." }`  
**Resposta individual:** `/queue/quiz/join-response` → `PlayerResponse`

| Campo da resposta | Descrição |
|-------------------|-----------|
| `id` | ID do jogador criado |
| `nickname` | Apelido confirmado |
| `score` | Pontuação inicial (0) |
| `correctAnswers` | Acertos iniciais (0) |
| `wrongAnswers` | Erros iniciais (0) |

Se a sessão já estiver `ACTIVE`, o sistema reemite a pergunta atual e o leaderboard para o recém-chegado.

---

## 3. Submissão de resposta

**Destino:** `/app/quiz/answer`  
**Payload:** `{ "questionId": ..., "selectedOption": ..., "responseTimeMs": ... }`  
**Resposta individual:** `/queue/quiz/answer-result` → `AnswerResult`

| Campo da resposta | Descrição |
|-------------------|-----------|
| `correct` | Se a opção enviada é a correta |
| `correctOption` | Índice da opção correta |
| `pointsEarned` | Pontos ganhos nesta rodada |
| `totalScore` | Pontuação acumulada do jogador |
| `rank` | Posição atual no ranking |

Se a pergunta já tiver um vencedor, `correct` retorna `false` e `pointsEarned` retorna `0` independentemente da opção enviada.

---

## 4. Broadcasts

Todos os broadcasts usam o canal `/topic/quiz/{sessionCode}/...`:

| Canal | Conteúdo | Quando |
|-------|----------|--------|
| `/start` | `{ "status": "STARTED" }` | Anfitrião inicia o quiz |
| `/question` | `QuestionBroadcast` — texto, opções, número, total, tempo limite | Nova pergunta distribuída |
| `/question-locked` | Nickname do vencedor, pontos, opção correta | Alguém acerta (ou timeout revela) |
| `/leaderboard` | Ranking completo com posições | Após cada resposta processada |
| `/player-joined` | `{ "nickname": "..." }` | Novo participante entra |
| `/end` | Ranking final completo | Sessão encerrada |

---

## 5. Pontuação

Apenas o primeiro a acertar pontua por rodada. A pontuação base da pergunta (`points`, padrão 10) recebe bônus ou penalidade por velocidade:

| Tempo de resposta | Pontuação final |
|-------------------|-----------------|
| < 5 segundos | `points + 5` |
| < 10 segundos | `points + 3` |
| < 20 segundos | `points` |
| ≥ 20 segundos | `max(points − 2, 1)` |

O ranking é ordenado por `score` decrescente, apenas entre jogadores com `active = true`. Empates não possuem desempate declarado — a posição reflete a ordem de processamento.

**Código:** `QuizWebSocketController.java`, `QuizGameService.java`, `QuizNotificationService.java`, `WebSocketConfig.java`, `Player.java`

---

## Exploração

- Como o anfitrião controla a sessão → [`sessao.md`](./sessao.md)
- Visão geral das funcionalidades → [`README.md`](./README.md)

---

*Jogabilidade — versão 1.0*

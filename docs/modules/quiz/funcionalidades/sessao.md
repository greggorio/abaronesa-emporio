# Sessão

Sou o painel de controle do anfitrião. Existo para que uma sessão de quiz possa ser criada, preparada, conduzida e encerrada — do QR Code de entrada ao ranking final. Cada ação administrativa durante o evento passa por aqui.

> **Pertenço a:** [`funcionalidades/`](./README.md)

---

## O que acontece aqui

Tudo começa com a criação: o anfitrião define quantas perguntas quer, de qual categoria (opcional), com quanto tempo por questão, e se o quiz deve avançar automaticamente quando o tempo esgota. O sistema sorteia as perguntas, gera um código único de oito caracteres e um QR Code — e a sessão nasce em estado de espera.

A partir daí, o anfitrião controla o ritmo. Inicia o quiz, que dispara a primeira pergunta para todos os participantes conectados. Avança manualmente entre perguntas ou deixa o auto-advance trabalhar. Quando a última pergunta é respondida ou encerrada, a sessão passa a finalizada e o ranking final é transmitido.

Se o tempo de uma pergunta esgotar sem que ninguém acerte, o anfitrião pode revelar a resposta correta — sem vencedor naquela rodada — antes de avançar.

---

## Leitura contextual

O filtro de categoria na criação usa o **nome** da categoria, não o ID. Renomear uma categoria quebra silenciosamente qualquer sessão criada com aquele nome.

O QR Code codifica a URL de entrada do participante no frontend. O código de sessão de oito caracteres é derivado de UUID truncado — colisões são improváveis mas possíveis; o sistema não verifica unicidade explicitamente no código gerado.

O `reveal` é idempotente: se a pergunta já tiver um vencedor registrado, o evento é ignorado sem erro.

O `autoAdvance` elimina a necessidade de um operador dedicado ao controle do quiz — útil em eventos menores onde o próprio anfitrião conduz a experiência sem apoio técnico.

---

## 1. Estados da sessão

```
WAITING ──start──▶ ACTIVE ──next (última pergunta)──▶ FINISHED
                     │
                     └──finish (manual)──▶ FINISHED
```

| Estado | Significado |
|--------|-------------|
| `WAITING` | Sessão criada. QR Code ativo. Participantes podem entrar |
| `ACTIVE` | Quiz em andamento. Perguntas distribuídas, respostas aceitas |
| `FINISHED` | Encerrado. Ranking final disponível. Respostas não são mais aceitas |

---

## 2. Criação da sessão

**Endpoint:** `POST /api/quiz/session`

| Campo | Tipo | Regra |
|-------|------|-------|
| `numberOfQuestions` | Integer | Entre 5 e 50. Não pode exceder o total de perguntas ativas disponíveis |
| `questionTimeLimit` | Integer | Segundos por pergunta — entre 10 e 60 |
| `category` | String | Nome da categoria (opcional). Restringe o sorteio |
| `autoAdvance` | Boolean | Se `true`, avança automaticamente após timeout. Padrão `false` |

O sistema sorteia aleatoriamente `numberOfQuestions` perguntas ativas, gera o código de sessão e o QR Code, e persiste com status `WAITING`.

**Resposta inclui:** `sessionCode`, `qrCodeUrl` (base64, PNG 300×300px), `totalQuestions`, `status`, `autoAdvance`.

---

## 3. Controles

| Endpoint | Ação | Pré-condição |
|----------|------|--------------|
| `GET /api/quiz/session/stats` | Total de perguntas ativas disponíveis | — |
| `GET /api/quiz/session/{code}` | Detalhes da sessão | — |
| `POST /api/quiz/session/{code}/start` | Inicia o quiz — transmite a primeira pergunta | Status `WAITING` |
| `POST /api/quiz/session/{code}/next` | Avança para a próxima pergunta ou encerra | Status `ACTIVE` |
| `POST /api/quiz/session/{code}/reveal` | Revela a resposta correta por timeout (sem vencedor) | Status `ACTIVE` |
| `POST /api/quiz/session/{code}/finish` | Encerra a sessão manualmente | Qualquer status |
| `GET /api/quiz/session/{code}/leaderboard` | Ranking atual da sessão | — |

**Código:** `QuizSessionController.java`, `QuizSessionService.java`, `QRCodeService.java`, `QuizSession.java`

---

## Exploração

- O que acontece com os participantes durante a sessão → [`jogabilidade.md`](./jogabilidade.md)
- Visão geral das funcionalidades → [`README.md`](./README.md)

---

*Sessão — versão 1.0*

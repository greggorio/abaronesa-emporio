# Integração OpenAI

Sou a capacidade que conecta o quiz à inteligência artificial. Vivo no módulo principal do Bakery — não no engine do quiz — e opero em dois papéis inseparáveis: administrar a integração com a API OpenAI e usá-la para gerar perguntas a partir de um tema livre.

> **Pertenço a:** [`funcionalidades/`](./README.md)

---

## O que acontece aqui

O administrador configura a integração uma vez: fornece a chave de API, escolhe o modelo, define os parâmetros de geração. A partir daí, pode solicitar perguntas informando apenas um tema, um nível de dificuldade e uma quantidade.

O sistema monta o prompt, chama a API OpenAI, recebe o JSON, valida cada item e devolve o resultado pronto para revisão. Nenhuma pergunta é persistida automaticamente — a decisão de importar é sempre do administrador.

---

## Leitura contextual

Esta funcionalidade vive no módulo `backend/` (`com.smartdata.bares`), não no engine do quiz `espresso_back/` (`com.villacustom`). É uma dependência do backend principal sobre um serviço externo, não uma capacidade interna do engine de sessões.

A chave de API é armazenada criptografada (AES-128) e pode ser alterada pelo painel sem redeploy. O modelo utilizado é configurável — não está fixo no código.

O parser de resposta da IA tolera JSON envolto em blocos de markdown (` ```json `). Questões com enunciado vazio, menos de quatro opções ou `correctAnswer` fora do intervalo 0–3 são descartadas silenciosamente; as válidas são retornadas normalmente.

Se a IA devolver mais perguntas do que o solicitado, o excedente é truncado. A quantidade padrão, quando o campo `quantidade` é nulo ou zero, é 5. O máximo é 20 por chamada.

---

## 1. Configuração da integração

### 1.1 Endpoints

| Método | Rota | Função | Acesso |
|--------|------|--------|--------|
| `GET` | `/api/openai/config` | Retorna a configuração atual (chave mascarada) | `ADMIN`, `SYSTEM` |
| `PUT` | `/api/openai/salvar` | Salva a configuração | `ADMIN`, `SYSTEM` |
| `POST` | `/api/openai/testar-conexao` | Testa a conexão com as credenciais fornecidas | `ADMIN`, `SYSTEM` |
| `POST` | `/api/openai/testar-prompt` | Envia um prompt livre e retorna a resposta | `ADMIN`, `SYSTEM` |
| `GET` | `/api/openai/status` | Verifica se a integração está habilitada e configurada | Público |

### 1.2 Campos de configuração

| Campo | Descrição |
|-------|-----------|
| `apiKey` | Chave de API OpenAI — armazenada criptografada; retornada mascarada como `"********"` |
| `model` | Modelo a ser utilizado — ex: `gpt-4o-mini` |
| `maxTokens` | Limite de tokens por chamada — usado com floor de 2000 e teto de 3000 para geração |
| `temperature` | Temperatura fixa em `0.4` para geração de perguntas — não configurável por chamada |

Ao salvar, enviar `"********"` no campo `apiKey` preserva a chave existente sem sobrescrevê-la.

---

## 2. Geração de perguntas

### 2.1 Endpoint

| Método | Rota | Acesso |
|--------|------|--------|
| `POST` | `/api/openai/quiz/generate` | `ADMIN`, `SYSTEM` |

### 2.2 Request

| Campo | Tipo | Regra |
|-------|------|-------|
| `tema` | String | Tema livre — ex: `"mitologia viking"`, `"música anos 80"` |
| `quantidade` | Integer | Máximo 20. Padrão 5 quando nulo ou zero |
| `dificuldade` | String | Passado diretamente ao prompt — ex: `"EASY"`, `"HARD"` |
| `idioma` | String | Padrão `"pt-BR"` |
| `categoryId` | Long | Categoria de destino — atribuída a todas as perguntas geradas |
| `points` | Integer | Pontuação base atribuída a cada pergunta gerada |

### 2.3 Resposta

Lista de `QuizQuestionDTO` com os mesmos campos aceitos pela importação em lote. **Nada é persistido** — o resultado é retornado para revisão e importação manual.

**Código:** `OpenAiController.java`, `QuizGenerationService.java`, `OpenAiConfigService.java` — módulo `backend/`

---

## Exploração

- Importar as perguntas geradas → [`importacao-em-lote.md`](./importacao-em-lote.md)
- Curadoria manual do banco → [`banco-de-perguntas.md`](./banco-de-perguntas.md)
- Visão geral das funcionalidades → [`README.md`](./README.md)

---

*Integração OpenAI — versão 1.0*

# Banco de Perguntas

Sou o acervo de conteúdo do quiz. Existo antes de qualquer sessão — aqui as perguntas nascem, recebem classificação e aguardam sua vez. Sem um banco habitado, não há quiz possível.

> **Pertenço a:** [`funcionalidades/`](./README.md)

---

## O que acontece aqui

O acervo organiza-se em dois objetos: **categorias** e **perguntas**.

Categorias são as divisórias da estante. Cada uma carrega tema, nível de dificuldade e identidade visual (ícone e cor). Elas não contêm conteúdo — classificam o conteúdo que existe nas perguntas.

Perguntas são os átomos do quiz. Cada uma pertence a uma categoria, apresenta quatro opções e declara o índice da opção correta. Carregam também uma pontuação base e, opcionalmente, uma imagem. Perguntas podem ser ativadas ou desativadas — apenas as ativas entram no sorteio quando uma sessão é criada.

O administrador navega este espaço continuamente: cria, edita, ativa, desativa. A curadoria é manual por padrão; a importação em lote e a geração por IA são atalhos para quando o volume exige.

---

## Leitura contextual

Categorias inativas não são deletadas — apenas desaparecem das listagens públicas. Isso preserva o vínculo histórico com as perguntas que as referenciam.

A exclusão de uma categoria é física e não possui proteção: perguntas vinculadas perdem a referência. Não há cascata nem bloqueio.

Quando uma sessão é criada com filtro de categoria, o sistema busca pelo **nome** da categoria, não pelo ID. Uma categoria renomeada quebra silenciosamente os filtros de sessão que dependiam do nome anterior.

A rota `GET /api/categories/active` é pública — usada pelo frontend para popular filtros de sessão sem autenticação.

---

## 1. Categorias

### 1.1 Endpoints

| Método | Rota | Função |
|--------|------|--------|
| `POST` | `/api/categories` | Cria uma nova categoria |
| `GET` | `/api/categories` | Lista todas as categorias |
| `GET` | `/api/categories/active` | Lista categorias ativas (pública) |
| `GET` | `/api/categories/{id}` | Retorna uma categoria pelo ID |
| `GET` | `/api/categories/difficulty/{level}` | Filtra por nível de dificuldade |
| `PUT` | `/api/categories/{id}` | Atualiza uma categoria |
| `PATCH` | `/api/categories/{id}/toggle` | Alterna o estado `active` |
| `DELETE` | `/api/categories/{id}` | Remove fisicamente a categoria |

### 1.2 Estrutura

| Campo | Tipo | Regra |
|-------|------|-------|
| `id` | Long | Gerado automaticamente |
| `name` | String(100) | Obrigatório, único |
| `description` | String(500) | Opcional |
| `difficultyLevel` | Enum | `EASY`, `MEDIUM`, `HARD` ou `EXPERT` |
| `icon` | String(100) | Identificador de ícone para o frontend |
| `color` | String(7) | Cor hexadecimal — ex: `#FF5733` |
| `active` | Boolean | Padrão `true` |
| `createdAt` | LocalDateTime | Automático |
| `updatedAt` | LocalDateTime | Automático |

**Código:** `CategoryController.java`, `CategoryService.java`, `Category.java`

---

## 2. Perguntas

### 2.1 Endpoints

| Método | Rota | Função |
|--------|------|--------|
| `POST` | `/api/questions` | Cria uma nova pergunta |
| `GET` | `/api/questions` | Lista todas as perguntas |
| `GET` | `/api/questions/active` | Lista apenas perguntas ativas |
| `GET` | `/api/questions/{id}` | Retorna uma pergunta pelo ID |
| `GET` | `/api/questions/category/{categoryId}` | Lista perguntas por categoria |
| `PUT` | `/api/questions/{id}` | Atualiza uma pergunta |
| `PATCH` | `/api/questions/{id}/toggle` | Alterna o estado `active` |
| `DELETE` | `/api/questions/{id}` | Remove fisicamente a pergunta |

### 2.2 Estrutura

| Campo | Tipo | Regra |
|-------|------|-------|
| `id` | Long | Gerado automaticamente |
| `question` | String(500) | Texto da pergunta |
| `options` | List\<String\> | Exatamente 4 opções |
| `correctAnswer` | Integer | Índice da opção correta (0–3) |
| `category` | FK → Category | Categoria de pertencimento |
| `points` | Integer | Pontuação base. Padrão 10 |
| `imageUrl` | String | URL de imagem opcional |
| `active` | Boolean | Padrão `true` |

### 2.3 Seleção para sessão

Na criação da sessão, o sistema sorteia aleatoriamente entre as perguntas ativas. Se uma categoria for informada (por nome), o sorteio é restrito a ela. O número solicitado não pode exceder o total disponível — caso contrário, a sessão não é criada.

**Código:** `QuestionController.java`, `QuestionService.java`, `Question.java`

---

## Exploração

- Entrada de conteúdo em volume → [`importacao-em-lote.md`](./importacao-em-lote.md)
- Geração de perguntas por IA → [`integracao-openai.md`](./integracao-openai.md)
- Visão geral das funcionalidades → [`README.md`](./README.md)

---

*Banco de Perguntas — versão 1.0*

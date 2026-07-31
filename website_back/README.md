# Delícias do Nordeste - Backend

Backend Spring Boot para sistema de Quiz multiplayer em tempo real.

## 🚀 Tecnologias

- **Java 21**
- **Spring Boot 3.3.5**
  - Spring Web
  - Spring Data JPA
  - Spring WebSocket (STOMP)
  - Spring Validation
- **PostgreSQL** - Banco de dados
- **Flyway** - Migrations de banco
- **Lombok** - Redução de boilerplate
- **ZXing** - Geração de QR Codes
- **Swagger/OpenAPI** - Documentação da API

## 📋 Requisitos

- Java 21
- Maven 3.8+
- PostgreSQL 14+

## ⚙️ Configuração

### 1. Criar banco de dados:
```sql
CREATE DATABASE littlepaul_db;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE littlepaul_db TO postgres;
```

### 2. Configurar `application.properties` (opcional)
O arquivo já vem configurado com valores padrão para desenvolvimento.
Para produção, use variáveis de ambiente ou arquivo `application-prod.properties`.

### 3. Executar aplicação:
```bash
# Via Maven
mvn spring-boot:run

# Ou compilar JAR
mvn clean package
java -jar target/littlepaul-backend-0.0.1-SNAPSHOT.jar
```

A aplicação estará disponível em: **http://localhost:8080**

## 📡 Endpoints

### REST API

#### Gerenciamento de Sessões
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/quiz/session` | Criar nova sessão de quiz |
| `GET` | `/api/quiz/session/{code}` | Obter detalhes da sessão |
| `POST` | `/api/quiz/session/{code}/start` | Iniciar o quiz |
| `POST` | `/api/quiz/session/{code}/next` | Avançar para próxima pergunta |
| `POST` | `/api/quiz/session/{code}/finish` | Finalizar sessão manualmente |
| `GET` | `/api/quiz/session/{code}/leaderboard` | Obter ranking atual |

### WebSocket (STOMP)

**Endpoint de conexão:** `ws://localhost:8080/ws`

#### Tópicos para Subscribe (receber mensagens):
- `/topic/quiz/{sessionCode}/question` - Nova pergunta
- `/topic/quiz/{sessionCode}/leaderboard` - Atualização do ranking
- `/topic/quiz/{sessionCode}/start` - Jogo iniciado
- `/topic/quiz/{sessionCode}/end` - Jogo finalizado
- `/topic/quiz/{sessionCode}/player-joined` - Novo jogador entrou

#### Destinos para Send (enviar mensagens):
- `/app/quiz/join` - Jogador entra na sessão
- `/app/quiz/answer` - Jogador responde pergunta

#### Respostas individuais (usuário específico):
- `/user/queue/quiz/join-response` - Confirmação de entrada
- `/user/queue/quiz/answer-result` - Resultado da resposta

## 🎮 Fluxo do Jogo

1. **Admin** cria sessão via `POST /api/quiz/session`
2. **Admin** recebe código da sessão e QR Code
3. **Jogadores** escaneiam QR Code e entram via WebSocket (`/app/quiz/join`)
4. **Admin** inicia quiz via `POST /api/quiz/session/{code}/start`
5. **Todos** recebem pergunta via `/topic/quiz/{code}/question`
6. **Jogadores** respondem via `/app/quiz/answer`
7. **Todos** recebem ranking atualizado via `/topic/quiz/{code}/leaderboard`
8. **Admin** avança para próxima pergunta via `POST /api/quiz/session/{code}/next`
9. Repetir passos 5-8 até acabarem as perguntas
10. **Todos** recebem ranking final via `/topic/quiz/{code}/end`

## 📊 Banco de Dados

O schema é gerenciado pelo Flyway. As migrations estão em `src/main/resources/db/migration/`:

- `V1__create_initial_schema.sql` - Schema inicial
- `V2__insert_sample_questions.sql` - 25 perguntas de exemplo

### Categorias de Perguntas:
- `CULINARIA_NORDESTINA` - Pratos típicos, ingredientes
- `CULTURA_REGIONAL` - Danças, música, festas
- `CONHECIMENTOS_GERAIS` - Geografia, história do Nordeste

## 🧪 Sistema de Pontuação

- **Pontos base:** 10 por pergunta
- **Bônus de velocidade:**
  - < 5s: +5 pontos
  - < 10s: +3 pontos
  - < 20s: pontos normais
  - > 20s: -2 pontos (mínimo 1)

## 📚 Documentação da API

Após iniciar a aplicação, acesse:

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

## 🔧 Estrutura do Projeto

```
src/main/java/com/delicias/nordeste/
├── config/          # Configurações (WebSocket, CORS, Swagger)
├── controller/      # REST e WebSocket Controllers
├── dto/             # Data Transfer Objects
├── entity/          # Entidades JPA
├── enums/           # Enumerações
├── repository/      # Repositories JPA
└── service/         # Lógica de negócio
```

## 🐛 Troubleshooting

### Erro de conexão com PostgreSQL
Verifique se o PostgreSQL está rodando e as credenciais em `application.properties`

### Flyway migration failed
Limpe o banco e recrie:
```sql
DROP DATABASE littlepaul_db;
CREATE DATABASE littlepaul_db;
```

### WebSocket não conecta
Verifique o CORS em `CorsConfig.java` e certifique-se que a origem do frontend está permitida.

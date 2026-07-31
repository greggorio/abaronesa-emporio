# Quiz — Especificação do Domínio

## Definição

Sistema de Quiz Ao Vivo para engajamento de clientes em eventos e sessões presenciais. Opera em tempo real via WebSocket para comunicação bidirecional entre servidor e participantes. O banco de perguntas pode ser construído manualmente, importado (CSV/JSON) ou gerado automaticamente via inteligência artificial.

## Escopo

**Inclui:**
- Categorias de quiz com tema, dificuldade, ícone e cor
- Perguntas de múltipla escolha com quatro opções e resposta correta indexada
- Geração automática de perguntas via OpenAI (por tema, dificuldade, idioma e quantidade)
- Importação de perguntas via CSV ou JSON, com pré-visualização antes do commit
- Sessões ao vivo com código único de 8 caracteres e QR Code para entrada de participantes
- Jogabilidade multiplayer via WebSocket (STOMP): perguntas, respostas, ranking
- Ranking em tempo real atualizado após cada resposta
- Controle de tempo por pergunta com opção de avanço automático após timeout
- Premiação ao final da sessão

**Não inclui:**
- Fidelização como domínio principal (pontos e recompensas contínuas pertencem a `fidelizacao/`)
- Gestão de agenda ou operação de eventos (pertence a `eventos/`)
- Gestão de produtos, vendas ou estoque

## Entidades

| Entidade | Descrição |
|----------|-----------|
| `Category` | Categoria com nome, dificuldade (EASY / MEDIUM / HARD / EXPERT), ícone e cor |
| `Question` | Pergunta com texto, quatro opções, índice da resposta correta, pontos e imagem opcional |
| `QuizSession` | Sessão com código único, status, lista de perguntas, índice atual e limite de tempo |
| `Player` | Participante com nickname, pontuação, contagem de acertos e erros |
| `QuizAnswer` | Resposta submetida por um participante, com tempo de resposta registrado |

## Fluxo principal

1. Administrador cria ou importa perguntas (manual, CSV/JSON ou geração por IA)
2. Administrador abre sessão — sistema gera código e QR Code
3. Participantes entram escaneando o QR Code ou digitando o código
4. Administrador inicia o quiz — primeira pergunta é enviada via WebSocket
5. Participantes respondem dentro do tempo limite
6. Ranking atualiza em tempo real após cada resposta
7. Administrador avança manualmente ou o sistema avança por timeout (auto-advance)
8. Ao final, ranking completo é exibido e ganhador é premiado

## Geração de perguntas por IA

O administrador aciona a geração informando:
- **Tema** (texto livre — ex: "mitologia viking", "música anos 80")
- **Quantidade** (até 20 perguntas por chamada)
- **Dificuldade** (EASY, MEDIUM, HARD, EXPERT)
- **Idioma** (padrão: português)
- **Categoria** de destino

O sistema envia o prompt à API OpenAI, recebe as perguntas em JSON, valida cada item (quatro opções, resposta correta válida, pontuação) e entrega o resultado pronto para importação. O modelo e as credenciais são configuráveis pelo painel de administração.

## Importação via CSV/JSON

O fluxo de importação opera em duas etapas:
1. **Preview (dry-run):** o arquivo é analisado e o sistema retorna o que seria importado, atualizado ou ignorado — sem gravar nada
2. **Commit:** importação definitiva com o comportamento escolhido para duplicatas (ignorar, atualizar ou permitir)

Templates de CSV e JSON estão disponíveis para download direto pelo painel.

## Decisões de domínio

- Quiz é engajamento situacional — sessões são efêmeras e não geram histórico de fidelização
- A geração por IA é um recurso de produtividade do administrador, não uma automação autônoma
- Credenciais da OpenAI são armazenadas criptografadas (AES-128) e configuráveis sem redeploy

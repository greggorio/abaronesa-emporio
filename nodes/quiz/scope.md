# Quiz — Escopo

**Status**: EM_DESENVOLVIMENTO

## Propósito

Sistema de quiz ao vivo para engajar clientes durante eventos e noites no estabelecimento. Participantes respondem pelo app e acompanham o ranking em tempo real na tela.

## Fluxo principal

1. Anfitrião inicia uma sessão de quiz
2. Participantes entram pelo app
3. Perguntas de múltipla escolha são exibidas com tempo limitado
4. Respostas são pontuadas automaticamente
5. Ranking é atualizado em tempo real via WebSocket
6. Ganhador é premiado ao final

## O que está no escopo

- Categorias de quiz (temas e dificuldade)
- Perguntas e respostas de múltipla escolha
- Sessões de quiz ao vivo
- Participação em tempo real (WebSocket)
- Ranking ao vivo
- Pontuação e gamificação
- Dashboard com dados de engajamento

## O que não está no escopo

- Gestão de produtos, vendas ou estoque
- Fidelização como domínio principal

## Entidades

| Entidade | Descrição |
|----------|-----------|
| `Category` | Tema do quiz com nível de dificuldade |
| `Question` | Pergunta de múltipla escolha |
| `QuizSession` | Sessão ao vivo de quiz |
| `QuizParticipant` | Cliente participante identificado |
| `QuizAnswer` | Resposta enviada por um participante |
| `QuizRanking` | Ranking calculado em tempo real |

# Quiz

Quiz é o domínio de engajamento ao vivo do Bakery. Existe para proporcionar experiências interativas de perguntas e respostas durante eventos e noites presenciais — com participação pelo app via QR Code, ranking visível em tempo real e premiação ao final.

O módulo opera sobre WebSocket/STOMP para a jogabilidade em tempo real e centraliza o domínio em cinco entidades: `Category`, `Question`, `QuizSession`, `Player` e `QuizAnswer`. O banco de perguntas pode ser curado manualmente, importado em lote (CSV/JSON com pré-visualização) ou gerado sob demanda pela OpenAI — a curadoria manual é a base, e a IA é um atalho opcional.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`quiz.md`](./quiz.md) — especificação do domínio: entidades, fluxo principal, geração por IA e importação em lote
- [`funcionalidades/`](./funcionalidades/README.md) — dissecação operacional: endpoints, regras de negócio, eventos WebSocket, pontuação e segurança

## Leitura contextual

Quiz é engajamento situacional, não fidelização. Sessões são efêmeras, iniciadas por um anfitrião — isso o distingue estruturalmente de `fidelizacao/`, onde o relacionamento é persistente e acumulativo.

A fronteira com `eventos/` é de uso: o quiz acontece dentro de um evento, mas não governa a agenda nem a operação do evento em si.

O diferencial técnico do módulo é a geração de questões via inteligência artificial (OpenAI): o administrador informa um tema, dificuldade e quantidade, e o sistema produz o banco de perguntas automaticamente. Isso reduz o esforço de curadoria para zero em casos comuns.

## Exploração

- Especificação completa do domínio → [`quiz.md`](./quiz.md)
- Identidade do participante → [`clientes/`](../clientes/README.md)
- Contexto de uso do quiz → [`eventos/`](../eventos/README.md)
- Módulo conceitualmente adjacente → [`fidelizacao/`](../fidelizacao/README.md)

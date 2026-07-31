# Módulos Funcionais

**Sou o ambiente onde o Bakery se reconhece por seus domínios de negócio.**  
Existo dentro de `docs/`, no caminho `docs/modules/`, e minha função não é explicar a arquitetura do sistema nem a operação da infraestrutura. Minha função é organizar a leitura funcional do que o Bakery existe para fazer.

Aqui vivem os módulos que estruturam a operação do ecossistema: cadastro, catálogo, estoque, produção, suprimentos, vendas, financeiro, relacionamento, consumo digital e demais recortes funcionais.

> **Pertenço a:** [`docs/`](../README.md)  
> **Ambiente:** `docs/modules/`

---

## Qual é minha função

Minha função é permitir que o leitor encontre o domínio funcional correto e, a partir dele, aprofunde a exploração do Bakery pelo recorte de negócio adequado.

Não sou a camada de desenho estrutural do sistema.  
Não sou a camada de runtime ou operação.  
Não sou a referência principal de contratos técnicos expostos pelo backend.

Eu existo para:

- apresentar os módulos funcionais reais do Bakery
- separar domínios de negócio de agrupadores editoriais ou materiais auxiliares
- indicar por qual módulo o leitor deve começar conforme a pergunta que trouxe
- manter visível a diferença entre especificação, implementação e aderência

---

## O que vive neste ambiente

Os elementos principais deste nível são módulos funcionais reais do sistema.

| Módulo | Finalidade | Entre quando precisar... |
|--------|------------|--------------------------|
| [`clientes/`](./clientes/README.md) | Identidade, perfis, relacionamento e superfícies do cliente | Entender cadastro, segmentação, área do cliente, favoritos e recorrência |
| [`consumo-digital/`](./consumo-digital/README.md) | Jornada digital do cliente | Entender mesa digital, QR ordering, delivery, conta digital, pagamentos, KDS e waiter |
| [`dashboard/`](./dashboard/README.md) | Visões gerenciais e leitura consolidada da operação | Entender relatórios, paineis e acompanhamento executivo |
| [`estoque/`](./estoque/README.md) | Controle de saldo, movimentação, validade e lotes | Entender estrutura e governança do estoque |
| [`eventos/`](./eventos/README.md) | Operação de eventos e capacidades transversais | Entender agenda, execução e particularidades do módulo |
| [`fidelizacao/`](./fidelizacao/README.md) | Pontos, recompensas, resgates e benefícios | Entender retenção, gamificação e relacionamento contínuo |
| [`financeiro/`](./financeiro/README.md) | Fluxos econômicos e contábeis operacionais | Entender contas a pagar, contas a receber, caixa e configurações |
| [`producao/`](./producao/README.md) | Preparação interna e transformação operacional | Entender ficha técnica e produção própria |
| [`produtos/`](./produtos/README.md) | Catálogo e oferta do estabelecimento | Entender SKUs, promoções, disponibilidade, signage e harmonização |
| [`quiz/`](./quiz/README.md) | Experiências de quiz e gamificação próprias | Entender este domínio transversal específico |
| [`suprimentos/`](./suprimentos/README.md) | Abastecimento e cadeia de compras | Entender fornecedores, pedidos de compra e recebimento |
| [`temas/`](./temas/README.md) | White-label e parametrização visual | Entender temas, Android e agendamentos relacionados |
| [`vendas/`](./vendas/README.md) | Pedido, mesa, conta e pagamento | Entender o coração comercial e transacional do sistema |

---

## Como interpretar este ambiente

Cada caixa acima representa um domínio funcional do Bakery.

O objetivo deste nível não é esgotar as regras de cada módulo, mas orientar a entrada correta.

Se a sua pergunta for:

- “como o Bakery vende e fecha a conta?” → entre em [`vendas/`](./vendas/README.md)
- “como o cliente interage digitalmente com o estabelecimento?” → entre em [`consumo-digital/`](./consumo-digital/README.md)
- “como o catálogo e a oferta são organizados?” → entre em [`produtos/`](./produtos/README.md)
- “como o estoque, a produção e o abastecimento se sustentam?” → entre em [`estoque/`](./estoque/README.md), [`producao/`](./producao/README.md) e [`suprimentos/`](./suprimentos/README.md)
- “como o sistema reconhece, segmenta e retém clientes?” → entre em [`clientes/`](./clientes/README.md) e [`fidelizacao/`](./fidelizacao/README.md)
- “como a gestão lê o negócio?” → entre em [`dashboard/`](./dashboard/README.md) e [`financeiro/`](./financeiro/README.md)

---

## Leitura de aderência

Este ambiente não trata os módulos como descrições abstratas.

Cada módulo deve tornar visível:

- o que ele declara ser
- o que já existe materialmente no sistema
- o quanto a implementação adere à especificação
- quais lacunas ainda permanecem

Por isso, a distinção entre especificação, implementação e aderência não é burocracia editorial. Ela é o mecanismo mínimo para que a leitura funcional não se torne ficção.

---

## O que está desalinhado neste nível

Hoje este ambiente ainda convive com uma mistura entre duas naturezas diferentes:

1. **Módulos funcionais reais** — as caixas que representam domínios do negócio
2. **Artefatos de governança editorial** — documentos que governam como esta pasta é mapeada, reorganizada, avaliada e mantida

Os seguintes arquivos soltos na raiz não são módulos funcionais:

- [`MAPEAMENTO_MODULOS.md`](./MAPEAMENTO_MODULOS.md)
- [`ESTRUTURA_EDITORIAL_ALVO.md`](./ESTRUTURA_EDITORIAL_ALVO.md)
- [`GUIA_ESTILO.md`](./GUIA_ESTILO.md)
- [`GUIA_OPERACIONAL.md`](./GUIA_OPERACIONAL.md)
- [`NIVEIS_COMPLETUDE.md`](./NIVEIS_COMPLETUDE.md)
- [`TEMPLATE_MODULO.md`](./TEMPLATE_MODULO.md)
- [`HISTORICO_REORGANIZACAO.md`](./HISTORICO_REORGANIZACAO.md)

Sua presença aqui não invalida a pasta, mas revela uma tensão estrutural: a camada que descreve os domínios e a camada que governa editorialmente essa descrição ainda compartilham o mesmo nível visível.

O encaminhamento correto não é ignorar isso, e sim reconhecer que esta governança deve, no futuro, ganhar enquadramento mais explícito dentro do próprio ambiente.

---

## Artefatos auxiliares deste ambiente

Enquanto essa separação não é materializada em uma caixa própria, os documentos abaixo permanecem como apoio de governança da pasta:

| Documento | Papel atual |
|-----------|-------------|
| [`MAPEAMENTO_MODULOS.md`](./MAPEAMENTO_MODULOS.md) | Inventário e enquadramento dos domínios reais do sistema |
| [`ESTRUTURA_EDITORIAL_ALVO.md`](./ESTRUTURA_EDITORIAL_ALVO.md) | Estrutura desejada para a representação editorial dos módulos |
| [`GUIA_ESTILO.md`](./GUIA_ESTILO.md) | Convenções de escrita dos READMEs e documentos do ambiente |
| [`GUIA_OPERACIONAL.md`](./GUIA_OPERACIONAL.md) | Método de trabalho para mapear, especificar, confrontar e evoluir módulos |
| [`NIVEIS_COMPLETUDE.md`](./NIVEIS_COMPLETUDE.md) | Critério de leitura de consolidação documental |
| [`TEMPLATE_MODULO.md`](./TEMPLATE_MODULO.md) | Estrutura base para novos módulos |
| [`HISTORICO_REORGANIZACAO.md`](./HISTORICO_REORGANIZACAO.md) | Memória dos agrupadores antigos e da evolução editorial da pasta |

Esses documentos apoiam a manutenção do ambiente, mas não substituem a leitura dos módulos funcionais.

---

## Fronteiras deste ambiente

Este nível não deve:

- explicar profundamente o conteúdo interno de cada módulo
- competir com os READMEs dos módulos-filhos
- absorver arquitetura, infraestrutura ou contratos técnicos como se fossem domínios funcionais
- tratar governança editorial como se fosse módulo de negócio

Quando a pergunta deixar de ser funcional e passar a ser estrutural, operacional ou técnica, o leitor deve voltar para o ambiente correto em `docs/`.

---

## Caminhos de exploração

- Quer começar pela **jornada comercial principal**? → [`vendas/`](./vendas/README.md)
- Quer começar pela **experiência digital do cliente**? → [`consumo-digital/`](./consumo-digital/README.md)
- Quer começar pelo **catálogo e oferta**? → [`produtos/`](./produtos/README.md)
- Quer começar pela **retaguarda operacional**? → [`estoque/`](./estoque/README.md), [`producao/`](./producao/README.md) e [`suprimentos/`](./suprimentos/README.md)
- Quer começar pelo **relacionamento com clientes**? → [`clientes/`](./clientes/README.md) e [`fidelizacao/`](./fidelizacao/README.md)
- Quer começar pela **leitura gerencial e econômica**? → [`dashboard/`](./dashboard/README.md) e [`financeiro/`](./financeiro/README.md)

---

## Navegação relacionada

- [Arquitetura](../architecture/README.md)
- [Infraestrutura](../infrastructure/README.md)
- [Integrações](../integrations/README.md)
- [API Reference](../api-reference/README.md)

# Documentação do Ecossistema Bakery

**Sou a porta de entrada documental do Bakery.**  
Existo dentro do repositório `~/git/bakery`, no ambiente `docs/`, e minha função não é explicar tudo de uma vez, nem inventariar cada detalhe técnico do sistema.

Meu papel é orientar a exploração do ecossistema a partir da pergunta que o leitor traz.

O Bakery é um sistema para operação de bares e restaurantes que atravessa toda a cadeia operacional: backoffice, superfícies digitais, atendimento, cozinha, delivery, relacionamento com clientes, pagamentos, produção, estoque e gestão.

Eu organizo esse conhecimento em ambientes separados para que cada tema possa ser compreendido dentro do seu próprio contexto.

> **Pertenço ao:** repositório `~/git/bakery`  
> **Ambiente:** `docs/`

---

## Qual é minha função

Minha função é ajudar o leitor a encontrar o ambiente correto de exploração.

Não sou a fonte de verdade de implementação em si.  
Não substituo o código.  
Não aprofundo aqui o conteúdo interno de cada área.

Eu apenas:

- apresento os grandes ambientes documentais do sistema
- esclareço o papel de cada um
- indico quando cada ambiente deve ser explorado
- encaminho a leitura para níveis mais profundos

Enquanto a raiz do repositório não possui uma consciência textual própria, assumo também a função de porta principal de navegação documental do projeto.

---

## Ambientes que vivem aqui

Meu conhecimento está distribuído em seis ambientes principais.

| Ambiente | Finalidade | Entre quando precisar... |
|----------|------------|--------------------------|
| [`modules/`](./modules/README.md) | Explicar o que o sistema faz no domínio de negócio | Entender módulos como clientes, vendas, estoque, produção, financeiro, fidelização e demais áreas funcionais |
| [`architecture/`](./architecture/README.md) | Explicar como o sistema foi desenhado | Entender a arquitetura atual, a arquitetura alvo, decisões técnicas, gaps e direção estrutural |
| [`infrastructure/`](./infrastructure/README.md) | Explicar onde e como o sistema roda em operação | Consultar deploy, monitoramento, segurança, escalabilidade e procedimentos reais |
| [`development/`](./development/README.md) | Explicar como trabalhar no código | Fazer setup local, seguir guias técnicos, entender testes e convenções de desenvolvimento |
| [`api-reference/`](./api-reference/README.md) | Explicar os contratos técnicos expostos pelo sistema | Consumir autenticação, endpoints e webhooks |
| [`integrations/`](./integrations/README.md) | Explicar com quais serviços externos o sistema se conecta | Entender provedores, contratos externos, acoplamentos e integrações de terceiros |

---

## Como interpretar esses ambientes

Alguns ambientes são próximos, mas não têm a mesma função.

- `modules/` descreve comportamento de produto e domínio de negócio
- `architecture/` descreve desenho estrutural, decisões e direção técnica
- `infrastructure/` descreve operação real, produção e procedimentos
- `development/` descreve trabalho local, implementação e continuidade técnica
- `api-reference/` descreve contratos expostos pelo Bakery
- `integrations/` descreve dependências e serviços externos consumidos pelo Bakery

Se a sua pergunta for “como isso funciona para o negócio?”, entre em `modules/`.

Se a sua pergunta for “como isso foi desenhado?”, entre em `architecture/`.

Se a sua pergunta for “como isso roda em produção?”, entre em `infrastructure/`.

Se a sua pergunta for “como eu altero isso no código?”, entre em `development/`.

Se a sua pergunta for “qual é o contrato técnico desta interface?”, entre em `api-reference/`.

Se a sua pergunta for “como nos conectamos com um provedor externo?”, entre em `integrations/`.

---

## Fronteiras deste ambiente

Este nível não deve despejar todo o conhecimento do projeto.

Por isso:

- não detalho profundamente o conteúdo interno das caixas acima
- não duplico o que já pertence aos READMEs das seções-filhas
- não trato o código como se ele pudesse ser resumido integralmente aqui
- não transformo este ambiente em inventário exaustivo de arquivos

Cada caixa deve assumir a responsabilidade por sua própria consciência contextual.

---

## Caminhos de exploração

- Quer entender **o que o sistema faz**? → [`modules/`](./modules/README.md)
- Quer entender **como ele é estruturado**? → [`architecture/`](./architecture/README.md)
- Quer entender **como ele opera em produção**? → [`infrastructure/`](./infrastructure/README.md)
- Vai **trabalhar no código**? → [`development/`](./development/README.md)
- Precisa **consumir contratos técnicos**? → [`api-reference/`](./api-reference/README.md)
- Vai **avaliar integrações externas**? → [`integrations/`](./integrations/README.md)

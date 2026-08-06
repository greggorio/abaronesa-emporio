# Roadmap do release control

Itens conscientemente adiados. Cada um foi identificado durante a implementação,
avaliado e deixado fora do escopo corrente por decisão explícita — não são
lacunas descobertas depois. O MVP é o fluxo de ponta a ponta: alterar em
desenvolvimento, publicar uma release e atualizar produção pela própria UI.

## Absorção seletiva de candidatos

**Estado:** não suportado. **Motivo do adiamento:** complexidade sem validação
prévia do fluxo completo.

Hoje a publicação usa sempre o candidato mais recente e absorve toda a sua
linhagem, porque o manifesto é cumulativo. Isso cobre o caminho normal — o que
está no `main` e ainda não saiu vai junto na próxima release.

O caso não coberto é o hotfix: absorver um único candidato entre vários já
commitados no `main`, deixando os demais para depois. Suportar isso exige
decidir o que fazer com as dependências entre candidatos, já que o manifesto
cumulativo do candidato escolhido carrega os antecessores por construção. Sem
isso resolvido, oferecer a escolha na tela transferiria para quem publica um
problema que o sistema não sabe resolver.

Enquanto não existir, o contorno é o caminho de sempre: corrigir no `main` e
publicar a release seguinte.

## Visibilidade do progresso de CI na UI

**Estado:** não suportado. **Motivo do adiamento:** não bloqueia o fluxo.

Depois de publicar, quem opera acompanha o run pelo GitHub. A UI reconcilia o
estado da operação, mas não mostra o andamento dos jobs. Trazer isso para a tela
onde a release é gerada evitaria a alternância de contexto.

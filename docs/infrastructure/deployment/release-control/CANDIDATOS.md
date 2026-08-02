# Candidatos e proveniência

Um candidato é o BOM completo dos seis componentes comerciais, sempre com
`deployable: false`. Ele não é release global e não pode ser consumido pelo
deployer.

## Cadeia confiável

Em push para `main`, a CI verde produz um único `candidate-plan`. O workflow
`publish-candidate.yml` é acionado somente por `workflow_run` concluído e
reconfere pela API canônica o repositório, evento, branch, conclusão, SHA e
artifact antes do checkout. Pull requests, forks, execução manual, artifact
ambíguo e SHA fora de `origin/main` falham fechados.

O run que produziu o plano na CI e o run de `Publish Candidate` são
identidades distintas. `sourceCi.runId/attempt` permanece ligado ao primeiro;
`workflow.runId/attempt` e os resultados publicados permanecem ligados ao
segundo. Cada vínculo é validado contra seu artifact de origem, sem exigir que
os dois IDs coincidam.

Somente o job de build recebe escrita em packages e OIDC. Cada componente
afetado é construído uma vez para `linux/amd64`, examinado antes da
autenticação, enviado ao GHCR sem rebuild e identificado pelo digest retornado
pelo registry. O controle de integridade do componente é a referência
imutável `immutableRef == imageRepository + "@" + digest`, exigida em todas as
camadas. A attestation nativa de build provenance foi removida por ser
indisponível em repositório privado de propriedade de usuário; nenhum
placeholder ou substituto nominal ocupa seu lugar.

## Herança e primeiro candidato

O candidato anterior é procurado em uma janela limitada de execuções verdes e
aceito somente com shape, checksum, schema e metadados válidos. Componentes não
reconstruídos preservam byte a byte `immutableRef` e digest. Origem ausente,
circular ou divergente é rejeitada.

Fim normal da paginação produz `no_previous`; atingir o limite configurado com
páginas cheias e sem decisão falha fechado. A seleção vincula ID, URL e digest
do artifact ao run e ao head SHA; manifesto, sidecar e metadata precisam
confirmar o mesmo run, SHA, candidato e checksum.

Sem candidato anterior, o plano deve classificar o evento como primeiro
release e construir os seis componentes. Uma matriz vazia é válida somente
quando todos podem ser herdados de um candidato anterior íntegro.

O plano efetivo usa `schemaVersion: 2` e registra explicitamente `mode`.
`continue` conserva a resolução integral do resolvedor S05. Os modos
`no_changes`, `already_published` e `superseded` usam resoluções terminais do
adaptador de lineage: arrays de build e validação vazios, os seis componentes
herdados e exatamente o warning prescrito para o modo. Eles não chamam o
resolvedor com paths vazios e nunca habilitam build, pending ou integração.

## Validação integrada e publicação

Antes do artifact final, o BOM completo é referenciado somente por digest em
uma stack Compose efêmera. O harness usa nomes exclusivos, variáveis
sanitizadas `.invalid`, banco novo, sete serviços saudáveis e somente o gateway
em um único bind efêmero de loopback. O job autentica no GHCR somente para
leitura. `config`, `up`, `ps`, probes e `down` usam os mesmos dois arquivos
Compose e o mesmo projeto. Probes atravessam ambos os backends, os dois
frontends e o serviço WhatsApp. A limpeza remove containers, redes e volumes
do prefixo exato e somente as seis imagens por digest declaradas no manifesto.

O manifesto, checksum e metadata só são publicados após essa validação. A
retenção do plano é de 7 dias; resultados intermediários, 1 dia; candidato
final, 30 dias. O step final registra os outputs públicos `artifact-id`,
`artifact-url` e `artifact-digest`. Antes de qualquer geração de ambiente,
login ou comando Docker, o bundle pending, sidecar, metadata, plano efetivo,
predecessor e bindings são revalidados. O cleanup tenta todas as operações
dirigidas e acumula os erros antes de falhar.

Uma falha anterior ao upload impede o artifact final. Uma falha posterior ao
upload pode deixar o artifact físico em um run vermelho; ele permanece
inelegível, pois a descoberta aceita somente runs concluídos com sucesso.

Esta automação está configurada, mas ainda não foi executada remotamente. Não
há promoção para release, deploy, SSH ou acesso à VPS.

## Outcome e concorrência

Cada execução verde produz exatamente um `candidate-outcome`. `published` e
`already_published` vinculam candidato e artifact; `no_changes` e
`superseded` mantêm esses campos nulos. A segurança não depende da ordem de
conclusão: HEAD, lineage e idempotência são verificados antes do build e
novamente antes do upload. Runs verdes sem um único outcome válido tornam a
busca fail-closed. Para `published`, run e attempt, candidate ID, artifact ID,
digest REST, manifesto, sidecar e metadata precisam formar uma única
identidade exata. Todos os candidatos encontrados são classificados antes da
decisão; lineage não relacionada ou combinação ambígua falha fechada.

Um outcome `already_published` referencia um artifact anterior pelo ID
canônico. A descoberta consulta esse artifact, exige seu run verde, valida
manifesto, sidecar e metadata e confere ID e digest. Nesse estado,
`predecessorCandidateId` deve ser o próprio `candidateId`; em `published`, ele
deve coincidir com o predecessor registrado no manifesto.

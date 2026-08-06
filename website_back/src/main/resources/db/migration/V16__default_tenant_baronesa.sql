-- O baseline V2 semeia o tenant 'espresso', herdado do cliente de origem da
-- solucao. O site publico do Emporio resolve o tema pelo tenant 'baronesa', e
-- sem nenhuma linha correspondente a resolucao do tema ativo falhava e a pagina
-- subia sem identidade visual.
--
-- A correcao vem como migration nova, e nao como edicao do V2: o V2 ja foi
-- aplicado nos bancos existentes, e mudar seu conteudo invalidaria o checksum
-- do Flyway e impediria a subida do servico.
--
-- Idempotente por construcao: em banco novo o V2 acabou de semear 'espresso' e
-- estas linhas o renomeiam; em banco ja renomeado nenhuma linha e afetada.

UPDATE tenant_config
   SET value = 'baronesa',
       updated_at = now()
 WHERE key = 'default_tenant_id'
   AND value = 'espresso';

UPDATE theme
   SET tenant_id = 'baronesa',
       updated_at = now()
 WHERE tenant_id = 'espresso';

UPDATE theme_assignment
   SET tenant_id = 'baronesa',
       updated_at = now()
 WHERE tenant_id = 'espresso';

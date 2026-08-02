-- O grupo Admin (id=1) e dado de referencia exigido pelo modelo de permissoes.
-- Ate aqui ele so existia em bancos antigos, criado manualmente. Em banco novo a
-- tabela nascia vazia e GrupoAdminInitializer falhava: a entidade usa
-- @GeneratedValue(IDENTITY), entao save() com id atribuido vira merge() sobre uma
-- linha inexistente e o Hibernate lanca StaleObjectStateException, marcando a
-- transacao como rollback-only e abortando a inicializacao da aplicacao.

INSERT INTO grupo_usuario (id, descricao, ativo)
SELECT 1, 'Admin', TRUE
WHERE NOT EXISTS (SELECT 1 FROM grupo_usuario WHERE id = 1);

-- Insert com id explicito nao avanca a sequence do BIGSERIAL; realinhar evita
-- colisao de chave primaria no proximo grupo criado pela aplicacao.
SELECT setval(
    pg_get_serial_sequence('grupo_usuario', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM grupo_usuario), 1),
    TRUE
);

-- Adiciona novos campos à tabela evento
ALTER TABLE evento ADD COLUMN data_hora_fim TIMESTAMP;
ALTER TABLE evento ADD COLUMN preco DECIMAL(10,2);
ALTER TABLE evento ADD COLUMN gratuito BOOLEAN NOT NULL DEFAULT FALSE;
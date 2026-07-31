INSERT INTO configuracoes (chave, valor, nome, descricao) VALUES
('nfe_ambiente', '2', 'Ambiente NFe', '1=Produção, 2=Homologação'),
('nfe_serie', '1', 'Série NFe', 'Série da Nota Fiscal Eletrônica'),
('nfe_numero', '1', 'Número NFe', 'Próximo número da NFe'),
('nfce_ambiente', '2', 'Ambiente NFCe', '1=Produção, 2=Homologação'),
('nfce_serie', '1', 'Série NFCe', 'Série da Nota Fiscal de Consumidor'),
('nfce_numero', '1', 'Número NFCe', 'Próximo número da NFCe'),
('nfce_token_csc', '', 'Token CSC', 'Código de Segurança do Contribuinte'),
('nfce_id_csc', '', 'ID CSC', 'Identificador do CSC'),
('nfce_imprimir_automatico', 'true', 'Imprimir Automático', 'Imprimir DANFCE após autorização'),
('nfce_modo_impressao', 'TERMICA', 'Modo Impressão', 'TERMICA ou A4'),
('nfe_cnpj', '', 'CNPJ Emitente', 'CNPJ da empresa emissora');

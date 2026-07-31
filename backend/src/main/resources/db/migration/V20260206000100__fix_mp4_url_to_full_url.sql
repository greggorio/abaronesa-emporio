-- Migration: Atualiza mp4_url para URL completa do ERP
-- Converte caminhos relativos /uploads/... para URLs completas
-- Versão: 20260206000100

UPDATE product_signage 
SET mp4_url = CONCAT('http://localhost:8080/', mp4_url)
WHERE mp4_url IS NOT NULL 
  AND mp4_url LIKE '/uploads/%'
  AND mp4_url NOT LIKE 'http%';

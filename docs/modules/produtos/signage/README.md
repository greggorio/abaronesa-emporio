# Signage

Signage é o sub-domínio que governa a exposição de produtos em painéis digitais. Existe para gerar conteúdo visual (frases, paletas, vídeo) com suporte a templates e IA, e sincronizar com o signage-api externo.

Pertenço a [`produtos/`](../README.md).

## Domínio

- [`signage.md`](./signage.md) — pipeline completo (IA → render → sync), modelo de dados (ProductSignage, SignageTemplate), endpoints e decisões

## Leitura contextual

O pipeline é assíncrono: IA gera frases e paleta → render service cria vídeo MP4 → sync job envia para signage-api a cada 5 min. O status do signage reflete o estágio no pipeline.

## Exploração

- Especificação completa → [`signage.md`](./signage.md)
- Painéis digitais (admin) → (espresso_front) `/admin/signage`
- KDS (cozinha) → [`../../consumo-digital/kds/README.md`](../../consumo-digital/kds/README.md)

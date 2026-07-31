# Mesas

**Status**: ESTAVEL

## Quem sou

Sub-domínio de Mesas — cadastro, ocupação e sessões de consumo. Gerencia o ponto de partida de toda venda presencial: a mesa física onde o cliente é atendido.

## Para que existo

Controlar quais mesas existem, se estão ocupadas ou livres, quem está sentado nelas e por quanto tempo, servindo como âncora para pedidos, conta e pagamento.

## A quem pertenço

Módulo de **Vendas** — a mesa é o contêiner da sessão de consumo, que por sua vez contém convidados, pedidos e pagamentos.

## Domínio imediato

- Cadastro de mesas (slug único, rótulo, referência, ativo/inativo)
- Sessão de consumo (abertura, fechamento, self-checkout liberado)
- Convidados por sessão (guest token único, nome, device fingerprint, anfitrião)
- Cobranças incidentes (couvert artístico por convidado ou sessão)
- Abertura assistida (staff) e auto-serviço (QR code)

## Coerente / Desalinhado

- **Coerente**: integração completa com pedidos (scopo da sessão), conta (agrega por sessão) e pagamento (fecha sessão)
- **Desalinhado**: não há reserva de mesas com horário; não há mapa visual do salão com disposição das mesas

## Caminhos de exploração

Leia `mesas.md` para especificação completa. Depois navegue para `pedidos/` para entender como os pedidos se vinculam a sessões e convidados.

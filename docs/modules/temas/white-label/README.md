# White Label

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de White Label — coração do sistema de temas. Gerencia o cadastro, personalização visual e conteúdo de cada tema, incluindo tokens de design (cores HSL, bordas), assets (logo, favicon, imagens) e conteúdo textual completo (hero, about, SEO).

## Para que existo

Permitir que cada estabelecimento tenha sua própria identidade visual sem precisar alterar uma linha de código — cores, fontes, logo e textos são configurados uma vez e aplicados automaticamente em todo o ecossistema (PWA, site, documentos).

## A quem pertenço

Módulo de **Temas** — white-label é o núcleo que define a identidade visual; android e agendamento-sazonal são extensões.

## Domínio imediato

- Cadastro CRUD de temas (nome, status DRAFT/PUBLISHED/ARCHIVED, tenant)
- Tokens de design em HSL (primary, secondary, accent, muted, destructive, background, foreground, border, ring, radius)
- Assets: upload de logo, favicon, hero background, OG image
- Conteúdo textual: hero, about, features, business info, nav items, redes sociais
- SEO e Open Graph: title, description, site name, author, canonical URL, locale, OG tags, Twitter cards, robots, themeColor
- Duplicação de temas (cópia com baseThemeId)
- Aplicação imediata via WebSocket para todos os clientes conectados
- Tradução i18n do conteúdo via entity_translation

## Coerente / Desalinhado

- **Coerente**: CSS injection em tempo real, suporte multi-tenant, fallback para tenant padrão, duplicação com herança
- **Desalinhado**: não há preview antes de publicar; não há versionamento de alterações por tema; não suporta tipografia customizada (apenas cores e radius)

## Caminhos de exploração

Leia `white-label.md` para especificação detalhada. Veja também `agendamento-sazonal/` para ativação programada de temas e `android/` para configuração de APK.

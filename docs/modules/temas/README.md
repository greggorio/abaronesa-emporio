# Temas

**Status**: EM_DESENVOLVIMENTO

## Quem sou

Módulo de Temas — sistema de identidade visual e white-label multi-tenant. Permite que cada estabelecimento customize completamente a aparência do seu app PWA, site e APK Android: cores (via tokens HSL), logo, favicon, conteúdo do site (hero, about, SEO), configuração de APK e temas sazonais com ativação programada.

## Para que existo

Tornar cada instância do Bakery visualmente única sem necessidade de fork do código. O tema controla desde a paleta de cores até o nome do app no celular do cliente, passando por metadados de SEO e Open Graph.

## A quem pertenço

Infraestrutura de apresentação — aplica-se a **todos** os módulos que expõem interface visual (vendas, cardápio digital, PDV). O tema ativo é injetado como variáveis CSS no `document.documentElement` em tempo real via `ThemeContext`.

## Domínio imediato

- **white-label/** — Cadastro de temas (nome, status), tokens de design (cores HSL, raio, bordas), assets (logo, favicon, hero, OG image), conteúdo (hero, about, SEO, redes sociais), duplicação
- **android/** — Configuração específica para APK Android: nome do app, package name, ícones (zip com todas as densidades), Firebase Cloud Messaging (FCM), Firebase Admin SDK
- **agendamento-sazonal/** — Programação de temas por período com prioridade (Natal, Páscoa, Halloween, etc.), ativação automática via `ThemeAssignment`

## Coerente / Desalinhado

- **Coerente**: CSS injection em tempo real via WebSocket, i18n do conteúdo via `entity_translation`, fallback hierarchy (agendado > publicado > tenant padrão), redeploy signal para mudanças críticas (SEO, favicon)
- **Desalinhado**: não há preview de tema antes de publicar; não há versionamento de histórico de alterações; agendamento sazonal ainda sem interface de calendário visual

## Caminhos de exploração

Leia `temas.md` para especificação completa. Depois navegue: `white-label/` → `android/` → `agendamento-sazonal/`.

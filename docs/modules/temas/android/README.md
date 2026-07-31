# Android

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Android — configuração para geração de APK customizado.

## Para que existo

Cada cliente ter seu próprio aplicativo Android na Play Store com nome, ícones e push notifications personalizados.

## A quem pertenço

Módulo de **Temas** — configurações armazenadas em `assets.android`.

## Domínio imediato

- Nome do app, package name, version name/code
- Custom URL scheme (deep linking)
- Ícones do launcher (zip com todas as densidades mipmap)
- Firebase Cloud Messaging (google-services.json)
- Firebase Admin SDK (firebase-adminsdk.json)
- Validação de estrutura do zip de ícones

## Caminhos de exploração

Leia `android.md`. Consulte também `white-label/` para contexto do tema como um todo.

# Android

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Android — configurações específicas para geração de APK Android customizado com a identidade visual do estabelecimento.

## Para que existo

Permitir que cada cliente do Bakery tenha seu próprio aplicativo Android na Play Store com nome, ícones, cores e configurações de push notification (Firebase) personalizadas, sem precisar de uma build manual para cada um.

## A quem pertenço

Módulo de **Temas** — as configurações de Android são armazenadas como parte do tema (dentro do JSONB `assets.android`).

## Domínio imediato

- Nome do app e activity title
- Package name, application ID, namespace
- Custom URL scheme (deep linking)
- Version name e version code
- Ícones do launcher (upload via zip com todas as densidades mipmap)
- Firebase Cloud Messaging (google-services.json)
- Firebase Admin SDK (firebase-adminsdk.json)
- Validação de estrutura do zip de ícones

## Especificação

### Campos de configuração (dentro de `assets.android`)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `appName` | `String` | Nome do app na Play Store |
| `activityTitle` | `String` | Título da activity principal |
| `applicationId` | `String` | Application ID (ex: com.bakery.app) |
| `namespace` | `String` | Namespace do projeto |
| `packageName` | `String` | Package name |
| `versionName` | `String` | Versão (ex: 1.0.0) |
| `versionCode` | `Number` | Código numérico da versão |
| `customUrlScheme` | `String` | Esquema de deep linking (ex: meubakery://) |
| `googleServicesPath` | `String` | Caminho do arquivo google-services.json |
| `firebaseAdminsdkPath` | `String` | Caminho do arquivo firebase-adminsdk.json |
| `iconsZipPath` | `String` | Caminho do zip com ícones |

### Upload de assets Android

`POST /api/themes/android/upload` — multipart file.

**Arquivos aceitos**:
1. `google-services.json` — configuração do Firebase para Android
2. `firebase-adminsdk.json` — chave da service account do Firebase Admin
3. `icons.zip` — arquivo zip com ícones do launcher

**Validação do zip de ícones**:
- Deve conter diretórios mipmap para todas as densidades: `mipmap-mdpi`, `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi`, `mipmap-anydpi-v26`
- Cada diretório mipmap deve conter `ic_launcher.png` e `ic_launcher_round.png`
- Deve conter `drawable/ic_launcher_background.xml` e `drawable/ic_launcher_foreground.xml` (adaptive icons)

### FirebaseConfig

`FirebaseConfig.java` lê o caminho do `firebase-adminsdk.json` do tema ativo (`assets.android.firebaseAdminsdkPath`) e inicializa o Firebase Admin SDK para enviar push notifications.

### Integração com backend

Os assets Android são referenciados por caminho no tema mas o consumo principal é feito pelo **sistema de build de APK** (não faz parte do código-fonte do Bakery — é uma ferramenta externa que lê o tema e gera o APK customizado).

## Regras

1. **Ícones obrigatórios**: ao configurar um tema para Android, o zip de ícones deve conter todas as densidades mipmap exigidas, incluindo adaptive icons (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`)
2. **Firebase separado**: cada tenant tem seu próprio projeto Firebase. O `google-services.json` vincula o app ao projeto correto para FCM
3. **Versionamento**: `versionCode` deve ser incrementado a cada nova submissão à Play Store
4. **Deep linking**: o `customUrlScheme` permite que links como `meubakery://cardapio` abram diretamente no app

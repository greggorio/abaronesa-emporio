# Infraestrutura

> **Status**: ✅ Documentação "chão de fábrica" — reflete implementação real

---

## Visão Geral

Esta seção documenta a infraestrutura de produção do ecossistema Bakery, incluindo:

- **Arquitetura atual** (o que está em produção)
- **Procedimentos de deploy** (scripts e SOPs)
- **Monitoramento** (o que existe + roadmap)
- **Segurança** (SSL/TLS, certbot)
- **Escalabilidade** (limites e plano de crescimento)
- **Roadmap** (evolução planejada)

Guia editorial da secao:
- [GUIA_ESTILO.md](./GUIA_ESTILO.md)

---

## Documentos Principais

| Documento | Descrição |
|-----------|-----------|
| [arquitetura_atual.md](./arquitetura_atual.md) | **Comece aqui** — Arquitetura real em produção |
| [roadmap.md](./roadmap.md) | Roadmap de evolução da infraestrutura |

---

## Deploy

| Documento | Descrição |
|-----------|-----------|
| [deployment/proposta-docker-ci-cd-producao-emporio.md](./deployment/proposta-docker-ci-cd-producao-emporio.md) | **Arquitetura aprovada, ainda não implementada** para Docker, CI/CD, releases globais, deploy, rollback e condução por slices do Empório |
| [deployment/implementation/README.md](./deployment/implementation/README.md) | Acompanhamento das slices de implementação; S05 em execução para o contrato canônico de componentes |
| [deployment/release-control/README.md](./deployment/release-control/README.md) | Contrato estrutural dos seis componentes, dependências, resolvedor e gates de readiness; releases ainda não implementadas |
| [deployment/deploy-bakery.md](./deployment/deploy-bakery.md) | Deploy do ERP (cafe_erp) |
| [deployment/deploy-espresso.md](./deployment/deploy-espresso.md) | Deploy do Espresso |
| [deployment/procedimento-padrao.md](./deployment/procedimento-padrao.md) | Procedimento operacional padrão para qualquer deploy |

**Scripts**: `ops/deploy/`

---

## Monitoramento

| Tipo | Status | Documento |
|------|--------|-----------|
| **Health Check** | ✅ Implementado | [monitoring/README.md](./monitoring/README.md) |
| **Logs** | ✅ Docker logs | [monitoring/README.md](./monitoring/README.md) |
| **Uptime** | ⏳ Planejado | [roadmap.md](./roadmap.md) |
| **Alertas** | ⏳ Planejado | [roadmap.md](./roadmap.md) |
| **Backup** | ⏳ Manual | [monitoring/README.md](./monitoring/README.md) |

---

## Segurança

| Documento | Descrição |
|-----------|-----------|
| [security/ssl-certbot.md](./security/ssl-certbot.md) | Gerenciamento de SSL/TLS |

---

## Escalabilidade

| Documento | Descrição |
|-----------|-----------|
| [scalability/README.md](./scalability/README.md) | Limites atuais e plano de escalabilidade |

**Resumo**:
- **Atual**: 2 clientes, 1 VPS
- **Limite prático**: ~10 clientes por VPS
- **Próximo upgrade**: 4-6 clientes → VPS com mais recursos

## Regra de Atualizacao

Esta secao deve ser atualizada quando ocorrer qualquer uma das situacoes abaixo:

- mudanca de procedimento real de deploy
- mudanca de topologia, host, dominio ou portas publicas
- inclusao ou remocao de mecanismo de monitoramento, seguranca ou backup
- alteracao relevante de limite operacional ou estrategia de escalabilidade

Quando a mudanca afetar o procedimento operacional, o README e os guias em `deployment/`, `monitoring/`, `security/` ou `scalability/` devem ser atualizados juntos.

## Cobertura Atual

Leitura resumida do nucleo documental da secao:

- `arquitetura_atual.md`: fotografia da operacao real
- `deployment/`: guias de deploy especificos e SOP geral
- `monitoring/`: capacidade atual de observacao e limites
- `security/`: configuracao e manutencao de SSL/TLS
- `scalability/`: limites praticos e proxima etapa esperada
- `roadmap.md`: evolucao planejada da infraestrutura

---

## Estrutura de Diretórios

```
infrastructure/
├── README.md                        ← Este arquivo
├── arquitetura_atual.md             ← Estado atual da infraestrutura
├── roadmap.md                       ← Roadmap de evolução
├── deployment/
│   ├── deploy-bakery.md             ← Deploy do ERP
│   ├── deploy-espresso.md           ← Deploy do Espresso
│   └── procedimento-padrao.md       ← SOP geral de deploy
├── monitoring/
│   └── README.md                    ← Monitoramento atual
├── security/
│   └── ssl-certbot.md               ← SSL/TLS
└── scalability/
    └── README.md                    ← Escalabilidade
```

---

## Acesso ao Servidor

```bash
# SSH
ssh root@31.97.251.16

# Chave SSH já configurada via ssh-copy-id
```

---

## Links Relacionados

| Seção | Descrição |
|-------|-----------|
| [Arquitetura](../architecture/README.md) | Design do sistema e integrações |
| [Desenvolvimento](../development/README.md) | Setup e contribuição |

---

**Última atualização**: Março 2026

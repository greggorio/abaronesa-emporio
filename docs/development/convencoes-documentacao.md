# Convenções da Documentação

## Nomenclatura de Arquivos

- **Documentos em português**: Nomes em português (ex: `visao-geral.md`)
- **Documentos técnicos**: Podem usar inglês (ex: `technical-overview.md`)
- **Escopos**: Prefixo `escopo-` ou sufixo `_scope.md` (ex: `escopo-delivery.md`, `rewards_scope.md`)
- **Planos**: Prefixo `plano-` (ex: `plano-execucao.md`)
- **Propostas**: Prefixo `proposta-` (ex: `proposta-modulo.md`)

## Estrutura de Pastas

```
docs/
├── architecture/          # Arquitetura (como é projetado)
├── infrastructure/        # Infraestrutura (onde roda)
├── modules/               # Módulos funcionais (features)
├── integrations/          # Integrações externas
├── api-reference/         # Documentação de API
├── development/           # Guia para desenvolvedores
│   ├── component-analysis/   # Análise de componentes
│   └── testing/
└── assets/                # Mídia de referência
```

## Contribuindo

Para contribuir com a documentação:

1. Siga as convenções de nomenclatura
2. Atualize o README da seção se adicionar novos tópicos
3. Mantenha a documentação atualizada com o código
4. Use Markdown para formatação consistente

Veja mais em [development/README.md](./README.md)

# Guia de Estilo de `architecture`

## Objetivo

Manter a secao arquitetural consistente, objetiva e util para decisao tecnica.

## Convencoes

- separar sempre `estado atual`, `estado alvo`, `roadmap` e `gaps`
- evitar misturar decisao arquitetural com backlog operacional
- registrar mudancas decisorias em ADR quando alterarem fronteiras ou padroes estruturais
- preferir linguagem descritiva e verificavel, evitando arquitetura aspiracional nao evidenciada

## Ordem recomendada

1. contexto
2. leitura atual
3. leitura alvo
4. gaps
5. decisao ou implicacao

## Regra editorial

Quando uma mudanca for apenas descritiva, atualizar os documentos-base da secao.

Quando a mudanca alterar padrao ou direcao arquitetural, abrir ou atualizar ADR.

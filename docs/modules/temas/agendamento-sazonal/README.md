# Agendamento Sazonal

**Status**: EM_DESENVOLVIMENTO

## Quem sou

Sub-domínio de Agendamento Sazonal — programação de temas por período com controle de prioridade.

## Para que existo

Automatizar a troca de identidade visual conforme o calendário sazonal. No Natal o app aparece temático; na Páscoa, outro tema entra automaticamente.

## A quem pertenço

Módulo de **Temas** — o agendamento controla *quando* cada tema fica ativo.

## Domínio imediato

- Criação de assignments (tema + período + prioridade)
- Ativação automática por período vigente
- Substituição: novo agendamento desativa anteriores
- Prioridade entre temas concorrentes
- Fallback para tema PUBLISHED

## Coerente / Desalinhado

- **Coerente**: integração com ThemeService.getActiveTheme(); prioridade funcional; fallback hierárquico completo
- **Desalinhado**: não há interface de calendário visual; não há temas sazonais pré-definidos no sistema (criação manual); não há preview do período agendado

## Caminhos de exploração

Leia `agendamento-sazonal.md` para detalhes. Veja `white-label/` para entender como os temas são criados.

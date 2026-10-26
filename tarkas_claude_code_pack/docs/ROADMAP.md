# Roadmap de implementação

> Histórico: as fases 0 e 1 abaixo já foram feitas. O app foi construído em Kotlin + Jetpack Compose, não em Flutter, e já passou da versão 1.7. Os passos com Flutter ficam só como registro do plano original.

## Fase 0 — auditoria (primeira tarefa)
- Inspecionar repositório e Git.
- Verificar Flutter, Dart, Java/JDK, Android SDK, adb e dispositivos.
- Executar `flutter doctor -v` quando Flutter estiver instalado.
- Registrar ambiente e bloqueios.
- Não implementar funcionalidades antes de concluir auditoria.

**Saída:** relatório de ambiente e decisão do scaffold.

## Fase 1 — scaffold
- Criar projeto Flutter somente se não houver projeto existente.
- Confirmar package/application ID provisório e mínimo SDK com base na versão de dependências selecionada.
- Criar tema adaptável e navegação básica.
- Adicionar README e testes de smoke.
- Executar analyze, testes e build debug.

**Saída:** app inicial executável.

## Fase 2 — pesquisa musical e prova técnica
- Preencher relatório de `MUSIC_PROVIDER_RESEARCH.md`.
- Escolher fonte autorizada.
- Fazer prova mínima de reprodução e testar possibilidade de efeitos na sessão.
- Registrar dispositivo/Android/rota.
- Decidir se o motor interno pode satisfazer requisitos do MVP.

**Saída:** decisão documentada, sem integração especulativa.

## Fase 3 — player e dados locais
- Player, estado de faixa e erros.
- Preferências e perfis persistentes.
- Estados vazios/offline.
- Testes.

## Fase 4 — áudio
- Diagnóstico de capacidades.
- Controles apenas quando suportados.
- Equalizador/perfis vinculados à sessão quando tecnicamente possível.
- Testes físicos e documentação de limitações.

## Fase 5 — curiosidades
- Modelo de dados com fonte e verificação.
- Fonte de dados confiável ou curadoria inicial.
- UI breve e contexto da faixa.
- Fallback honesto quando não houver informação.

## Fase 6 — assistente e ferramentas
- Interface de provedor.
- Modo mock/regras locais.
- Timers/lembretes.
- Validação e confirmação de ações.
- Pesquisa de modelo gratuito/local sem obrigar serviço pago.

## Fase 7 — integração e beta
- Integrar apenas provedor musical aprovado.
- Revisar privacidade, segurança, acessibilidade e consumo.
- Testar em dispositivos reais.
- Gerar APK/AAB conforme plano de distribuição.
- Documentar limitações conhecidas.

## Gate de cada fase
Não avançar se build/testes essenciais falharem ou se a capacidade central da fase não foi comprovada. Reportar bloqueios em vez de contorná-los com comportamento falso.

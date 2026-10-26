# Registro de decisões

## Aprovadas
### D-001 — Android como plataforma inicial
**Decisão:** priorizar Android.
**Motivo:** foco de produto definido pelo proprietário.
**Consequência:** APIs, UX e testes devem priorizar Android.

### D-002 — Claude Code como agente de desenvolvimento
**Decisão:** usar Claude Code como agente principal, guiado por `CLAUDE.md` e documentação.
**Consequência:** trabalho incremental, testes e relatórios são obrigatórios.

### D-003 — Pesquisa antes de escolher fonte musical
**Decisão:** fonte musical inicial permanece em aberto até pesquisa de APIs, termos, custo e viabilidade de DSP.
**Consequência:** não implementar integração de streaming presumida.

### D-004 — IA com prioridade a custo zero
**Decisão:** priorizar funções locais, modo mock e opções gratuitas/localmente executáveis; nenhuma API paga obrigatória para iniciar.
**Consequência:** provedor de IA intercambiável e modo degradado.

### D-005 — Tema adaptável
**Decisão:** respeitar tema claro/escuro do sistema, com possibilidade de preferência futura.

### D-006 — Áudio interno primeiro
**Decisão:** validar reprodução e processamento dentro do app antes de investigar controle externo.
**Consequência:** não prometer equalização universal.

## Em aberto
- Dispositivo de teste principal.
- Versões do Android Gradle Plugin, Kotlin e SDK (hoje em `gradle/libs.versions.toml`).
- Application ID final.
- Fonte musical inicial após pesquisa.
- Estratégia definitiva de IA gratuita/local.
- Biblioteca de DSP e capacidades reais de equalização.
- Necessidade de backend.
- Política de retenção do histórico e conversas.

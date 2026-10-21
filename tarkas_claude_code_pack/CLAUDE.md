# T.A.R.K.A.S. — Instruções permanentes para Claude Code

## Missão
Desenvolver o T.A.R.K.A.S., um aplicativo Android de música, configuração de áudio e assistência contextual. Prioridades: confiabilidade, honestidade sobre capacidades do Android, privacidade, baixo custo e implementação incremental.

## Fonte de verdade
- `README.md`: visão rápida e como executar.
- `docs/PRODUCT.md`: visão do produto e princípios.
- `docs/MVP.md`: escopo e critérios de aceitação do MVP.
- `docs/ARCHITECTURE.md`: arquitetura e decisões técnicas.
- `docs/MUSIC_PROVIDER_RESEARCH.md`: pesquisa e critérios para escolher fonte musical.
- `docs/AUDIO_ENGINE.md`: escopo e limitações de áudio.
- `docs/AI_ASSISTANT.md`: IA, ferramentas e política de custo.
- `docs/UI_UX.md`: interface e navegação.
- `docs/SECURITY.md`: privacidade e segurança.
- `docs/ROADMAP.md`: fases de execução.
- `docs/DECISIONS.md`: decisões registradas e questões abertas.

Se houver conflito, não adivinhe: identifique-o, explique opções e proponha uma decisão. Atualize os documentos quando uma decisão aprovada mudar.

## Regras de trabalho
1. Antes de alterar código, inspecione o repositório, arquivos existentes, versões instaladas e estado do Git.
2. Não sobrescreva nem apague trabalho existente sem explicar o impacto e obter autorização.
3. Trabalhe em pequenas etapas verificáveis. Antes de uma mudança ampla, apresente plano curto.
4. Não implemente o produto inteiro de uma só vez. Priorize a próxima tarefa do `docs/ROADMAP.md`.
5. Após cada etapa, execute testes, análise estática e build apropriados. Informe exatamente o que foi executado e o resultado.
6. Nunca alegue que um teste, build, integração, timer ou recurso funcionou sem tê-lo executado/verificado.
7. Não adicione dependências sem explicar finalidade, manutenção, licença e impacto no tamanho/segurança.
8. Prefira APIs estáveis e documentação oficial. Verifique a compatibilidade das versões antes de usar uma API.
9. Não inclua segredos, tokens, chaves API, senhas ou dados pessoais no código, logs, testes, screenshots ou Git.
10. Não crie scraping, reprodução não autorizada, download de streams protegidos ou formas de contornar termos de serviços musicais.
11. Não prometa controle global de áudio no Android. Faça distinção explícita entre áudio reproduzido pelo app, efeitos de uma sessão de áudio e controle de outros apps.
12. Não use `Equalizer` com sessão zero como solução global; essa abordagem é descontinuada e não representa um equalizador universal do sistema.
13. Qualquer ferramenta de IA que altere estado deve ter esquema validado, parâmetros limitados, autorização explícita quando necessária e resultado confirmado pela aplicação.
14. Para funcionalidades ainda não integradas, use interfaces e estados de indisponibilidade honestos. Não simule sucesso.
15. A aplicação deve funcionar sem IA e sem credenciais de serviços externos nas funções locais essenciais.
16. Mantenha UI adaptável ao tema do sistema (claro/escuro), acessibilidade básica e textos em português brasileiro.
17. Faça alterações focalizadas. Não reformate arquivos sem necessidade.
18. Ao concluir uma tarefa, responda com: resumo, arquivos alterados, comandos/testes executados, limitações conhecidas e próximo passo sugerido.

## Processo obrigatório para cada tarefa
1. Inspecionar o estado atual.
2. Declarar objetivo e plano de implementação.
3. Implementar a menor solução funcional.
4. Testar e revisar diff.
5. Atualizar documentação relevante.
6. Relatar resultados sem exageros.

## Stack-alvo (a validar no ambiente)
- Flutter/Dart para UI e lógica de apresentação.
- Kotlin para integrações Android específicas.
- AndroidX Media3 para reprodução e sessão de mídia quando aplicável.
- Persistência local inicialmente; backend somente quando houver necessidade demonstrada.
- Integração de IA atrás de uma interface de provedor; padrão inicial gratuito/offline ou modo indisponível explícito.
- Nenhum serviço pago é requisito para iniciar o MVP.

## Primeira tarefa autorizada
Comece apenas com auditoria do ambiente e scaffold mínimo:
- verificar Flutter, Dart, Android SDK, Java/JDK, Git e dispositivos disponíveis;
- inspecionar se o diretório atual já contém projeto;
- se não existir projeto, criar scaffold Flutter com nome de pacote/documentação provisórios, sem implementar integrações;
- executar `flutter doctor`, `flutter analyze` e testes iniciais quando aplicáveis;
- documentar resultados e propor a próxima tarefa.
Não implementar player, DSP, IA ou integrações musicais antes de concluir essa auditoria e confirmar os critérios do scaffold.

## Critério de conclusão
Uma tarefa só está concluída quando o código, testes e documentação correspondentes foram verificados. Se algo não puder ser verificado no ambiente, marque explicitamente como pendente.

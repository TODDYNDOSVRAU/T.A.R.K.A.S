# Arquitetura técnica

## Direção inicial
Aplicativo Android nativo de distribuição, com Flutter/Dart para interface e lógica de apresentação e Kotlin para integrações Android que não estejam adequadamente expostas pelo Flutter.

Stack candidata:
- Flutter/Dart: UI e estado.
- Kotlin: APIs nativas Android.
- AndroidX Media3: player e sessão de mídia, se confirmada como adequada à fonte escolhida.
- Persistência local: escolher solução simples após revisar compatibilidade e necessidade; evitar adicionar banco complexo sem caso de uso.
- Git: controle de versão.
- Testes unitários/widget e testes de integração quando possível.

As versões exatas devem ser selecionadas durante a auditoria, verificando compatibilidade e estabilidade. Não fixar versões por suposição.

## Camadas
1. Presentation: telas, navegação, acessibilidade, tema.
2. Application: casos de uso e coordenação de fluxos.
3. Domain: entidades e contratos independentes de SDK.
4. Infrastructure: player, armazenamento, APIs, timers e provedores.
5. Android native bridge: Kotlin/Platform Channels somente para capacidades nativas necessárias.

## Módulos lógicos
- `home`: resumo e atalhos.
- `music`: estado do player, metadados e fonte.
- `audio`: capacidades, perfis e parâmetros suportados.
- `curiosities`: dados, fontes e histórico de exibição.
- `assistant`: conversa e roteador de ferramentas.
- `reminders`: timer e lembretes locais.
- `settings`: tema, privacidade e preferências.
- `core`: erros, logging seguro, contratos e utilitários.

A estrutura de diretórios pode ser ajustada após inspeção do scaffold; evitar abstrações excessivas.

## Contratos importantes
- `MusicProvider`: capacidades, pesquisa e reprodução, apenas se legalmente suportado.
- `AudioCapabilitiesService`: reporta capacidades verificadas.
- `AudioProfileRepository`: salvar/carregar perfis locais.
- `CuriosityRepository`: obter curiosidade e fonte por identificador de faixa.
- `AssistantProvider`: interface para modelo local/remoto/mock.
- `ToolExecutor`: valida e executa comandos permitidos.
- `ReminderScheduler`: agenda/cancela notificações conforme APIs Android.

## Fluxo de dados
UI -> caso de uso -> contrato de domínio -> implementação -> resultado tipado -> UI.
Não fazer chamadas de rede diretamente dentro de widgets.
Não armazenar tokens de provedor no repositório nem embutir chaves secretas no APK.

## Backend
Não criar backend na primeira etapa. Adicioná-lo apenas se a solução de IA escolhida ou sincronização exigir. Se uma chave secreta de API for necessária, usar backend/proxy seguro; não colocar chave privada no aplicativo.

## Decisões técnicas
Registrar mudanças significativas em `DECISIONS.md`, com contexto, alternativas, decisão e consequências.

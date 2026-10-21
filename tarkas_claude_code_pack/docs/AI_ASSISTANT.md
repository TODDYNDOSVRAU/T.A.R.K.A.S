# Assistente de IA

## Meta
Assistente contextual com custo inicial zero ou próximo de zero, sem exigir API paga para abrir ou usar as funções locais essenciais.

## Estratégia
- Definir `AssistantProvider` para permitir trocar implementação.
- Começar com modo mock determinístico para UI/testes e comandos locais baseados em regras.
- Pesquisar opção de modelo local ou camada gratuita de provedor; tratar limites e termos como variáveis.
- Não prometer que uma API terá plano gratuito permanente.
- Não baixar modelos grandes automaticamente sem consentimento, espaço e avaliação de compatibilidade.
- Se não houver provedor disponível, informar claramente que conversa generativa está indisponível; timers e configurações locais devem continuar funcionando.

## Ferramentas permitidas inicialmente
- `create_timer(duration)`: duração positiva e limitada.
- `cancel_timer(timer_id)`.
- `create_reminder(title, datetime)`: validar data, permissões e fuso local.
- `get_current_track()`.
- `get_audio_capabilities()`.
- `apply_audio_profile(profile_id)`: somente parâmetros suportados.
- `get_music_curiosity(track_id)`: retorna conteúdo e fontes existentes/verificáveis.

## Regras de execução
- O modelo propõe uma chamada; a aplicação valida e executa.
- Validar tipos, limites, autorização e estado atual.
- Pedir confirmação antes de ações externas, destrutivas, sensíveis ou com efeitos não óbvios.
- Não afirmar sucesso antes de receber resultado real.
- Não permitir execução arbitrária de código, shell, SQL ou URLs.
- Usar lista permitida de ferramentas.
- Evitar enviar histórico ou dados pessoais desnecessários ao provedor.
- Tratar saída do modelo como dado não confiável.

## Curiosidades
- Priorizar 40–90 palavras, sem transformar em artigo.
- Guardar título/identificador da faixa, texto, fontes, data e estado de verificação.
- Separar fato documentado de interpretação.
- Nunca inventar citações ou URLs.
- Se não houver fonte confiável, mostrar “Não encontrei uma fonte confiável para esta curiosidade”.
- Oferecer aprofundamento sob demanda.

## Timers e lembretes
- Persistir agendamentos localmente.
- Explicar limitações de notificações e otimização de bateria do Android.
- Solicitar permissões apenas no momento apropriado.
- Não alegar execução exata se o sistema operacional não garantir exatidão.

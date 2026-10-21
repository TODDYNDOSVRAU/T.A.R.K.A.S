# Áudio e sistemas multicanais

## Princípio
Android não fornece um equalizador universal capaz de processar o áudio de todos os outros aplicativos. O comportamento varia por versão, fabricante, efeito, sessão de áudio, rota e hardware.

## Fases
### Fase A — diagnóstico
- Detectar versão Android e dispositivo/rota quando APIs permitirem.
- Documentar canais e formatos realmente suportados.
- Criar tela de capacidades sem prometer recursos não detectados.
- Registrar modelo do dispositivo, versão do sistema e rota em relatórios de teste, evitando identificadores pessoais.

### Fase B — áudio interno
- Usar fonte autorizada/local.
- Implementar player.
- Avaliar API de equalizador/efeitos vinculados à sessão de áudio ou DSP próprio.
- Confirmar se efeito está ativo e em qual sessão/rota.
- Salvar perfis de parâmetros validados.

### Fase C — saídas avançadas
- Testar dispositivos USB, HDMI, Bluetooth e receptores, conforme hardware disponível.
- Diferenciar número de canais do conteúdo, canais do fluxo e canais efetivamente entregues à saída.
- Não alegar 5.1 real sem teste de canal verificável.
- Investigar processamento DSP próprio apenas se o pipeline de reprodução e licenças permitirem.

### Fase D — controle externo
- Pesquisar APIs oficiais e permissões para cada caso.
- Não tentar capturar/processar áudio de terceiros por meios não autorizados.
- Apresentar como indisponível qualquer controle global não suportado.

## Segurança e qualidade do sinal
- Evitar clipping; validar limites e ganho.
- Usar parâmetros limitados e valores padrão seguros.
- Não executar alocação pesada ou trabalho de rede no callback de áudio.
- Testar latência, consumo, estabilidade, troca de rota, fones e interrupções.
- Fornecer bypass/desativação de efeitos quando possível.

## Aceitação
- A UI reflete capacidades reais.
- Perfis inválidos são rejeitados.
- Falhas de efeitos não derrubam o player.
- Testes documentam dispositivo, Android, rota e resultado.

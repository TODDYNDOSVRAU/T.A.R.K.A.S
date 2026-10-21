# Escopo do MVP

## P0 — obrigatório
- Aplicativo Android instalável.
- UI em português brasileiro, tema claro/escuro adaptável ao sistema.
- Navegação entre Início, Música, Áudio, Curiosidades e Assistente.
- Seleção de fonte musical somente depois da pesquisa técnica/licencial documentada.
- Player mínimo: reproduzir/pausar, progresso quando suportado, faixa atual e estado de erro.
- Configurações e perfis de áudio persistidos localmente.
- Diagnóstico claro de capacidades detectadas; não mostrar controles não suportados como funcionais.
- Curiosidade ligada à faixa atual, texto curto e fonte/URL quando disponível.
- Assistente com modo offline/indisponível e interface de provedor substituível.
- Timer e lembrete local, com permissões e restrições Android explicadas.
- Testes automatizados para lógica de domínio e comandos.
- Tratamento de ausência de rede, permissão ou provedor.

## P1 — após validar P0
- Integração com um serviço musical que permita legalmente a experiência desejada.
- Pesquisa musical no provedor escolhido.
- Favoritos e histórico.
- Provedor de IA gratuito ou execução local, se requisitos de qualidade/desempenho forem atendidos.
- Controles de áudio nativos adicionais específicos do dispositivo.

## P2 — fora do primeiro lançamento
- Múltiplos serviços de streaming em uma única interface.
- Controle de áudio de outros aplicativos.
- DSP multicanal avançado (crossover, delay por canal, upmix/downmix avançado).
- Sincronização entre dispositivos e conta na nuvem.
- Voz sempre ativa.
- Correção acústica com microfone.

## Critérios de aceitação gerais
- Build de debug concluído em ambiente Android compatível.
- `flutter analyze` sem erros; exceções justificadas/documentadas.
- Testes automatizados passando.
- App informa falhas e limitações em linguagem clara.
- Nenhum segredo no código ou no histórico Git.
- Nenhuma reprodução/download não autorizado de conteúdo.

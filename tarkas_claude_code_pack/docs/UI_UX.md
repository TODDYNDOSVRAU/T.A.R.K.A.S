# UI/UX

## Direção
Visual técnico, limpo e acolhedor. Tema adaptável às preferências do sistema (claro/escuro); sem fixar paleta final antes do protótipo.

## Navegação principal
- Início: faixa atual/estado do player, atalhos e curiosidade disponível.
- Música: fonte, pesquisa e reprodução.
- Áudio: capacidades detectadas, perfil ativo e controles suportados.
- Curiosidades: informação da faixa, fontes e favoritos/histórico quando implementados.
- Assistente: conversa, ações e estado de disponibilidade da IA.
- Configurações: tema, privacidade, permissões e diagnóstico.

## Estados obrigatórios
- Carregando.
- Vazio/sem faixa.
- Sem conexão.
- Permissão negada.
- Provedor indisponível.
- Erro recuperável.
- Sucesso confirmado.
- Recurso não suportado no dispositivo.

## Acessibilidade
- Contraste adequado em ambos os temas.
- Alvos de toque confortáveis.
- Rótulos semânticos para controles.
- Não depender apenas de cor para comunicar estado.
- Respeitar tamanho de fonte do sistema sempre que possível.
- Textos curtos e português brasileiro.

## Regras
- Não apresentar botão ativo para recurso que não funciona.
- Explicar por que recurso está indisponível e, quando possível, como habilitá-lo.
- Não mostrar curiosidade como fato confirmado sem fonte.

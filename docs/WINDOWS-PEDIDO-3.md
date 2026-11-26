# Windows: pedido 3, refinamento do player (dono, 2026-11-18)

Vídeo de referência: `C:\Users\todyn\.claude\uploads\3edb5160-e674-4923-bd1a-ee3414631a61\16529e6e-20261118-1129-45.8496520.mp4` (mostra o problema atual). Dono: win-player (`player/*`). Trabalhar sobre o código existente, sem recriar o player.

## Requisitos
1. **Animações:** troca de faixa com movimento vertical sutil (próxima desce/sobe, anterior o contrário), entrada e saída suaves de capa, título e artista. Movimento 150–300 ms, ease-out, sem sobreposição nem salto de layout, sem travar.
2. **Tela expandida:** corrigir a abertura (clique na música ou no player). Capa, título, artista e dados organizados; nada sobreposto, cortado ou desalinhado; respeita o tamanho da janela; transição suave entre minimizado e expandido.
3. **Minimizar:** com a tela expandida aberta, clicar de novo no player do canto inferior esquerdo a minimiza, com animação de fechamento. Nunca fica presa; a reprodução não para.
4. **Capa ausente no player inferior esquerdo:** achar a causa real (URL da API chega? é repassada ao estado? descartada na troca? carregamento assíncrono? fallback trocando a imagem? cache?). A capa deve ser a da API para a faixa tocando, atualizar a cada faixa, sem a da faixa anterior, sem espaço vazio, sem salto de layout. Nada de imagem genérica para esconder o erro.
5. **Testes:** tocar, pausar/continuar, próxima, anterior, troca rápida, animações, expandir, minimizar pelo player inferior esquerdo, reprodução contínua ao minimizar, capas nos dois estados, redimensionar, alinhamento, sem erro de console. Testes de unidade para a lógica (estado expandir/minimizar, seleção de capa, direção da animação).

## Design (importante)
Sem Liquid Glass, sem blur exagerado, sem copiar o visual dos vídeos de referência anteriores. Interface própria, limpa: hierarquia, espaçamento, tipografia, capas bem apresentadas, contraste. Grade T.A.L. e tokens `Grid` / `GridText`.
Nota do shell: o fundo borrado da capa (`ShellBackdrop`) e o vidro (`glass()`) foram feitos antes; se atrapalharem o novo visual, o dono do shell (win-geral) ajusta; avise.

## Regras
Preservar a arquitetura e as funções existentes; sem dados fictícios; sem mudar a integração com a API sem necessidade; compatível com Windows; corrigir a causa, não o sintoma.

## Relatório final (obrigatório)
A. Alterações (componentes, animações, como expandir/minimizar ficou). B. Problemas e causas. C. Testes e resultados, o que ainda falha. D. Pendências e limitações.

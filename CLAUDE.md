# T.A.R.K.A.S.

Segue `C:\dev\CLAUDE.md` (identidade T.A.L.).

## Várias sessões no mesmo checkout

O trabalho está dividido em sessões por tópico: Player, Biblioteca, Equalizador, Assistente. Elas editam este mesmo checkout. Antes de editar ou compilar, leia `.claude/sessoes/QUADRO.md`: ele diz de quem é cada arquivo, como reservar arquivos compartilhados e como compilar (`.claude/sessoes/gradle.sh`, com trava). Nunca use `git checkout/restore/stash/reset/clean` nem commite sem o usuário pedir.

## Interface: a grade T.A.L. vale em toda tela

Toda tela segue a grade T.A.L. (`C:\dev\CLAUDE.md`, "Layout: the T.A.L. grid"; regras completas na skill `tal`, `references/ui.md` seção 6). No código, use os tokens `Grid` e `GridText` de `com.example.ui.theme` e as cores de `MaterialTheme.colorScheme`; nenhum dp, sp ou hex solto numa tela. O app tem a opção de seguir as cores do celular (Material You, desligada por padrão), e cor fixa não acompanha essa escolha. Antes de mostrar uma tela, meça em tema claro e escuro, retrato e paisagem.

## Equalizador: decisões do dono (2026-11-15)

O estilo aprovado é o do exemplo `.claude/sessoes/eq-proposta/exemplo-equalizador.html`; a especificação para Compose está em `.claude/sessoes/eq-proposta/SPEC-compose.md`. Não mude estas decisões sem o dono pedir:

- **Ordem da tela:** título com a rota e o liga/desliga 96x48; predefinição 144 + Correção 96 + menu 48; curva 192; bandas; dock com Gráfico/Paramétrico 192 e Tarkas Spatial; barra de folga.
- **Duplo toque redefine** uma banda, um fader ou um botão giratório, sempre.
- **Faders** ficam embaixo da própria frequência, no mesmo eixo logarítmico da curva.
- **Cor da banda:** neutra em 0 e mais forte quanto maior o ajuste, igual para corte e reforço.
- **Paramétrico:** as bandas são peças 96x48 em grade, com uma peça "+" para adicionar. Tocar numa peça abre o editor como balão sobre a grade (0,75x dela, peças atrás levemente desfocadas); o lixeiro fica no balão e remover tem desfazer.
- **Predefinições:** o botão abre a lista com todas; ao lado dele fica o botão de correção dos fones.
- **Tarkas Spatial:** ocupa no dock o lugar do antigo "segurar: sem EQ". A folha tem o palco 3D com a cabeça de costas: desligado mostra o fone; Sutil e Padrão, o fone vira falantes que se afastam um pouco; Amplo, dois falantes à distância das caixas; Cinema 5.1, eles viram as seis caixas.
- **O que saiu da tela principal** (pré-amplificação, graves, agudos, limitador, espectro) mora na folha Ajustes; nenhuma função foi removida.

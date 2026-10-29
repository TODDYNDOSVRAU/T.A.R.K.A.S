# Protótipo do motor espacial (fase 0)

Implementação de referência, em Python/NumPy, do algoritmo descrito em
[`AUDIO_ESPACIAL_TARKAS.pdf`](../../tarkas_claude_code_pack/docs/AUDIO_ESPACIAL_TARKAS.pdf) (seção 8).
Serve para ouvir e ajustar o algoritmo antes de portá-lo para C++ (fase 1). O app não usa este código.

## O que faz

A cada 512 amostras (10,7 ms a 48 kHz), o motor divide o áudio em 1.025 bins de frequência e calcula, para
cada bin:

1. o pan e a coerência entre esquerda e direita, ou seja, onde aquela frequência está no mix;
2. a fração direta e a fração de ambiente;
3. a direção 3D de destino (alargamento do palco, elevação dos agudos, âncora da voz, graves intactos);
4. a HRTF daquela direção, aplicada só àquele bin.

A HRTF usada é um modelo paramétrico de cabeça esférica (Brown & Duda) mais uma pista de orelha. Ele não
precisa de nenhuma base de dados. A grade e a busca por direção são as mesmas que o núcleo C++ vai usar com
uma base SOFA real, como a SADIE II.

## Uso

```bash
pip install -r requirements.txt
python demo.py
python render.py "minha musica.flac" --preset padrao --ab --plot
python -m unittest discover -s tests -v
```

- `demo.py` cria um mix com posições conhecidas e grava em `out/demo/` todos os presets, o original alinhado e
  uma simulação de head tracking.
- `render.py` processa qualquer WAV. Mp3, flac e m4a também funcionam se o `ffmpeg` estiver no PATH.
  - `--ab` grava também o original com a mesma latência e o mesmo limitador, para um A/B justo.
  - `--plot` gera o mapa de onde cada banda está.
- Ouça com fones. O motor foi feito para binaural.

| Preset | Largura (w) | Altura máx. | Ambiente no teto | Intensidade |
|---|---|---|---|---|
| `sutil` | 1,3 | 12° | 0,3 | 0,7 |
| `padrao` | 1,8 | 25° | 0,5 | 1,0 |
| `amplo` | 2,4 | 35° | 0,8 | 1,0 |

## Números medidos

- Tempo por quadro em NumPy: cerca de 0,44 ms, de um orçamento de 10,7 ms. Em C++ com NEON deve ficar bem abaixo.
- Latência: 1.024 + 64 + 32 amostras, cerca de 23 ms a 48 kHz.
- Coloração no mix de demo: no máximo +2,9 dB e no mínimo −7 dB por banda ERB. O −7 dB fica em 7,3 kHz e é o
  notch de orelha do som frontal. É o primeiro ponto a calibrar ouvindo.

## Diferenças em relação ao PDF, encontradas no protótipo

- **Fonte toda num lado só:** com sinal só na esquerda, a coerência vira 0/0 e o bin seria tratado como ambiente.
  O protótipo usa `D = max(ψ, dominância)^γ`, onde `dominância = |Φ_LL − Φ_RR| / (Φ_LL + Φ_RR)`.
- **Mistura seco/espacial:** somar os dois sinais direto criava filtro pente, com buracos de até −13 dB.
  `blend()` interpola magnitude e fase separadamente.
- **ITD:** o modelo dá cerca de 0,78 ms nos graves. O valor de Woodworth (0,656 ms) vale para altas frequências.
  Em baixas frequências o valor esperado é de cerca de 3a/c ≈ 0,765 ms (Kuhn, 1977).

## Próximos passos (fase 0 → 1)

1. Escuta A/B com músicas reais: preset preferido, profundidade do notch, largura máxima.
2. Trocar o modelo esférico por uma HRTF medida (SADIE II KU100), depois de confirmar a licença.
3. Portar `engine.py` para C++. Os testes em `tests/` viram a especificação do núcleo nativo.

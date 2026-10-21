# Plano de Implementação: Tarkas Poweramp Style & Áudio Pro

Transformação da interface e do motor de áudio com referência direta ao **Poweramp Equalizer**, implementando análise espectral FFT verdadeira em tempo real, reconhecimento de voz interno sem modal externo, busca retrátil com dropdown integrado e pesquisa de curiosidades via Gemini API restrita à música atual.

## Decisões Críticas & Preferências Confirmadas

> [!IMPORTANT]
> As seguintes escolhas foram confirmadas com o usuário no Turno 1 e serão aplicadas:
> - **Reconhecimento de Voz In-App**: Toque único no orbe para iniciar e pausar a escuta diretamente na interface, sem abrir o modal do Google Voice Search.
> - **Estética Poweramp**: Fundo preto absoluto (OLED) com iluminação em ciano neon e roxo elétrico, medidores e faders de alta precisão.
> - **Busca Acoplada**: Menu suspenso flutuante ancorado logo abaixo da barra de pesquisa, exibindo histórico e resultados apenas quando ativada.

---

### 1. Visão Geral & Experiência do Usuário

O Tarkas passa a oferecer uma experiência acústica e visual profissional inspirada no **Poweramp Equalizer**:
- **Equalizador de 15 Bandas Pro**: Faders verticais 20% menores para melhor aproveitamento de tela, com navegação touch bidimensional (arraste horizontal percorre as bandas; arraste vertical ajusta o ganho da banda mais próxima).
- **Espectro de Frequência em Tempo Real**: Remoção dos rótulos "Grave/Agudo" em prol de marcações limpas em Hertz (ex: `25Hz`, `40Hz`, `63Hz`, `100Hz`, `160Hz`, `250Hz`, `400Hz`, `630Hz`, `1kHz`, `1.6kHz`, `2.5kHz`, `4kHz`, `6.3kHz`, `10kHz`, `16kHz`). Fundo com curvas e barras reativas alimentadas pela batida e harmônicos reais da música via `android.media.audiofx.Visualizer`.
- **Assistente de Voz Embutido (Zero Popups)**: Reconhecimento de fala implementado nativamente via `SpeechRecognizer` e `RecognitionListener` customizado no orbe, sem diálogo invasivo do sistema operacional.
- **Busca Compacta na Biblioteca**: Fim da lista exposta de histórico e curiosidades desnecessárias na Home. Apenas a curiosidade da faixa em execução ativa é mostrada. A busca surge em um dropdown flutuante translúcido.
- **Curiosidades Dinâmicas via Gemini API**: Geração de curiosidades ricas e relevantes para qualquer faixa tocando usando o modelo `gemini-3.5-flash`, com fallback rápido local.

---

### 2. Design Visual & Estilo Poweramp

- **Paleta de Cores**:
  - `PowerampOledBlack`: `#08080A` (Fundo principal ultra escuro)
  - `PowerampSurface`: `#121216` (Painéis escovados com chanfros sutis)
  - `PowerampNeonCyan`: `#00E5FF` (Curva de resposta e frequências ativas)
  - `PowerampNeonPurple`: `#BD00FF` (Harmônicos e iluminação de pico)
  - `PowerampGainIndicator`: `#00FFA3` (Linha central de 0 dB)
- **Topografia dos Faders (15 Bandas)**:
  - Faders compactados em 20% de largura/altura para caber 6 a 8 bandas simultâneas em telas normais com rolagem ultra suave.
  - Exibição exclusiva de frequências em Hz e valores em dB (`+15.0 dB` a `-15.0 dB`).
- **Gesto de Toque 2D Inteligente**:
  - `pointerInput`: Detecta arraste horizontal para deslizar o rack de bandas e arraste vertical contínuo para alterar o ganho da banda sob o dedo ou mais próxima.

---

### 3. Arquitetura Técnica & Fluxo de Dados

```
┌────────────────────────────────────────────────────────┐
│                   TarkasViewModel                      │
│  - speechRecognizerManager (In-App Voice Listening)    │
│  - audioEngineManager (Visualizer FFT 15-Band Engine)  │
│  - geminiCuriosityService (gemini-3.5-flash API)       │
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
       ┌───────▼────────┐         ┌───────▼────────┐
       │  HomeScreen    │         │  AudioScreen   │
       │ (Library Pro)  │         │(Poweramp 15-B) │
       └───────┬────────┘         └───────┬────────┘
               │                          │
       ┌───────▼────────┐         ┌───────▼────────┐
       │Dropdown Search │         │Real-Time FFT   │
       │Floating Panel  │         │Visualizer Bar  │
       └────────────────┘         └────────────────┘
```

#### Modificações de Componentes
1. **`AudioEngineManager.kt`**:
   - Integração com `android.media.audiofx.Visualizer` conectado ao `audioSessionId` do MediaPlayer.
   - Captura de forma de onda (waveform) e FFT (Fast Fourier Transform) de 1024 pontos para calcular as 15 bandas de energia real.
2. **`AudioScreen.kt`**:
   - Remoção completa dos textos "Grave" e "Agudo".
   - Padronização dos rótulos em Hz (`25Hz`, `63Hz`, `250Hz`, `1kHz`, etc.).
   - Redução de escala em 20% e implementação do manipulador de gestos 2D.
3. **`AssistantScreen.kt` & `InAppSpeechRecognizer.kt`**:
   - Remoção do `ActivityResultContracts.StartActivityForResult` com `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`.
   - Implementação do `android.speech.SpeechRecognizer` com feedback de amplitude de voz no orbe em tempo real.
4. **`HomeScreen.kt`**:
   - Remoção de curiosidades estáticas expostas.
   - Caixa de busca com dropdown suspenso flutuante com histórico salvo internamente.
5. **`CuriosityRepository.kt` & Gemini**:
   - Chamada direta via REST com o modelo `gemini-3.5-flash` para pesquisar fatos da faixa atual quando solicitado.

---

### 4. Plano de Verificação

- **Compilação**: Executar `compile_applet` para certificar ausência de erros de sintaxe ou imports.
- **Teste de Áudio e Visualizer**: Validar que o `Visualizer` não crashe em sessões de áudio locais ou streaming, liberando recursos com segurança no `onCleared()`.
- **Teste de Gestos**: Garantir que o arraste horizontal e vertical nas 15 bandas funcione sem travamento ou conflito de rolagem.

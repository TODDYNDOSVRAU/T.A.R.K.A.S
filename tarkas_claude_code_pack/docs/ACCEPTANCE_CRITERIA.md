# Critérios de aceitação do MVP

## A. Ambiente e build
- [ ] Auditoria de ambiente documentada.
- [ ] Projeto compila em debug no ambiente escolhido.
- [ ] `flutter analyze` sem erros.
- [ ] Testes automatizados passam.
- [ ] README descreve como executar.

## B. Interface
- [ ] Navegação principal acessível.
- [ ] Tema claro/escuro segue sistema.
- [ ] Estados de carregamento, vazio, erro e indisponível estão implementados.
- [ ] Textos em português brasileiro.
- [ ] Controles têm rótulos acessíveis.

## C. Música
- [ ] Fonte escolhida com base em pesquisa documentada.
- [ ] Reprodução e pausa funcionam.
- [ ] Estado de erro é visível.
- [ ] O app não contorna DRM ou termos do provedor.
- [ ] Limitações de playback/metadata são documentadas.

## D. Áudio
- [ ] Capacidades exibidas correspondem ao que foi detectado/testado.
- [ ] Perfis são persistidos e recuperados.
- [ ] Parâmetros inválidos são rejeitados.
- [ ] Efeitos indisponíveis não são apresentados como ativos.
- [ ] Teste documenta dispositivo, versão Android e rota.
- [ ] Não se afirma controle universal de áudio de terceiros.

## E. Curiosidades
- [ ] Curiosidade vinculada à faixa correta.
- [ ] Fonte identificável e acessível quando disponível.
- [ ] Fatos e interpretações são diferenciados.
- [ ] Falta de fonte resulta em estado honesto, não em conteúdo inventado.
- [ ] Texto curto e legível.

## F. Assistente
- [ ] App funciona sem provedor generativo.
- [ ] Ferramentas permitidas são validadas antes da execução.
- [ ] Timer/lembrete confirma apenas resultado real.
- [ ] Entradas inválidas são rejeitadas.
- [ ] Nenhuma execução arbitrária de código ou comando de shell.
- [ ] Custo de IA não é obrigatório para o MVP local.

## G. Segurança
- [ ] Nenhum segredo no repositório.
- [ ] Logs não expõem tokens ou dados sensíveis.
- [ ] Permissões são solicitadas com contexto.
- [ ] Dados armazenados e enviados são documentados.

## H. Beta
- [ ] Teste em pelo menos um dispositivo Android físico.
- [ ] Limitações conhecidas listadas.
- [ ] Instruções de instalação e feedback preparadas.

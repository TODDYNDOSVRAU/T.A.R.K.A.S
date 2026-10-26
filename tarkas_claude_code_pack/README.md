# T.A.R.K.A.S.

T.A.R.K.A.S. é um projeto Android planejado para combinar reprodução musical, configuração de áudio, assistência pessoal e curiosidades contextuais sobre músicas.

## Estado do projeto
Este pacote é a especificação original. O app já existe (versão 1.7), feito em Kotlin + Jetpack Compose, e o código está em `app/` na raiz do repositório. Os documentos que ainda citam Flutter registram o plano inicial, não o que foi feito.

## Objetivos do MVP
1. App Android com interface adaptável ao tema do sistema.
2. Player funcional com uma fonte musical legalmente acessível escolhida após pesquisa.
3. Perfis e controles de áudio limitados às capacidades verificadas do dispositivo/sessão.
4. Curiosidades musicais curtas, com fontes e indicação de incerteza.
5. Assistente com ações locais simples e arquitetura de provedor substituível.
6. Funcionamento degradado e transparente sem rede, IA ou credenciais externas.

## Documentação
Leia `CLAUDE.md` e os documentos em `docs/` antes de implementar funcionalidades.

## Princípio de produto
Não confundir uma interface que apresenta controles com a capacidade real de controlar a saída de áudio do Android ou outros aplicativos. Toda capacidade deve ser demonstrada em dispositivo compatível e documentada.

## Execução
O procedimento de instalação e execução será completado após a auditoria inicial do ambiente. Consulte `docs/ROADMAP.md`.

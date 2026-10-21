# Pesquisa e decisão da fonte musical

## Objetivo
Escolher a primeira fonte de áudio legal, tecnicamente viável, com baixo custo e que permita testar player e capacidades sonoras. A escolha está deliberadamente **em aberto**.

## Candidatos a avaliar
1. Arquivos locais fornecidos pelo usuário (formatos e acesso documentados).
2. Conteúdo de demonstração livre de direitos ou explicitamente licenciado para testes.
3. Serviço de streaming com API/SDK oficial que permita o fluxo pretendido.
4. Catálogo de metadados sem reprodução, como fase de descoberta separada.

## Para cada candidato, investigar
- API/SDK oficial e status de manutenção.
- Plataformas e versões suportadas.
- Autenticação, requisitos de conta e região.
- Permissão para reprodução dentro de aplicativo de terceiros.
- Restrições de catálogo, busca, playback, cache, monetização e branding.
- Limites de chamadas, quota e disponibilidade.
- Custo e necessidade de plano pago.
- Compatibilidade com reprodução em segundo plano.
- Se áudio passa pelo pipeline do T.A.R.K.A.S. ou é reproduzido pelo app do provedor.
- Se é tecnicamente possível aplicar DSP ao áudio dessa fonte.
- Requisitos de privacidade e revisão/aprovação.
- Plano de fallback quando integração não estiver disponível.

## Regra de decisão
Não selecionar uma plataforma só por popularidade. A fonte deve permitir, de forma documentada, a função que o MVP realmente pretende oferecer. Se um serviço permite apenas controlar um player externo, não descrevê-lo como fonte de áudio processada pelo motor interno.

## Entrega da pesquisa
Criar uma tabela comparativa com fontes oficiais, data de consulta, capacidades confirmadas, limitações, custos, riscos e recomendação. Não usar scraping, engenharia reversa ou contorno de DRM/termos. Até a decisão, implementar apenas contratos e testes com conteúdo autorizado/local, sem fingir que um serviço está integrado.

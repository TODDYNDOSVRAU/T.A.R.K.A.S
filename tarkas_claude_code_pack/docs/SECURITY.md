# Segurança e privacidade

## Requisitos
- Não commitar `.env`, keystores, tokens, chaves ou credenciais.
- Incluir `.gitignore` apropriado.
- Não embutir chave privada de API no APK.
- Preferir armazenamento local para preferências e histórico.
- Coletar o mínimo de dados possível.
- Não registrar conteúdo privado de conversas ou tokens em logs.
- Pedir permissões de forma contextual e explicar finalidade.
- Usar HTTPS para chamadas remotas.
- Validar respostas externas e entradas do usuário.
- Definir política de retenção antes de sincronizar conversas.
- Não enviar histórico musical à IA sem necessidade/consentimento informado.
- Remover dados sensíveis de relatórios de bugs.

## Dependências
Antes de adicionar pacote:
- verificar repositório e mantenedor;
- verificar versão, licença, atividade e vulnerabilidades conhecidas;
- preferir pacotes necessários e amplamente mantidos;
- documentar motivo da inclusão.

## Repositório
- Revisar `git diff` antes de commits.
- Não usar dados reais em fixtures.
- Se segredo for exposto, removê-lo do código não basta: revogar/rotacionar credencial.

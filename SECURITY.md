# Política de segurança

O NexoDesk é um **projeto acadêmico**, ainda sem versão em produção e sem dados reais de moradores.

## Como avisar sobre uma falha

Use **Security > Report a vulnerability** neste repositório (aviso privado, só os mantenedores veem). Evite abrir uma issue pública
com os detalhes de uma falha. Descreva o que acontece, como repetir e qual o impacto.

## O que já está tratado

Está descrito em [`README.md`](README.md#segurança-o-que-está-feito) e em [`docs/documentacao-tecnica.md`](docs/documentacao-tecnica.md):
senhas com bcrypt, bloqueio de força bruta, CSRF, CSP restritiva, upload validado pelo conteúdo e sem GPS, e controle de acesso por perfil.

## Fora do escopo por enquanto

HTTPS real, e-mail testado contra provedor real e revisão jurídica da página de privacidade
(lista completa em "Ainda não feito" no `README.md`).

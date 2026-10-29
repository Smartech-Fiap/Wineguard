# Implantação WineGuard

## Serviços

- Express: 127.0.0.1:3000 — serviço legado interno; as rotas públicas de sensores estão bloqueadas.
- Java: 127.0.0.1:8080 — autenticação, usuários, ambientes, dispositivos, limites, leituras, histórico e mapa.
- PostgreSQL: 127.0.0.1:5432 — dados da aplicação.
- Nginx: portas públicas 80 e 443.

O backend Express é mantido separadamente. Os arquivos desta pasta
não instalam o Express nem a infraestrutura FIWARE.

## Configuração Java

Criar o .env local com as variáveis de conexão, sem versionar segredos.

Para publicação HTTPS:

```dotenv
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SESSION_COOKIE_SECURE=true
```

O serviço systemd carrega o .env por EnvironmentFile.
A execução manual exige carregar as variáveis no ambiente.

O modelo de serviço está em systemd/wineguard-java.service.
Ele pressupõe o projeto em /home/ubuntu/wineguard-java e o JAR
compilado no caminho indicado em ExecStart.

O PostgreSQL utiliza volume persistente e restart: unless-stopped.
O Java utiliza Restart=on-failure e pode tentar iniciar novamente
enquanto o PostgreSQL fica disponível.

## Nginx

Os modelos correspondem ao IP fixo 184.192.182.39.
Em outra instalação, ajustar o endereço e os caminhos dos certificados.

Destinos:

- nginx/wineguard-http.conf:
  /etc/nginx/sites-available/default
- nginx/wineguard-https.conf:
  /etc/nginx/sites-available/wineguard-https
- nginx/snippets/wineguard-java.conf:
  /etc/nginx/snippets/wineguard-java.conf

Ativar os sites por links em /etc/nginx/sites-enabled.
Inspecionar os arquivos e links existentes antes de substituir qualquer
configuração. Não criar dois servidores default para a mesma porta.

O HTTP preserva o caminho ACME para emissão e renovação do certificado.
As rotas Java são publicadas somente no servidor HTTPS.
As rotas /api/sensors e /api/sensors/* retornam 404 nos dois servidores,
HTTP e HTTPS. Não reabrir o acesso público à telemetria do Express.

O HTTPS encaminha para o Java os prefixos /api/auth/, /api/admin/,
/api/devices e /api/environments, além de /api/wineries e
/api/wineries/. Preserve os caminhos nas requisições encaminhadas.
O snippet Java mantém os cabeçalhos de proxy, incluindo
X-Forwarded-Proto, para informar a conexão HTTPS original.

Antes de recarregar:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

O servidor HTTPS só deve ser ativado depois da emissão do certificado.

## Certificado HTTPS para IP

A implantação utiliza Certbot instalado por Snap e certificado
Let's Encrypt de curta duração para o IP fixo.

Preparar o servidor HTTP com raiz /var/www/html.
O caminho /.well-known/acme-challenge/ precisa ser acessível
publicamente na porta 80.

Com Certbot 5.4 ou superior:

```bash
sudo /snap/bin/certbot certonly \
  --preferred-profile shortlived \
  --webroot \
  --webroot-path /var/www/html \
  --ip-address 184.192.182.39
```

Os arquivos de certificado e chave são criados no servidor.
Não incluí-los no repositório.

Copiar letsencrypt/reload-nginx.sh para:
 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

O script deve pertencer ao root e ter permissão 750.
Ele valida e recarrega o Nginx após uma renovação.

Verificações:

```bash
sudo /snap/bin/certbot renew --dry-run --run-deploy-hooks
systemctl list-timers --all --no-pager --full | grep -i certbot
```

O laboratório AWS precisa estar ativo para atender as validações.
Após períodos desligado, conferir validade do certificado e renovação.

## Verificação de acesso

Sem sessão autenticada, no HTTPS:

- GET /api/auth/csrf: 200 e cookie Secure, HttpOnly e SameSite=Lax.
- GET /api/auth/me: 401.
- GET /api/devices: 401.
- GET /api/admin/users: 401.
- GET /api/wineries: 401.

As rotas /api/sensors e /api/sensors/* devem retornar 404,
com ou sem sessão, tanto em HTTP quanto em HTTPS.

Com sessão autenticada:

- GET /api/auth/me retorna username e role.
- USER lista somente os dispositivos que cadastrou.
- USER recebe 404 ao acessar um dispositivo de outra conta.
- ADMIN pode consultar todos os dispositivos.
- Dispositivos antigos sem proprietário ficam acessíveis somente ao ADMIN.
- USER recebe 403 nas rotas administrativas.
- ADMIN pode listar e criar contas USER.
- Ambos os perfis podem consultar /api/wineries.
- O mapa compartilha apenas nome e localização, sem contas ou sensores.
- POST, PUT e DELETE protegidos também exigem token CSRF válido.

A localização é obrigatória para novas contas USER. Contas antigas e
a conta ADMIN podem permanecer sem localização; não recebem pontos
fictícios no mapa. A versão atual não oferece edição de localização.

O frontend utiliza as leituras e o histórico Java do dispositivo
selecionado. Não deve fazer fallback para as rotas públicas antigas.

## Integração validada e pendências

Validado no painel local conectado à API implantada:

- Login e administração de usuários.
- Lista de dispositivos filtrada por proprietário e acesso ADMIN.
- Leituras pela API Java.
- Cadastro de localização e marcador no mapa compartilhado.
- Conta USER sem acesso à administração ou sensores de outras contas.

Provisionamento e remoção foram testados com um dispositivo temporário.
O cadastro no IoT Agent não cria credenciais ou ACL MQTT. A remoção
não revoga essas credenciais nem apaga o histórico STH.

Limites persistidos por métrica não alteram automaticamente o ESP.
O envio de configuração completa usa revisão e confirmação separadas.

Pendências desta rodada:

- Concluir a execução das operações ainda não testadas da coleção Postman, incluindo o novo cadastro de usuário.
- Consolidar backend, firmware, infraestrutura e documentação no repositório final da equipe.
- Conferir os documentos e materiais da apresentação antes da entrega.

## Publicação do painel na Vercel

Frontend publicado: https://wineguard-peach.vercel.app/

O repositório final é https://github.com/Smartech-Fiap/Wineguard,
com Root Directory frontend na Vercel. Os demais componentes ainda
estão em consolidação.

O vercel.json encaminha /api/* para a API HTTPS da AWS.
O Nginx continua atendendo a API, bloqueando as rotas antigas dos
sensores e preservando o caminho ACME. O frontend não precisa ser
publicado pelo Nginx nessa topologia.

Verificações realizadas no endereço da Vercel:

- Página inicial: 200.
- Sessão, dispositivos e mapa sem login: 401.
- CSRF: 200, cookie Secure, HttpOnly, SameSite=Lax e sem Domain.
- Rota antiga de sensores: 404.
- Respostas da API com Cache-Control: private, no-store.
- Login ADMIN, leituras, sessão após recarga e logout.
- Conta USER com mapa compartilhado, sem administração ou sensor alheio.
- Salvamento de limite de temperatura com CSRF.
- Alerta físico de temperatura e encerramento com normal.
- Recuperação da revisão 2 e confirmação APPLIED após recarga.

O painel estático depende da API AWS disponível e de certificado
válido. A hospedagem na Vercel não mantém o laboratório AWS ativo.

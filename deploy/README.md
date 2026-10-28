# Implantação WineGuard

## Serviços

- Express: 127.0.0.1:3000 — leituras e histórico atuais do painel.
- Java: 127.0.0.1:8080 — autenticação, ambientes, dispositivos e limites.
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

O HTTP mantém o acesso anterior ao Express e o caminho ACME.
As rotas Java foram publicadas somente no servidor HTTPS.

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

Sem sessão autenticada:

- GET /api/auth/csrf: 200 e cookie Secure, HttpOnly e SameSite=Lax.
- GET /api/auth/me: 401.
- GET /api/devices: 401.
- GET /api/sensors/WineSensor002/readings: rota Express existente.

As leituras Express continuam públicas. O login Java não protege
automaticamente as rotas Express.

## Pendências

- Integração do login e da gestão com o React.
- Publicação dos arquivos finais do React pelo Nginx.
- Provisionamento e remoção de dispositivos no FIWARE.
- Avaliação dos limites e integração com comandos do ESP.

# Pacote de entrega para a equipe

Este guia relaciona cada pedido de revisão aos arquivos do backend WineGuard. Os arquivos são locais neste momento; nenhum repositório ou envio externo foi criado.

## O que compartilhar

| Pedido | Arquivo ou informação |
|---|---|
| Link do repositório ou SQL | Repositório público: `https://github.com/joao-jps/wineguard-backend`. O schema está em `src/main/resources/db/migration/V1__create_application_schema.sql`; dados locais fictícios opcionais em `src/main/resources/db/seed/demo-data.sql`. |
| Modelo e relacionamentos | `docs/database.md` e a seção “Dados e relações” do `README.md`. |
| Banco e versão | PostgreSQL 16 (`postgres:16-alpine` no Compose local). A versão pontual do servidor conectada deve ser conferida no ambiente de implantação. |
| Instalar e executar | `docs/rodar-localmente.md` e o `README.md`. |
| Exemplo de variáveis | `.env.example`, com valores locais fictícios; copiar para `.env` e nunca preencher com segredos no arquivo compartilhado. |
| Onde está executando | Desenvolvimento local: API pela classe principal no IntelliJ/porta `8080`; PostgreSQL em Docker Desktop/porta local `5432`. Não está implantado na AWS. |
| API visual e testes | `docs/api-visual.md` e `docs/postman/WineGuard-API.postman_collection.json`. |
| Guia visual editável | `docs/guia-da-equipe.docx`; pode ser aberto no Word/Pages ou importado no Google Docs. |
| Arquitetura e execução | `docs/fluxo-e-execucao.svg` e `docs/rodar-localmente.md`. |

## Modelo atual

`environments` agrupa locais monitorados; `devices.environment_id` associa cada dispositivo a um ambiente. `device_limits.device_id` associa uma configuração de limite ao dispositivo e `metric` identifica temperatura, umidade ou luminosidade. Cada dispositivo pode ter um limite por métrica. A restrição permite informar mínimo, máximo ou ambos e rejeita uma faixa invertida. Leituras não são copiadas para PostgreSQL: o estado atual vem do Orion e o histórico do STH-Comet.

## Resultado da verificação local no Postman

- Passaram: saúde da API `200`, listagens `200`, criação de ambiente/dispositivo `201`, gravação e consulta de limites `200`.
- O Runner do Postman percorreu os nove endpoints: 16 verificações aprovadas e zero erros. A checagem das leituras considerou `502` uma falha de dependência FIWARE tratada pelo backend, não uma leitura válida.
- A consulta de limites revelou um nome de consulta JPA incorreto; foi corrigido e retestado com sucesso.
- As rotas de leitura atual e histórica responderam `502` porque Orion (`localhost:1026`) e STH-Comet (`localhost:8666`) não estavam acessíveis na execução local. Isso deixa pendente validar resposta e atributos contra as VMs reais.
- Os testes criaram registros fictícios no banco local, descritos em `README.md`; não representam sensores instalados.

## Pendências antes de integrar às VMs

Solicite à equipe os endereços acessíveis do Orion e STH-Comet, autenticação exigida, versão/contexto FIWARE, nomes e unidades dos atributos, além das regras reais de limites. A API deve alcançar esses serviços por rede privada. O PostgreSQL local está ligado somente a `127.0.0.1`; não abra a porta `5432` para a internet. Para AWS, use rede privada e segredo em serviço próprio, como Secrets Manager.

## Como enviar

O código está organizado no repositório público indicado acima. Para conceder acesso de escrita, adicione os colegas como colaboradores do GitHub. Remova `.env` local se existir e não envie senhas por Git, e-mail aberto ou junto do SQL. O arquivo `.env.example` contém apenas valores fictícios locais.

# Implantação sugerida na AWS

## Primeira etapa

Manter o backend Node atual em EC2 até a equipe validar o novo contrato. Criar uma imagem do serviço Java e executá-la em ECS com Fargate; hospedar PostgreSQL em RDS dentro de subnets privadas. Configurar Security Groups para permitir a porta do PostgreSQL somente a partir das tarefas ECS. As tarefas precisam alcançar Orion e STH-Comet, seja por rede/VPC, peering/VPN ou endpoints expostos com TLS e controle de acesso.

Use Secrets Manager ou parâmetros seguros do serviço de execução para credenciais; não coloque senha em imagem, arquivo de configuração, task definition em texto aberto ou repositório. Ative backups automáticos do RDS, logs da aplicação, health checks e monitoramento. Defina retenção e sizing depois de medir volume e orçamento.

## Sequência de implantação

1. Confirmar região, VPC/subnets e caminho de rede para Orion/STH.
2. Criar RDS PostgreSQL em subnets privadas, sem acesso público, e credencial exclusiva da aplicação.
3. Publicar imagem no Amazon ECR e executar tarefa ECS/Fargate com as variáveis de ambiente e segredos.
4. Executar migrações Flyway na inicialização controlada e validar `/actuator/health`.
5. Configurar HTTPS no Application Load Balancer/API Gateway e autenticação antes de abrir acesso ao painel.
6. Só migrar tráfego depois de contrato validado, backup, rollback e aceite do grupo.

## Limites desta documentação

Não foram fornecidos conta/região AWS, topologia de rede, URLs FIWARE, domínio, política de autenticação, orçamento ou tráfego. Portanto, este repositório descreve o desenho e não cria recursos AWS, não escolhe tamanhos/custos e não supõe acesso à EC2 existente. Fargate + RDS é uma proposta de destino, não uma alteração aplicada à infraestrutura.

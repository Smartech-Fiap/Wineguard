-- Dados fictícios para ambiente local. Não são executados automaticamente em produção.
INSERT INTO environments (name, description)
VALUES ('Ambiente de demonstração', 'Registro fictício para validar o cadastro local.')
ON CONFLICT (name) DO NOTHING;

INSERT INTO devices (name, fiware_id, fiware_type, fiware_service, fiware_service_path, environment_id)
SELECT 'Sensor de demonstração', 'demo-wine-001', 'WineSensor', 'demo', '/', id
FROM environments
WHERE name = 'Ambiente de demonstração'
ON CONFLICT (fiware_service, fiware_service_path, fiware_id) DO NOTHING;

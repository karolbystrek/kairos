.DEFAULT_GOAL := help
.PHONY: help dev prod up stop logs reset

help:
	@echo 'make dev    Prepare config and keys, start the development stack with hot reload'
	@echo 'make prod   Prepare config and keys, start the production stack'
	@echo 'make up     Start or rebuild the selected stack'
	@echo 'make stop   Stop the stack and keep data'
	@echo 'make logs   Follow logs'
	@echo 'make reset  Delete containers, volumes and keys; refuses production'

dev prod:
	@sh scripts/setup.sh --$@

up:
	docker compose up --build -d

stop:
	docker compose stop

logs:
	docker compose logs -f

reset:
	@sh scripts/reset.sh

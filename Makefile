.DEFAULT_GOAL := help
.DELETE_ON_ERROR:
.PHONY: help dev stop logs reset

KEYS := secrets/email-encryption.bin secrets/webhook-encryption.bin \
	secrets/push-subscription-encryption.bin secrets/vapid-private.pem secrets/vapid-public.pem

help:
	@echo 'make dev    Create missing .env and keys, then start or rebuild the stack'
	@echo 'make stop   Stop the stack and keep data'
	@echo 'make logs   Follow logs'
	@echo 'make reset  Delete containers, volumes, keys and .env; refuses production'

dev: .env $(KEYS)
	docker compose up --build -d --wait

stop:
	docker compose stop

logs:
	docker compose logs -f

reset:
	@case "$${COMPOSE_FILE:-$$(sed -n 's/^COMPOSE_FILE=//p' .env 2>/dev/null)}" in \
		*compose.production.yaml*) echo 'Refusing to reset: COMPOSE_FILE selects production.' >&2; exit 1;; \
	esac
	docker compose --env-file .env.example $(if $(wildcard .env),--env-file .env) down -v --remove-orphans
	@if [ -f .env ]; then saved=.env.old.$$(date +%Y%m%d%H%M%S); mv .env $$saved; echo "Saved .env as $$saved."; fi
	rm -f $(KEYS)

.env:
	install -m 600 .env.example $@

secrets:
	mkdir -m 700 $@

secrets/%.bin: | secrets
	openssl rand -out $@ 32
	chmod 400 $@

secrets/vapid-private.pem: | secrets
	openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out $@
	chmod 400 $@

secrets/vapid-public.pem: secrets/vapid-private.pem
	rm -f $@
	openssl pkey -in $< -pubout -out $@
	chmod 400 $@

.PHONY: help run test console build db:setup db:seed db:backup db:restore docker:dev docker:prod check-deps clean

help: ## Show this help message
	@./bin/galaticos help

run: ## Run the application
	@./bin/galaticos run

test: ## Run backend and CLJS tests
	@./bin/galaticos test

console: ## Start Clojure REPL
	@./bin/galaticos console

build: ## Build uberjar
	@./bin/galaticos build

db:setup: ## Set up MongoDB indexes
	@./bin/galaticos db:setup

db:seed: ## Seed database from Excel
	@./bin/galaticos db:seed

db:backup: ## Backup MongoDB (mongodump archive under backups/mongodb)
	@./bin/galaticos db:backup

db:restore: ## Restore MongoDB (pass ARGS="--archive path [--drop]")
	@./bin/galaticos db:restore $(ARGS)

docker:dev: ## Manage Docker dev environment (use: make docker:dev CMD=start)
	@./bin/galaticos docker:dev $(CMD)

docker:prod: ## Manage Docker prod environment (use: make docker:prod CMD=start)
	@./bin/galaticos docker:prod $(CMD)

check-deps: ## Check if all dependencies are installed
	@./bin/galaticos check-deps

clean: ## Clean build artifacts
	@./bin/galaticos clean


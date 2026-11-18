.PHONY: up down db backend frontend test it full full-down clean live help

# ─────────────────────────────────────────────────────────────────────────────
# Ops Assistant – developer shortcuts
# Usage: make <target>
# ─────────────────────────────────────────────────────────────────────────────

help: ## Show this help message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

up: ## Start dev Postgres (and back/front if desired)
	docker compose up -d

down: ## Stop and remove the dev Postgres container
	docker compose down

db: up ## Open a psql shell to the dev database
	docker compose exec postgres psql -U ops -d opsdb

backend: up ## Run the Spring Boot backend with the dev profile (seeder enabled)
	cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

frontend: ## Install deps (if needed) and start the Vite dev server
	cd frontend && npm install && npm run dev

test: ## Run all unit tests (no Docker required)
	cd backend && mvn -q test -DexcludedGroups=integration

it: ## Run integration tests (Docker required for Testcontainers)
	cd backend && mvn -q test -Dgroups=integration

full: ## Build and start the full-stack Docker Compose (postgres + backend + frontend)
	docker compose -f docker-compose.full.yml up --build

full-down: ## Stop and remove full-stack containers and volumes
	docker compose -f docker-compose.full.yml down -v

clean: ## Remove Maven target/ and frontend dist/
	cd backend && mvn -q clean
	rm -rf frontend/dist

live: ## Run live Groq tests using .env (requires AI_API_KEY)
	cd backend && set -a && . ../.env && set +a && mvn -q test -Dgroups=live -DexcludedGroups=integration

# Convenience targets. Java 21 is required; this Makefile points JAVA_HOME at the
# Homebrew keg-only openjdk@21 if present, otherwise falls back to the ambient JDK.
JAVA_HOME ?= $(shell [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ] \
	&& echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
	|| /usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME

MVN := ./mvnw -B

.PHONY: help up down logs build test it verify run fmt clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

up: ## Start Postgres + Redis
	docker compose up -d

down: ## Stop infra
	docker compose down

logs: ## Tail infra logs
	docker compose logs -f

build: ## Compile without tests
	$(MVN) -DskipTests package

test: ## Run unit tests (no Docker needed)
	$(MVN) test

it: ## Run integration tests (needs Docker)
	$(MVN) -DskipUnitTests=false verify -Dtest.groups=integration

verify: ## Full verify: tests + quality gates
	$(MVN) verify

run: up ## Start infra then run the app locally
	$(MVN) spring-boot:run

fmt: ## Auto-format sources (Spotless)
	$(MVN) spotless:apply

clean: ## Remove build output
	$(MVN) clean

.DEFAULT_GOAL := help
.PHONY: help version release wrapper

help:  ## Show this help
	@echo "Pilot — available targets:"
	@echo "  make version          Show current released version (latest tag)"
	@echo "  make release V=0.3.0  Test, build, sign, and publish a GitHub release"
	@echo "  make wrapper          (Re)generate the Gradle wrapper (needs gradle installed)"

version:  ## Print the current released version
	@./scripts/version.sh

release:  ## Cut a release: make release V=MAJOR.MINOR.PATCH
	@test -n "$(V)" || { echo "usage: make release V=MAJOR.MINOR.PATCH"; exit 1; }
	@./scripts/release.sh $(V)

wrapper:  ## Regenerate the Gradle wrapper
	@./scripts/bootstrap-wrapper.sh

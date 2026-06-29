.DEFAULT_GOAL := help
.PHONY: help check version release wrapper

help:  ## Show this help
	@echo "Pilot — available targets:"
	@echo "  make check            Build, run unit tests, and lint (debug)"
	@echo "  make version          Show current released version (latest tag)"
	@echo "  make release V=0.3.0  Test, build, sign, update CHANGELOG, and publish a GitHub release"
	@echo "  make wrapper          (Re)generate the Gradle wrapper (needs gradle installed)"

check:  ## Build, run unit tests, and lint (debug)
	./gradlew assembleDebug testDebugUnitTest lintDebug

version:  ## Print the current released version
	@./scripts/version.sh

release:  ## Cut a release: make release V=MAJOR.MINOR.PATCH
	@test -n "$(V)" || { echo "usage: make release V=MAJOR.MINOR.PATCH"; exit 1; }
	@./scripts/release.sh $(V)

wrapper:  ## Regenerate the Gradle wrapper
	@./scripts/bootstrap-wrapper.sh

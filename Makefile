APP_NAME := NomadDashboard
.DEFAULT_GOAL := help

.PHONY: help bootstrap doctor connect-wireless start-emulator build run rerun test test-emulator test-device screenshot screenshot-device screenshots lint probe-sources bundle-release apk-release release-dry-run publish-internal publish-closed publish-production release release-patch release-minor release-major clean

help: ## Print available make targets
	@printf "\nAvailable commands:\n\n"
	@awk 'BEGIN {FS = ":.*## "}; /^[a-zA-Z0-9._-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"

bootstrap: ## Verify and prepare local Android development tooling
	./scripts/bootstrap.sh

doctor: ## Print the resolved Java/SDK/Gradle environment
	./scripts/doctor.sh

connect-wireless: ## Pair and connect an Android device over Wi-Fi
	./scripts/connect-wireless-device.sh

start-emulator: ## Boot the default Android emulator AVD
	./scripts/start-emulator.sh

build: ## Build the debug app
	./scripts/build-dev.sh

run: ## Install and launch the debug app on a connected device or emulator
	./scripts/run-dev.sh

rerun: ## Reinstall and relaunch the debug app
	./scripts/rerun-dev.sh

test: ## Run unit tests and connected tests on the default emulator
	./scripts/test.sh

test-emulator: ## Run unit tests and connected tests against the emulator only
	./scripts/test-emulator.sh

test-device: ## Run unit tests and connected tests against a physical device
	./scripts/test-device.sh

screenshot: ## Capture a timestamped screenshot from the selected adb target
	./scripts/capture-device-screenshot.sh

screenshot-device: ## Capture a timestamped screenshot from a physical Android device only
	PHYSICAL_DEVICE_ONLY=1 ./scripts/capture-device-screenshot.sh

screenshots: ## Capture deterministic Android UI review screenshots from the emulator
	SCREEN="$(SCREEN)" ./scripts/capture-screenshots.sh

lint: ## Run Kotlin and Android lint checks
	./scripts/lint.sh

probe-sources: ## Probe external data sources used by the app
	./scripts/probe-external-sources.sh

bundle-release: ## Build the signed release Android App Bundle
	./scripts/bundle-release.sh

apk-release: ## Build the signed release APK
	./scripts/apk-release.sh

release-dry-run: ## Print resolved release configuration without publishing
	./scripts/release-preflight.sh --dry-run

publish-internal: ## Upload the release bundle to Google Play internal testing
	./scripts/publish-internal.sh

publish-closed: ## Upload the release bundle to the configured Google Play closed testing track
	./scripts/publish-closed.sh

publish-production: ## Upload the release bundle to Google Play production
	./scripts/publish-production.sh

release: ## Run preflight, build a signed bundle, and publish to internal testing
	./scripts/release.sh

release-patch: ## Prepare a local patch release commit, tag, changelog, and Play notes
	./scripts/prepare-release.sh patch

release-minor: ## Prepare a local minor release commit, tag, changelog, and Play notes
	./scripts/prepare-release.sh minor

release-major: ## Prepare a local major release commit, tag, changelog, and Play notes
	./scripts/prepare-release.sh major

clean: ## Remove local build artifacts
	rm -rf .gradle build artifacts output

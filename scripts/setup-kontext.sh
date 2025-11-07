#!/usr/bin/env bash
set -euo pipefail

header() { printf "\n\033[1;34m==> %s\033[0m\n" "$*"; }
warn() { printf "\033[1;33m[warn]\033[0m %s\n" "$*"; }
err() { printf "\033[1;31m[err]\033[0m %s\n" "$*"; }

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  warn "Some steps require sudo; you may be prompted for your password."
fi

INSTALL_JAVA=${INSTALL_JAVA:-true}
INSTALL_DOCKER=${INSTALL_DOCKER:-true}
INSTALL_MISC=${INSTALL_MISC:-true}

OS="$(uname -s)"
ARCH="$(uname -m)"

header "Kontext setup"
echo "Detected: OS=${OS}, ARCH=${ARCH}"

case "${ARCH}" in
  arm64|aarch64) ;;
  *) warn "This script targets ARM64/aarch64; proceeding anyway."; ;
esac

install_mac() {
  if ! command -v brew >/dev/null 2>&1; then
    header "Installing Homebrew"
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    eval "$(/opt/homebrew/bin/brew shellenv)"
  fi

  if [[ "${INSTALL_JAVA}" == "true" ]]; then
    header "Installing Java 21 (temurin)"
    brew install --quiet --cask temurin@21 || brew install --quiet temurin@21
  fi

  if [[ "${INSTALL_DOCKER}" == "true" ]]; then
    header "Checking Docker Desktop"
    if ! command -v docker >/dev/null 2>&1; then
      warn "Docker Desktop is not installed. Downloading..."
      brew install --quiet --cask docker
    fi
    warn "Start Docker Desktop manually if it is not already running."
  fi

  if [[ "${INSTALL_MISC}" == "true" ]]; then
    header "Installing supporting tools"
    brew install --quiet git wget unzip jq
  fi
}

install_linux() {
  if ! command -v apt >/dev/null 2>&1; then
    err "This script expects apt-based distro (Ubuntu/Debian). Install dependencies manually."
    exit 1
  fi

  header "Updating apt cache"
  sudo apt-get update -y

  if [[ "${INSTALL_MISC}" == "true" ]]; then
    header "Installing common utilities"
    sudo apt-get install -y curl wget git unzip jq software-properties-common ca-certificates gnupg lsb-release
  fi

  if [[ "${INSTALL_JAVA}" == "true" ]]; then
    header "Installing OpenJDK 21"
    sudo apt-get install -y openjdk-21-jdk
  fi

  if [[ "${INSTALL_DOCKER}" == "true" ]]; then
    if ! command -v docker >/dev/null 2>&1; then
      header "Installing Docker Engine"
      sudo install -m 0755 -d /etc/apt/keyrings
      curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
      echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
        $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
      sudo apt-get update -y
      sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      sudo usermod -aG docker "${SUDO_USER:-$USER}"
      warn "Log out/in (or reboot) so group membership takes effect."
    else
      header "Docker already installed"
    fi
  fi
}

case "${OS}" in
  Darwin) install_mac ;;
  Linux) install_linux ;;
  *) err "Unsupported OS ${OS}" ; exit 1 ;;
esac

header "Kontext setup complete"
echo "Review .kontext-setup.conf for environment defaults."

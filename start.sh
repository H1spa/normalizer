#!/usr/bin/env bash

set -euo pipefail

if [[ ! -f .env ]]; then
  echo ".env is required; create it from .env.example"
  exit 1
fi

docker run --rm \
  -v "${PWD}:/workspace" \
  -w /workspace \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package

docker compose up --build -d
docker compose ps

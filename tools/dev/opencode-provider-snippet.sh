#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: $(basename "$0") <runner> [model]
Prints an OpenCode provider snippet for the given runner (`ollama`, `vllm`, `llama-cpp`).
If `model` is omitted it falls back to a reasonable default for that runner.
USAGE
}

if [ $# -lt 1 ]; then
  usage
  exit 1
fi

runner=$1
model=${2:-}

case $runner in
  ollama)
    model=${model:-qwen2:latest}
    base_url="http://localhost:11434/v1"
    provider_name="Ollama"
    model_name="$model"
    ;;
  vllm)
    model=${model:-NousResearch/Hermes-3-Llama-3.1-405B-AWQ}
    base_url="http://localhost:8000/v1"
    provider_name="vLLM"
    model_name="$model"
    ;;
  llama-cpp)
    model=${model:-llama3.2}
    base_url="http://localhost:11435/v1"
    provider_name="llama.cpp"
    model_name="$model"
    ;;
  *)
    echo "Unknown runner: $runner" >&2
    usage
    exit 2
    ;;
esac

cat <<JSON
{
  "provider": {
    "$runner": {
      "npm": "@ai-sdk/openai",
      "name": "$provider_name",
      "options": { "baseURL": "$base_url" },
      "models": {
        "$model_name": { "name": "$model_name" }
      }
    }
  }
}
JSON

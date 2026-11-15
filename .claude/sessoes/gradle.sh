#!/bin/bash
# Roda o gradle do T.A.R.K.A.S. com trava: so uma sessao compila/instala por vez.
# Uso: bash .claude/sessoes/gradle.sh <sessao> <argumentos do gradle...>
#   ex: bash .claude/sessoes/gradle.sh player -q :app:installDebug :app:testDebugUnitTest
# Espera ate 10 min pela trava. Uma trava com mais de 20 min e considerada abandonada.
SESSAO=${1:?uso: gradle.sh <sessao> <args do gradle>}; shift
DIR="$(cd "$(dirname "$0")" && pwd)"
LOCK="$DIR/build.lock"
G=$(ls ~/.gradle/wrapper/dists/gradle-9.3.1-bin/*/gradle-9.3.1/bin/gradle | head -1)
waited=0
until mkdir "$LOCK" 2>/dev/null; do
  now=$(date +%s); born=$(stat -c %Y "$LOCK" 2>/dev/null || echo "$now")
  if [ $((now - born)) -gt 1200 ]; then echo "trava abandonada ($(cat "$LOCK/dono" 2>/dev/null)), removendo"; rm -rf "$LOCK"; continue; fi
  [ $waited -eq 0 ] && echo "build ocupado por: $(cat "$LOCK/dono" 2>/dev/null); aguardando..."
  [ $waited -ge 600 ] && { echo "esperou 10 min pela trava de build; tente de novo ou fale com o dono: $(cat "$LOCK/dono" 2>/dev/null)"; exit 75; }
  sleep 5; waited=$((waited + 5))
done
echo "$SESSAO $(date '+%F %T')" > "$LOCK/dono"
trap 'rm -rf "$LOCK"' EXIT
cd "$DIR/../.." && "$G" "$@"

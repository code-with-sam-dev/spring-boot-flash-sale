#!/usr/bin/env bash
# One measured run. Usage: bin/run.sh <label> [POOL] [MODE] [BUY_RATE] [BROWSE_RATE] [DURATION]
# Restarts the app with the given pool and checkout, warms it up, runs k6, samples the app's own
# metrics every 2 s, takes one thread dump at the midpoint, and writes everything to runs/<label>/.
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL=$1; POOL=${2:-10}; MODE=${3:-pay-inside}; BUY=${4:-20}; BROWSE=${5:-200}; DUR=${6:-60s}
OUT=runs/$LABEL; rm -rf "$OUT"; mkdir -p "$OUT"
POOL=$POOL MODE=$MODE docker compose up -d --build --force-recreate app >/dev/null 2>&1
until curl -sf localhost:8080/actuator/health >/dev/null; do sleep 1; done
# Warm-up: JIT, pools and connections, not recorded. Then a clean table, so the count is this run's.
k6 run -q -e BUY_RATE=5 -e BROWSE_RATE=50 -e DURATION=15s load/flash-sale.js >/dev/null 2>&1
docker compose exec -T postgres psql -qU shop -c "TRUNCATE orders; UPDATE products SET stock = 1000000 WHERE id = 1;" >/dev/null
echo "pool=$POOL mode=$MODE buy=$BUY/s browse=$BROWSE/s duration=$DUR $(date -u +%FT%TZ)" > "$OUT/run.txt"

m() { curl -s "localhost:8080/actuator/metrics/$1${2:+?tag=$2}" | python3 -c "import json,sys;print(json.load(sys.stdin)['measurements'][0]['value'])" 2>/dev/null || echo -; }
( while true; do
    printf '%s %s %s %s %s %s\n' "$(date +%s)" "$(m hikaricp.connections.active)" "$(m hikaricp.connections.pending)" \
      "$(m tomcat.threads.busy)" "$(m process.cpu.usage)" "$(m tomcat.connections.current 2>/dev/null)" >> "$OUT/metrics.txt"
    sleep 2
  done ) &
SAMPLER=$!
( sleep "$(( ${DUR%s} / 2 ))"; curl -s localhost:8080/actuator/threaddump -H 'Accept: text/plain' > "$OUT/threaddump.txt";
  docker compose exec -T postgres psql -qU shop -At -c \
    "SELECT coalesce(wait_event_type,'cpu') || ':' || coalesce(wait_event,'-') || ' ' || state, count(*) FROM pg_stat_activity WHERE datname='shop' GROUP BY 1 ORDER BY 2 DESC" > "$OUT/pg-waits.txt" ) &
k6 run -q -e PRE_VUS=${PRE_VUS:-6000} -e BUY_RATE=$BUY -e BROWSE_RATE=$BROWSE -e DURATION=$DUR --summary-export "$OUT/k6.json" load/flash-sale.js > "$OUT/k6.txt" 2>&1 || true
kill $SAMPLER 2>/dev/null || true; wait 2>/dev/null || true
docker compose exec -T postgres psql -qU shop -At -c "SELECT count(*) FROM orders WHERE status = 'PAID'" > "$OUT/orders-in-db.txt"
python3 bin/summary.py "$OUT"

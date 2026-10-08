#!/usr/bin/env bash
# The three-way comparison, three times each, same load: buy 20/s, browse 200/s, 60 s.
# A: default pool 10, pay inside the transaction. B: pool 50 (the obvious fix). C: pool 10, pay after commit.
set -uo pipefail
cd "$(dirname "$0")/.."
for n in 1 2 3; do
  bin/run.sh "a-pool10-inside-$n" 10 pay-inside 20 200 60s
  bin/run.sh "b-pool50-inside-$n" 50 pay-inside 20 200 60s
  bin/run.sh "c-pool10-after-$n"  10 pay-after-commit 20 200 60s
done

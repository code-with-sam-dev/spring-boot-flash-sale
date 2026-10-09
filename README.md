# Spring Boot flash sale: one @Transactional mistake, measured

A small shop built on Spring Boot 4.1.1 and Java 25, with two checkouts that do the same thing to the data:

- **`PayInsideTransaction`**: takes one unit of stock, calls the card provider and records the order, all inside one `@Transactional` method. This is the version most of us write first.
- **`PayAfterCommit`**: reserves the stock and commits, calls the card provider with no database connection and no row lock held, then marks the order paid in a second short transaction.

Under a flash sale the first one makes checkout take more than 20 seconds, and makes browsing a completely different product just as slow. Raising the connection pool from 10 to 50 does not help. The second one answers in about 100 ms. Every number below is reproduced by a command in this repository.

## What you need

- Docker (Compose v2)
- Java 25 and Maven (the wrapper is included)
- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) on the host
- Python 3 for the run summaries

## The setup

`compose.yaml` runs everything on one machine, with fixed resources so runs compare:

| Service | What it is | Limits |
|---|---|---|
| `postgres` | Postgres 17, port 5480 | 2 CPUs, 2 GB |
| `payments` | WireMock standing in for the card provider: every charge answers after 100 ms | 2 CPUs, 1 GB |
| `app` | The shop, port 8080 | 2 CPUs, 1 GB |

The catalogue has one flash sale product (id 1, a million units) and 999 ordinary products. The load, in `load/flash-sale.js`, is an open model: buyers place orders on product 1 and browsers view random other products, at a fixed rate whether or not the app keeps up.

These are numbers from one laptop with a stubbed provider. They show the shape of the failure, not the capacity of any production server.

## Run it

```bash
docker compose up -d --build

# Tests: both checkouts against a real Postgres (Testcontainers)
./mvnw test

# One measured run: label, pool size, checkout, orders/s, page views/s, duration
bin/run.sh a-pool10-inside 10 pay-inside       20 200 60s
bin/run.sh b-pool50-inside 50 pay-inside       20 200 60s
bin/run.sh c-pool10-after  10 pay-after-commit 20 200 60s

# The full comparison: the three runs above, three times each
bin/matrix.sh

# How far the fixed checkout goes: five times the load
PRE_VUS=200 bin/run.sh cap-after-100 10 pay-after-commit 100 1000 60s

# One burst, every request timed: twenty orders at once, then one product page
python3 bin/timeline.py

# The tests, one line each
bin/tests.sh

# Does Tomcat refuse connections once its 200 threads are busy? Hold idle connections, then try a real request
python3 bin/connections.py 1000 5000 8100 8180 8200 8300
```

Each run writes `runs/<label>/`: the k6 summary, the app's own metrics every 2 seconds (Hikari active and waiting, Tomcat busy threads, CPU), a thread dump taken at the midpoint, what Postgres sessions were waiting on at that moment, and the number of paid orders in the database. `bin/summary.py` prints the headline numbers.

`run.sh` pre-allocates 6,000 load generator users. With too few, k6 itself runs out of users to send requests and reports them as dropped, which looks like the app failing when it is not. The broken checkout needs thousands in flight, so it needs them.

The five times load run uses 200, which dropped nothing. With 6,000 pre-allocated at that rate, the run on this machine failed: over half the checkouts errored, with the app timing out while connecting to the payment stub. That run is kept in `evidence/runs/cap-after-100-pre6000-failed/`. We have not established why, so we do not draw a conclusion from it.

## What it showed

Three runs of each, from `bin/matrix.sh`, ranges across the three. Every run's summary, thread dump summary, Postgres waits and metrics are in `evidence/runs/`. Earlier runs, before the load generator pre-allocated enough users, are kept in `evidence/earlier/`.

| | Pool 10, pay inside | Pool 50, pay inside | Pool 10, pay after commit |
|---|---|---|---|
| Checkout, median | 24.4 to 24.6 s | 24.2 to 24.7 s | 106 to 107 ms |
| Browsing another product, median | 23.4 to 23.7 s | 20.4 to 20.5 s | 1 to 2 ms |
| Paid orders (of 1,200 asked for) | 828 to 835 | 815 to 833 | 1,201 |
| App CPU, peak | 29 to 41% | 28 to 31% | 27 to 39% |

**Five times the load**, fixed checkout: 1,098 requests a second, checkout median 103 ms, p99 141 ms, all 6,001 orders paid, app CPU 98%. The limit is now compute.

**One burst** (`evidence/timeline-*.txt`): twenty orders at the same instant. Paying inside the transaction, they finish one after another, about 110 ms apart, the last at 2.3 s, and a product page sent just after waits 226 ms. Paying after commit, all twenty finish within 0.18 s and the page takes 15 ms.

**Why.** Inside the transaction, the connection that took the stock holds the product row's lock for the whole 100 ms payment call. Every other buyer waits on that lock (Postgres shows them on `Lock:transactionid`, with one session `idle in transaction`). The waiting buyers hold the other pool connections, so the browsers queue in Hikari behind them, and all 200 Tomcat threads end up parked. The CPU sits mostly idle. A bigger pool just lets more sessions wait on the same row.

## Threads are not connections

A common explanation says Tomcat, with 200 threads and an accept queue of 100, starts refusing connections after a few hundred. `bin/connections.py` holds idle connections open and then sends one real request (`evidence/connections.txt`): with 8,180 held, the request answered in 8 ms; with 8,200 held, it hung until the 10 second timeout. That matches Tomcat's `max-connections` default of 8,192; the accept queue only applies beyond it. This ran through Docker Desktop's port proxy, so we saw a hang rather than a refused connection, and we do not claim which layer held it.

## What the fix costs

Paying outside the transaction means the charge and the database can disagree. If the card is charged and the "mark paid" write then fails, the order stays `RESERVED`. The charge was sent with the order id as its idempotency key (`order-<id>`), so a reconciler can ask the provider what happened to that order and finish it. `PayAfterCommitTest.chargedButNotRecordedLeavesAReservedOrderToReconcile` proves that state. There is no atomic transaction across your database and someone else's payment API, whichever version you choose.

## Defaults this depends on

At the time of writing (October 2026, Spring Boot 4.1.1): Tomcat `server.tomcat.threads.max` 200, `accept-count` 100, `max-connections` 8,192; HikariCP `maximum-pool-size` 10, `connection-timeout` 30 seconds.

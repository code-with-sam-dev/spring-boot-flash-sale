"""One burst, every request timed: twenty orders for the flash sale product at the same instant,
then one shopper viewing a different product 50 ms later. Prints when each request started and
ended, relative to the burst, so the queue can be drawn from measurements rather than imagined.
Run it against a quiet app: bin/run.sh restarts it, or `docker compose up -d app`.
Usage: python3 bin/timeline.py > runs/timeline-<mode>.txt"""
import json, threading, time, urllib.request

BASE = "http://localhost:8080"
ORDERS, rows, lock = 20, [], threading.Lock()

def call(kind, n, delay, t0):
    time.sleep(delay)
    if kind == "order":
        req = urllib.request.Request(f"{BASE}/orders", method="POST",
              data=json.dumps({"productId": 1, "customer": f"buyer-{n}"}).encode(),
              headers={"Content-Type": "application/json"})
    else:
        req = urllib.request.Request(f"{BASE}/products/{500 + n}")
    start = time.perf_counter() - t0
    with urllib.request.urlopen(req, timeout=60) as r:
        r.read()
        status = r.status
    end = time.perf_counter() - t0
    with lock:
        rows.append((kind, n, start, end, status))

t0 = time.perf_counter()
threads = [threading.Thread(target=call, args=("order", n, 0, t0)) for n in range(ORDERS)]
threads.append(threading.Thread(target=call, args=("browse", 0, 0.05, t0)))
for t in threads: t.start()
for t in threads: t.join()
print("kind    n  start_s  end_s  took_ms  status")
for kind, n, s, e, st in sorted(rows, key=lambda r: r[2]):
    print(f"{kind:6} {n:>3}  {s:7.3f} {e:6.3f}  {(e - s) * 1000:7.0f}  {st}")

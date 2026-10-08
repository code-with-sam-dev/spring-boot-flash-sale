"""Prints one run's numbers from runs/<label>/: what was asked for, what completed, and how long it took."""
import json, sys, pathlib

out = pathlib.Path(sys.argv[1])
k = json.loads((out / "k6.json").read_text())["metrics"]

def g(name, field, default=0):
    return k.get(name, {}).get(field, default)

ms = lambda v: f"{v/1000:.1f}s" if v >= 1000 else f"{v:.0f}ms"
print((out / "run.txt").read_text().strip())
for kind in ("order", "browse"):
    d = f"http_req_duration{{name:{kind}}}"
    f = f"http_req_failed{{name:{kind}}}"
    print(f"  {kind:6} med {ms(g(d,'med'))}  p99 {ms(g(d,'p(99)'))}  max {ms(g(d,'max'))}  failed {g(f,'value')*100:.1f}%")
print(f"  completed requests {g('http_reqs','count')}  ({g('http_reqs','rate'):.0f}/s)   never started (dropped) {g('dropped_iterations','count')}")
print(f"  orders in database {(out/'orders-in-db.txt').read_text().strip()}")
rows = [l.split() for l in (out / "metrics.txt").read_text().splitlines() if l.strip()]
def peak(i):
    vals = [float(r[i]) for r in rows if len(r) > i and r[i] not in ("-",)]
    return max(vals) if vals else None
cpu = peak(4)
print(f"  peak: hikari active {peak(1)}  waiting for a connection {peak(2)}  tomcat busy threads {peak(3)}  app cpu {f'{cpu*100:.0f}%' if cpu is not None else '-'}")

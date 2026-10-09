"""What were Tomcat's request threads doing? Reads runs/<label>/threaddump.txt and sorts each
http-nio thread by the first frame of ours, or of a library we call, on its stack.
Usage: python3 bin/threads.py runs/a-pool10-inside-1"""
import re, sys, pathlib
from collections import Counter

dump = (pathlib.Path(sys.argv[1]) / "threaddump.txt").read_text()
threads = [t for t in re.split(r'\n(?=")', dump) if t.startswith('"http-nio-8080-exec')]

def doing(stack):
    if "HikariPool.getConnection" in stack:
        return "waiting for a database connection"
    if "PaymentGateway.charge" in stack:
        return "waiting for the card provider"
    if "ThreadDumpEndpoint" in stack:
        return "taking this thread dump"
    if "org.postgresql" in stack:
        return "waiting on Postgres"
    if "TaskQueue.take" in stack or "TaskQueue.poll" in stack:
        return "idle"
    return "other"

counts = Counter(doing(t) for t in threads)
print(f"tomcat request threads: {len(threads)}")
for what, n in counts.most_common():
    print(f"  {n:>4}  {what}")

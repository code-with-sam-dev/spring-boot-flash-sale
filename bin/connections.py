"""Opens N idle TCP connections to the app and keeps them open, then asks: does a real request still work?
Tests the claim that Tomcat refuses connections once 200 threads and the accept queue are full.
Usage: python3 bin/connections.py 1000 5000 8500"""
import resource, socket, sys, time, urllib.request

resource.setrlimit(resource.RLIMIT_NOFILE, (20000, 20000))
held = []
for target in map(int, sys.argv[1:]):
    refused = timeouts = 0
    while len(held) < target:
        s = socket.socket()
        s.settimeout(3)
        try:
            s.connect(("127.0.0.1", 8080))
            held.append(s)
        except ConnectionRefusedError:
            refused += 1; s.close(); break
        except OSError:
            timeouts += 1; s.close(); break
    time.sleep(2)
    t = time.time()
    try:
        code = urllib.request.urlopen("http://127.0.0.1:8080/products/2", timeout=10).status
    except Exception as e:
        code = type(e).__name__
    print(f"idle connections held {len(held):5d}  new connection refused {refused}  timed out {timeouts}  "
          f"real request -> {code} in {int((time.time()-t)*1000)} ms", flush=True)

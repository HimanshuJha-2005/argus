# 0001: In-Process Runtime Sensors Over eBPF and Reverse Proxies

We chose language-specific in-process bytecode and middleware instrumentation (Java Agent via ByteBuddy, Python ASGI middleware) over eBPF kernel probes or transparent reverse proxies.

Network proxies and eBPF sniffers operate outside the application runtime: they cannot distinguish dynamic path parameters (`/users/1234`) from distinct static routes without statistical heuristics, cannot read framework security contexts (`@PreAuthorize`, authenticated principal IDs) to catch BOLA/IDOR vulnerabilities, and require complex TLS certificate injection or uprobe hooking to inspect encrypted traffic.

In exchange for maintaining separate sensor packages for each language runtime (Java and Python), in-process sensors yield exact framework Route Templates, post-TLS plaintext payloads, and internal security context with zero false positives and sub-millisecond overhead.

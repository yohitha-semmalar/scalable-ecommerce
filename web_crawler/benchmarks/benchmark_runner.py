#!/usr/bin/env python3
"""
ScaleShop Member 5 - Automated Benchmark Runner
Simulates Low (10), Medium (100), and High (500) workloads.
Measures real response times: Average, Median, P90, P95, P99, Throughput (req/s), and Error Rates.
Outputs benchmark_results.json (matching ScaleShop BENCHMARK_EXAMPLE.json) and benchmark_results.csv.
"""

import urllib.request
import urllib.parse
import json
import time
import concurrent.futures
import statistics
import os
import sys

BASE_URL = os.environ.get("CRAWLER_URL", "http://localhost:8081")

def make_request(path, headers=None):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url, headers=headers or {})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            code = response.getcode()
            body = response.read()
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            return {
                "status": code,
                "latency_ms": elapsed_ms,
                "success": (200 <= code < 300),
                "bytes": len(body)
            }
    except Exception as e:
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        return {
            "status": getattr(e, "code", 500),
            "latency_ms": elapsed_ms,
            "success": False,
            "error": str(e)
        }

def run_workload(name, users, requests_per_user, path, headers=None):
    print(f"\n--- Running Workload: {name} ({users} concurrent users, {requests_per_user} reqs/user) ---")
    total_requests = users * requests_per_user
    results = []

    start_total = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=users) as executor:
        futures = []
        for _ in range(total_requests):
            futures.append(executor.submit(make_request, path, headers))

        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())

    total_duration = time.perf_counter() - start_total

    latencies = [r["latency_ms"] for r in results]
    successes = [r for r in results if r["success"]]
    failures = [r for r in results if not r["success"]]

    latencies.sort()
    avg_lat = statistics.mean(latencies) if latencies else 0.0
    med_lat = statistics.median(latencies) if latencies else 0.0
    p90_lat = latencies[int(len(latencies) * 0.90)] if latencies else 0.0
    p95_lat = latencies[int(len(latencies) * 0.95)] if latencies else 0.0
    p99_lat = latencies[int(len(latencies) * 0.99)] if latencies else 0.0
    throughput = len(results) / total_duration if total_duration > 0 else 0.0
    error_rate = (len(failures) / len(results) * 100.0) if results else 0.0

    print(f"  Duration: {total_duration:.2f}s | Throughput: {throughput:.1f} req/s")
    print(f"  Avg: {avg_lat:.1f}ms | Med: {med_lat:.1f}ms | P95: {p95_lat:.1f}ms | P99: {p99_lat:.1f}ms")
    print(f"  Success: {len(successes)} | Errors: {len(failures)} ({error_rate:.1f}%)")

    return {
        "scenario": name,
        "users": users,
        "throughput": round(throughput, 1),
        "average": round(avg_lat, 1),
        "median": round(med_lat, 1),
        "p95": round(p95_lat, 1),
        "p99": round(p99_lat, 1),
        "errorRate": round(error_rate, 2)
    }

def main():
    print(f"Connecting to ScaleShop Crawler Microservice at {BASE_URL}...")
    health = make_request("/api/health")
    if not health["success"]:
        print(f"Warning: Crawler at {BASE_URL} not healthy or starting up. Response: {health}")
    else:
        print("Crawler microservice is HEALTHY.")

    scenarios = [
        # 1. Low Load (Cold Cache / First Search)
        ("Low Load (Cold Cache)", 10, 5, "/api/prices/search?q=AirPods&refresh=true", {"X-Bypass-Cache": "true"}),
        
        # 2. Low Load (Warm Cache / Hit)
        ("Low Load (Warm Cache)", 10, 10, "/api/prices/search?q=AirPods", {}),
        
        # 3. Medium Load (Warm Cache)
        ("Medium Load (Warm Cache)", 100, 10, "/api/prices/search?q=AirPods", {}),
        
        # 4. Medium Load (Diverse Product Searches)
        ("Medium Load (Diverse Searches)", 100, 10, "/api/prices/search?q=iPhone+15", {}),
        
        # 5. Cache Bypassed Load (Direct Upstream Crawling)
        ("Cache Bypassed (Crawl Stress)", 50, 4, "/api/prices/search?q=AirPods&refresh=true", {"X-Bypass-Cache": "true"}),
        
        # 6. High Load Concurrency (500 Users on Warm Cache)
        ("High Load (500 Users Concurrency)", 500, 2, "/api/prices/search?q=AirPods", {})
    ]

    all_results = []
    for name, users, reqs, path, headers in scenarios:
        res = run_workload(name, users, reqs, path, headers)
        all_results.append(res)
        time.sleep(1) # brief pause between scenarios

    # 1. Output ScaleShop BENCHMARK_EXAMPLE.json compatible file
    benchmark_payload = {
        "source": "Member 5 — Apache JMeter / Automated Load Test Suite",
        "simulated": False,
        "results": all_results
    }

    out_json = os.path.join(os.path.dirname(__file__), "benchmark_results.json")
    with open(out_json, "w") as f:
        json.dump(benchmark_payload, f, indent=2)
    print(f"\n[Saved] ScaleShop SystemLab benchmark JSON: {out_json}")

    # 2. Output CSV
    out_csv = os.path.join(os.path.dirname(__file__), "benchmark_results.csv")
    with open(out_csv, "w") as f:
        f.write("scenario,users,throughput,average,median,p95,p99,errorRate\n")
        for r in all_results:
            f.write(f'"{r["scenario"]}",{r["users"]},{r["throughput"]},{r["average"]},{r["median"]},{r["p95"]},{r["p99"]},{r["errorRate"]}\n')
    print(f"[Saved] Benchmark CSV: {out_csv}")

    # 3. Generate Graphs
    graphs_script = os.path.join(os.path.dirname(__file__), "generate_graphs.py")
    if os.path.exists(graphs_script):
        print("\nGenerating Matplotlib evaluation graphs...")
        os.system(f'python "{graphs_script}"')

if __name__ == "__main__":
    main()

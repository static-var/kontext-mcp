import subprocess
import json
import time
import sys
import os
import argparse

# Path to the mcp-server executable
SERVER_CMD = ["/opt/mcp-server/bin/mcp-server", "--config", "/app/config/application.conf"]

QUERIES = [
    "how to create a coroutine",
    "android activity lifecycle",
    "jetpack compose modifier",
    "kotlin flow vs channel",
    "viewmodel scope",
    "recycler view adapter",
    "data classes in kotlin",
    "intent filters",
    "room database migration",
    "dependency injection with hilt"
]

def run_benchmark(threshold):
    print(f"Starting server: {' '.join(SERVER_CMD)}")
    process = subprocess.Popen(
        SERVER_CMD,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=sys.stderr,
        text=True,
        bufsize=0 # Unbuffered
    )

    # MCP Initialize Request
    init_req = {
        "jsonrpc": "2.0",
        "id": 0,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "benchmark", "version": "1.0"}
        }
    }
    
    print("Sending initialize request...")
    start_init = time.time()
    try:
        process.stdin.write(json.dumps(init_req) + "\n")
        process.stdin.flush()
    except BrokenPipeError:
        print("Server died immediately.")
        return

    # Read response
    while True:
        line = process.stdout.readline()
        if not line:
            break
        try:
            resp = json.loads(line)
            if resp.get("id") == 0:
                print(f"Initialized in {time.time() - start_init:.2f}s")
                break
        except json.JSONDecodeError:
            continue

    # Send initialized notification
    process.stdin.write(json.dumps({
        "jsonrpc": "2.0",
        "method": "notifications/initialized"
    }) + "\n")
    process.stdin.flush()

    print(f"\nStarting benchmark with threshold={threshold}...")
    print("-" * 80)
    print(f"{'Query':<40} | {'Time (ms)':<10} | {'Results':<5} | {'Top Score'}")
    print("-" * 80)

    total_time = 0
    
    for i, query in enumerate(QUERIES):
        req = {
            "jsonrpc": "2.0",
            "id": i + 1,
            "method": "tools/call",
            "params": {
                "name": "search_docs",
                "arguments": {
                    "query": query,
                    "similarityThreshold": threshold
                }
            }
        }
        
        start_time = time.time()
        try:
            process.stdin.write(json.dumps(req) + "\n")
            process.stdin.flush()
        except BrokenPipeError:
            print("\nServer process died!")
            break
        
        while True:
            line = process.stdout.readline()
            if not line:
                if process.poll() is not None:
                     print("\nServer process exited unexpectedly!")
                     return
                break
            try:
                resp = json.loads(line)
                if resp.get("id") == i + 1:
                    end_time = time.time()
                    duration_ms = (end_time - start_time) * 1000
                    total_time += duration_ms
                    
                    # Parse result to count chunks
                    result_content = resp.get("result", {}).get("content", [])
                    text = result_content[0].get("text", "") if result_content else ""
                    
                    top_score = "-"
                    if "No documentation chunks matched" in text:
                        chunk_count = 0
                    elif "Found" in text and "chunk(s)" in text:
                        try:
                            chunk_count = text.split("Found")[1].split("chunk(s)")[0].strip()
                            # Extract score from first result: "1. url (score=0.85)"
                            if "(score=" in text:
                                top_score = text.split("(score=")[1].split(")")[0]
                        except:
                            chunk_count = "Err"
                    else:
                        chunk_count = "?"
                    
                    print(f"{query:<40} | {duration_ms:10.2f} | {chunk_count:<5} | {top_score}")
                    break
                elif resp.get("error"):
                    print(f"Error from server: {resp.get('error')}")
            except json.JSONDecodeError:
                continue

    print("-" * 80)
    print(f"Average Latency: {total_time / len(QUERIES):.2f} ms")
    
    process.terminate()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--threshold", type=float, default=0.7, help="Similarity threshold")
    args = parser.parse_args()
    run_benchmark(args.threshold)

import subprocess
import json
import time
import sys
import threading

QUERIES = [
    "jetpack compose state",
    "kotlin coroutines scope",
    "android navigation graph",
    "room database migration",
    "dependency injection hilt"
]

def run_persistent_benchmark():
    print(f"{'Query':<30} | {'Score':<6} | {'Time (s)':<8} | {'Top Result'}", flush=True)
    print("-" * 80, flush=True)

    # Start the process ONCE
    cmd = [
        "docker", "compose", "run", "-T", "--rm", 
        "--entrypoint", "/opt/mcp-server/bin/mcp-server",
        "mcp-server"
    ]
    
    try:
        process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        # Consume stderr in background
        def consume_stderr(p):
            for line in p.stderr:
                print("STDERR:", line.strip(), flush=True)
        
        t = threading.Thread(target=consume_stderr, args=(process,))
        t.daemon = True
        t.start()

        print("Waiting for server initialization (15s)...", flush=True)
        time.sleep(15) 

        # Run queries in the SAME session
        for i, query in enumerate(QUERIES):
            payload = {
                "jsonrpc": "2.0",
                "id": i + 1,
                "method": "tools/call",
                "params": {
                    "name": "search_docs",
                    "arguments": {
                        "query": query
                    }
                }
            }
            json_payload = json.dumps(payload) + "\n"
            
            start_time = time.time()
            try:
                process.stdin.write(json_payload)
                process.stdin.flush()
            except BrokenPipeError:
                print("Error: Broken pipe", flush=True)
                break
            
            # Read response
            result = None
            while True:
                line = process.stdout.readline()
                if not line:
                    break
                
                try:
                    data = json.loads(line)
                    if "result" in data:
                        result = data["result"]
                        break
                    if "error" in data:
                        result = None
                        break
                except json.JSONDecodeError:
                    continue
            
            end_time = time.time()
            duration = end_time - start_time
            
            top_score = 0.0
            top_url = "N/A"
            
            if result and "content" in result:
                for item in result["content"]:
                    if item["type"] == "text":
                        text = item["text"]
                        lines = text.splitlines()
                        for line in lines:
                            if line.strip().startswith("1. "):
                                parts = line.split("(score=")
                                if len(parts) > 1:
                                    try:
                                        score_str = parts[1].rstrip(")")
                                        top_score = float(score_str)
                                        top_url = parts[0].replace("1. ", "").strip()
                                    except ValueError:
                                        pass
                                break
            
            print(f"{query:<30} | {top_score:<6.2f} | {duration:<8.2f} | {top_url[:30]}...", flush=True)
            # No sleep needed between requests in a real session

        process.terminate()
        
    except Exception as e:
        print(f"Exception: {e}", flush=True)

if __name__ == "__main__":
    run_persistent_benchmark()

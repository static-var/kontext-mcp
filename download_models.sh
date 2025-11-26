#!/bin/bash
set -e

MODEL_DIR="/app/models"

# Clean up old models to save space
echo "Cleaning up old models..."
rm -rf "$MODEL_DIR/BAAI_bge-large-en-v1.5"
rm -rf "$MODEL_DIR/BAAI_bge-reranker-base"
rm -rf "$MODEL_DIR/mixedbread-ai_mxbai-embed-large-v1"
rm -rf "$MODEL_DIR/Xenova_snowflake-arctic-embed-l"
rm -rf "$MODEL_DIR/Xenova_mxbai-rerank-large-v1"

mkdir -p "$MODEL_DIR"

download_model() {
    REPO_ID=$1
    SANITIZED_ID=${REPO_ID//\//_}
    SANITIZED_ID=${SANITIZED_ID//:/_}
    TARGET_DIR="$MODEL_DIR/$SANITIZED_ID"
    
    echo "Downloading $REPO_ID to $TARGET_DIR..."
    mkdir -p "$TARGET_DIR"
    
    # Download tokenizer.json
    if [ ! -f "$TARGET_DIR/tokenizer.json" ]; then
        echo "  - tokenizer.json"
        curl -L -f -o "$TARGET_DIR/tokenizer.json" "https://huggingface.co/$REPO_ID/resolve/main/tokenizer.json"
    else
        echo "  - tokenizer.json (exists)"
    fi
    
    # Download model_quantized.onnx
    if [ ! -f "$TARGET_DIR/model_quantized.onnx" ]; then
        echo "  - model_quantized.onnx"
        # Try onnx/model_quantized.onnx first (common in Xenova repos)
        if curl -L -f -o "$TARGET_DIR/model_quantized.onnx" "https://huggingface.co/$REPO_ID/resolve/main/onnx/model_quantized.onnx"; then
            echo "    (downloaded from onnx/ subdirectory)"
        else
            # Fallback to root
            echo "    (trying root directory...)"
            curl -L -f -o "$TARGET_DIR/model_quantized.onnx" "https://huggingface.co/$REPO_ID/resolve/main/model_quantized.onnx"
        fi
    else
        echo "  - model_quantized.onnx (exists)"
    fi
}

# Download Embedding Model
download_model "Snowflake/snowflake-arctic-embed-l"

# Download Reranking Model
download_model "mixedbread-ai/mxbai-rerank-large-v1"

echo "All models downloaded successfully."
ls -R "$MODEL_DIR"

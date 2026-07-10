#!/usr/bin/env bash
#
# gen-jsonl.sh —— 自动生成/追加 AI 辅助开发过程记录（JSONL 格式）
#
# 用法：
#   ./scripts/gen-jsonl.sh -m prompt.txt -a "Kilo Code" -l "Java"
#   ./scripts/gen-jsonl.sh -p "本轮向智能体发送的指令原文" -a "Kilo Code" -l "Java"
#
# 参数：
#   -m FILE   从文件读取本轮 prompt 内容（推荐，多行指令更方便）
#   -p TEXT   直接以字符串传入 prompt 内容（适合单行短指令）
#   -a AGENT  智能体类型：Kilo Code / PI / Cine
#   -l LANG   本次开发语言，如 Java / Go
#   -c HASH   指定 commit（默认使用 HEAD，即最近一次提交）
#   -o FILE   输出的 jsonl 文件路径（默认 ./dev-log.jsonl）
#   -h        显示帮助
#
# 典型工作流：
#   1. 把本轮发给智能体的 prompt 写入 prompt.txt（或直接用 -p 传字符串）
#   2. 智能体修改代码后，正常 git add + git commit
#   3. 运行本脚本，自动抓取最近一次 commit 的 diff、hash、时间，
#      连同 prompt 一起追加写入 jsonl 文件
#
set -euo pipefail

OUTPUT_FILE="dev-log.jsonl"
COMMIT="HEAD"
PROMPT_FILE=""
PROMPT_TEXT=""
AGENT_TYPE=""
DEV_LANGUAGE=""

usage() {
  grep '^#' "$0" | sed 's/^#//' | sed '1d'
  exit 1
}

while getopts "m:p:a:l:c:o:h" opt; do
  case "$opt" in
    m) PROMPT_FILE="$OPTARG" ;;
    p) PROMPT_TEXT="$OPTARG" ;;
    a) AGENT_TYPE="$OPTARG" ;;
    l) DEV_LANGUAGE="$OPTARG" ;;
    c) COMMIT="$OPTARG" ;;
    o) OUTPUT_FILE="$OPTARG" ;;
    h) usage ;;
    *) usage ;;
  esac
done

# --- 依赖检查 ---
command -v jq >/dev/null 2>&1 || { echo "错误：需要安装 jq（brew install jq / apt install jq）" >&2; exit 1; }
command -v git >/dev/null 2>&1 || { echo "错误：需要安装 git" >&2; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "错误：当前目录不是 git 仓库" >&2; exit 1; }

# --- 参数校验 ---
if [[ -z "$PROMPT_FILE" && -z "$PROMPT_TEXT" ]]; then
  echo "错误：必须通过 -m FILE 或 -p TEXT 提供本轮 prompt 内容" >&2
  usage
fi
if [[ -n "$PROMPT_FILE" && ! -f "$PROMPT_FILE" ]]; then
  echo "错误：prompt 文件不存在: $PROMPT_FILE" >&2
  exit 1
fi
if [[ -z "$AGENT_TYPE" ]]; then
  echo "错误：必须通过 -a 指定 agent_type（Kilo Code / PI / Cine）" >&2
  exit 1
fi
if [[ -z "$DEV_LANGUAGE" ]]; then
  echo "错误：必须通过 -l 指定 dev_language" >&2
  exit 1
fi

# --- 读取 prompt 内容 ---
if [[ -n "$PROMPT_FILE" ]]; then
  PROMPT_CONTENT="$(cat "$PROMPT_FILE")"
else
  PROMPT_CONTENT="$PROMPT_TEXT"
fi

# --- 解析 commit 信息 ---
COMMIT_HASH="$(git rev-parse "$COMMIT")"

# 判断是否有父 commit（避免首次提交时 diff 报错）
if git rev-parse "${COMMIT_HASH}^" >/dev/null 2>&1; then
  MODIFY_DIFF="$(git diff "${COMMIT_HASH}^" "${COMMIT_HASH}")"
else
  # 首次提交，没有父提交，diff 全部新增内容
  MODIFY_DIFF="$(git show "${COMMIT_HASH}" --pretty=format:"" --patch)"
fi

# 提交时间转换为 YYYY-MM-DD HH:MM:SS（去掉时区）
MODIFY_TIME="$(git show -s --format=%ci "${COMMIT_HASH}" | cut -d' ' -f1,2)"

# --- 计算 round_id（自增，基于已有行数）---
if [[ -f "$OUTPUT_FILE" ]]; then
  ROUND_ID=$(($(wc -l < "$OUTPUT_FILE" | tr -d ' ') + 1))
else
  ROUND_ID=1
fi

# --- 用 jq 安全构建 JSON（避免手写转义出错）---
JSON_LINE=$(jq -nc \
  --argjson round_id "$ROUND_ID" \
  --arg prompt_content "$PROMPT_CONTENT" \
  --arg modify_diff "$MODIFY_DIFF" \
  --arg commit_hash "$COMMIT_HASH" \
  --arg modify_time "$MODIFY_TIME" \
  --arg agent_type "$AGENT_TYPE" \
  --arg dev_language "$DEV_LANGUAGE" \
  '{
    round_id: $round_id,
    prompt_content: $prompt_content,
    modify_diff: $modify_diff,
    commit_hash: $commit_hash,
    modify_time: $modify_time,
    agent_type: $agent_type,
    dev_language: $dev_language
  }')

echo "$JSON_LINE" >> "$OUTPUT_FILE"

echo "✅ 已写入第 ${ROUND_ID} 轮记录到 ${OUTPUT_FILE}"
echo "   commit: ${COMMIT_HASH:0:12}"
echo "   time:   ${MODIFY_TIME}"
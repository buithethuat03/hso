#!/bin/sh
# Tắt server an toàn KHÔNG dùng container: gửi packet opcode -1 từ localhost để server tự
# lưu dữ liệu (SaveData.process) rồi thoát. Dùng cho systemd ExecStop hoặc chạy tay.
# Với Docker, `docker compose stop` đã an toàn nhờ shutdown hook (core/Start.java) + stop_grace_period.
HOST=127.0.0.1
PORT="${1:-19129}"
printf '\377\000\000' | nc -w 3 "$HOST" "$PORT" 2>/dev/null \
  || (exec 3<>/dev/tcp/$HOST/$PORT && printf '\377\000\000' >&3 && sleep 1)
# Chờ server lưu xong và tự thoát.
sleep 15

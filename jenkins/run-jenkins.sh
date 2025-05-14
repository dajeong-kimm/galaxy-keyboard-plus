#!/usr/bin/env bash
set -e

# 호스트 docker.sock GID를 읽어 컨테이너에 주입
SOCK_GID=$(stat -c '%g' /var/run/docker.sock)

docker build -t jenkins-docker:latest "$(dirname "$0")"

docker rm -f jenkins 2>/dev/null || true
docker volume create jenkins_home >/dev/null 2>&1 || true

docker run -d --name jenkins \
  -p 8088:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --group-add "${SOCK_GID}" \          # ★ docker 권한 자동 해결
  --restart unless-stopped \
  jenkins-docker:latest

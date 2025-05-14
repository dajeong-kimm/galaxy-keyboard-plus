FROM jenkins/jenkins:lts-jdk17

USER root
RUN apt-get update \
 && apt-get install -y docker.io docker-compose-plugin \
 && usermod -aG docker jenkins \
 && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && apt-get clean

USER jenkins

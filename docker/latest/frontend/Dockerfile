FROM gradle:7.6-jdk17 AS builder

COPY --chown=gradle:gradle . /home/gradle/src/
WORKDIR /home/gradle/src/
RUN gradle clean distZip -Pdistribution

FROM eclipse-temurin:17.0.7_7-jre

RUN apt-get update \
  && apt-get -y install unzip wget bash \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir /app

COPY --from=builder /home/gradle/src/hermes-frontend/build/distributions/*.zip /app/hermes-frontend.zip
RUN unzip /app/hermes-frontend.zip && mv /hermes-frontend-* /hermes-frontend

ADD docker/latest/frontend/frontend.yaml /etc/hermes/frontend.yaml
ADD docker/latest/frontend/logback.xml /etc/hermes/logback.xml
ENV HERMES_FRONTEND_OPTS="-Dspring.config.location=file:///etc/hermes/frontend.yaml -Dlogback.configurationFile=/etc/hermes/logback.xml"

CMD /hermes-frontend/bin/hermes-frontend
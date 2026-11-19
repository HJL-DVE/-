# ============================================================
# TeaClub microservice runtime image
#
# The jar is built by the HOST Maven (reuses the host 1.58GB .m2 cache,
# building in ~7s) and then COPY'd into this image.
#
# Build steps:
#   cd teaclub-cloud && mvn -DskipTests -pl payment-service -am clean package
#   docker build -f payment-service/Dockerfile -t teaclub/payment-service:1.0.0 payment-service
# ============================================================

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai"

RUN groupadd -r teaclub && useradd -r -g teaclub teaclub

COPY target/*.jar /app/app.jar
RUN chown -R teaclub:teaclub /app
USER teaclub

EXPOSE 9006
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

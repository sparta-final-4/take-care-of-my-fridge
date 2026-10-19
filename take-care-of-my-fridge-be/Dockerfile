# 배포 이미지: App 서버(EC2 t4g, arm64)의 docker compose가 실행한다(imhungry-infra compose-app.yml).
# 접속 정보·시크릿·힙 크기(JAVA_TOOL_OPTIONS)는 모두 compose가 환경변수로 넣는다.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스와 분리해 소스만 바뀌면 다운로드를 다시 하지 않는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
# 테스트는 CI(be-ci.yml)에서 이미 통과한 뒤에만 배포 이미지를 만든다.
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

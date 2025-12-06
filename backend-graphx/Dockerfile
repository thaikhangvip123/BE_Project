FROM sbtscala/scala-sbt:graalvm-ce-22.3.3-b1-java17_1.11.7_2.12.20


WORKDIR /app

COPY . /app

# Cache dependencies
RUN sbt update

RUN sbt assembly

EXPOSE 9000

CMD ["java", "-jar", "target/scala-2.12/backend-graphx-assembly-0.1.0.jar"]

ThisBuild / scalaVersion := "2.12.0"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "com.graphx"

lazy val http4sVersion = "0.23.27"
lazy val sparkVersion = "3.5.7"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.4",
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-core" % "0.14.7",
  "io.circe" %% "circe-generic" % "0.14.7",
  "io.circe" %% "circe-parser" % "0.14.7",
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-graphx" % sparkVersion,
  "mysql" % "mysql-connector-java" % "8.0.33"
)

Compile / run / fork := true

Compile / run / javaOptions ++= Seq(
  "-Dspark.master=local[*]",
  "-Dspark.driver.host=127.0.0.1"
)



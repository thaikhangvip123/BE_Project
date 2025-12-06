package com.graphx

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.{Host, Port, ipv4}
import com.graphx.config.AppConfig
import com.graphx.http.RankRoutes
import com.graphx.service.GraphAnalytics
import org.apache.spark.sql.SparkSession
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    val config = AppConfig.load()

    val spark = SparkSession
      .builder()
      .appName("GraphX PageRank Backend")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "200")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val analytics = new GraphAnalytics(spark, config)
    analytics.bootstrap()

    val httpApp = new RankRoutes(analytics, config).routes.orNotFound

    val host: Host = ipv4"0.0.0.0"
    val port: Port = Port.fromInt(config.httpPort).getOrElse(Port.fromInt(9000).get)

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(httpApp)
      .build
      .useForever
      .guarantee(IO.blocking(spark.stop()))
  }
}



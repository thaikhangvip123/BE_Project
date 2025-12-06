package com.graphx.config

import java.nio.file.{Files, Paths}

final case class AppConfig(
    dbHost: String,
    dbPort: Int,
    dbName: String,
    dbUser: String,
    dbPassword: String,
    dataDir: String,
    httpPort: Int,
    pagerankIterations: Int,
    pagerankResetProb: Double,
    tolerance: Double,
    maxProductsPerOrder: Int
) {
  val jdbcUrl: String =
    s"jdbc:mysql://$dbHost:$dbPort/$dbName?useSSL=false&serverTimezone=UTC&rewriteBatchedStatements=true"
}

object AppConfig {
  def load(): AppConfig = {
    val cwd = Paths.get("").toAbsolutePath
    val parent = Option(cwd.getParent).getOrElse(cwd)
    val defaultDataDir = parent.toString

    val dirFromEnv = sys.env.get("DATA_DIR") match {
      case Some(path) if Files.isDirectory(Paths.get(path)) => path
      case _                                               => defaultDataDir
    }

    AppConfig(
      dbHost = sys.env.getOrElse("DB_HOST", "localhost"),
      dbPort = sys.env.get("DB_PORT").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(3306),
      dbName = sys.env.getOrElse("DB_NAME", "graphdb"),
      dbUser = sys.env.getOrElse("DB_USER", "root"),
      dbPassword = sys.env.getOrElse("DB_PASS", "Jacuby123"),
      dataDir = dirFromEnv,
      httpPort = sys.env.get("HTTP_PORT").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(9000),
      pagerankIterations = sys.env.get("PAGERANK_ITER").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(20),
      pagerankResetProb = sys.env.get("RESET_PROB").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(0.15),
      tolerance = sys.env.get("RANK_TOL").flatMap(s => scala.util.Try(s.toDouble).toOption).getOrElse(1e-6),
      maxProductsPerOrder = sys.env.get("MAX_PRODUCTS_PER_ORDER").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(40)
    )
  }
}



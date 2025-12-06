package com.graphx.service

import com.graphx.config.AppConfig
import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{Dataset, Row, SaveMode, SparkSession}

import java.sql.DriverManager
import java.util.Properties
import scala.collection.mutable

final case class AlgorithmResult(
    algorithm: String,
    iterations: Int,
    rowsWritten: Long,
    sourceId: Option[Long] = None
)

final case class ProductVertex(nodeId: Long, label: String)
final case class OrderProduct(orderId: Long, productId: Long)

class GraphAnalytics(spark: SparkSession, config: AppConfig) {
  import spark.implicits._

  private val jdbcProps = {
    val props = new Properties()
    props.setProperty("user", config.dbUser)
    props.setProperty("password", config.dbPassword)
    props.setProperty("driver", "com.mysql.cj.jdbc.Driver")
    props
  }

  private val productsDs: Dataset[ProductVertex] = loadProducts().cache()
  private val graph: Graph[String, Double] = buildGraph().cache()

  def bootstrap(): Unit = {
    writeNodesToMySql()
  }

  def computePageRank(iterations: Int, resetProb: Double): AlgorithmResult = {
    val prGraph = graph.staticPageRank(iterations, resetProb)
    val vertices = prGraph.vertices
    val df = vertices
      .map { case (vertexId, score) => (vertexId.toInt, score) }
      .toDF("node_id", "score")

    val rowCount = df.count()

    df.write
      .mode(SaveMode.Overwrite)
      .option("truncate", "true")
      .jdbc(config.jdbcUrl, "pagerank", jdbcProps)

    AlgorithmResult(
      algorithm = "pagerank",
      iterations = iterations,
      rowsWritten = rowCount
    )
  }

  def computePersonalizedPageRank(sourceId: Long, iterations: Int, resetProb: Double): AlgorithmResult = {
    val personalized = graph
      .staticPersonalizedPageRank(sourceId, iterations, resetProb)
      .vertices

    val df = personalized
      .filter { case (_, score) => score > 0.0 }
      .map { case (vertexId, score) => (vertexId.toInt, sourceId.toInt, score) }
      .toDF("node_id", "source_id", "score")

    val rowCount = df.count()
    deleteExistingPpr(sourceId)

    df.write
      .mode(SaveMode.Append)
      .jdbc(config.jdbcUrl, "ppr", jdbcProps)

    AlgorithmResult(
      algorithm = "personalized-pagerank",
      iterations = iterations,
      rowsWritten = rowCount,
      sourceId = Some(sourceId)
    )
  }

  def vertexCount: Long = graph.numVertices
  def edgeCount: Long = graph.numEdges

  private def loadProducts(): Dataset[ProductVertex] = {
    val path = s"${config.dataDir}/products.csv"
    spark.read
      .option("header", "true")
      .csv(path)
      .select(col("product_id"), col("product_name"))
      .na.drop()
      .map { row =>
        ProductVertex(
          nodeId = row.getString(0).toLong,
          label = row.getString(1)
        )
      }
  }

  private def loadOrderProducts(): Dataset[OrderProduct] = {
    val prior = readOrderCsv("order_products__prior.csv")
    val train = readOrderCsv("order_products__train.csv")
    prior.union(train).cache()
  }

  private def readOrderCsv(fileName: String): Dataset[OrderProduct] = {
    val path = s"${config.dataDir}/$fileName"
    spark.read
      .option("header", "true")
      .csv(path)
      .select(col("order_id"), col("product_id"))
      .na.drop()
      .map { row =>
        OrderProduct(
          orderId = row.getString(0).toLong,
          productId = row.getString(1).toLong
        )
      }
  }

  private def buildGraph(): Graph[String, Double] = {
    val orderProducts = loadOrderProducts()

    val vertices: RDD[(Long, String)] = productsDs.rdd.map { p =>
      (p.nodeId, p.label)
    }

    val grouped: RDD[(Long, Iterable[Long])] = orderProducts.rdd
      .map(op => (op.orderId, op.productId))
      .groupByKey()

    val edges: RDD[Edge[Double]] = grouped.flatMap { case (_, productIds) =>
      val unique = mutable.LinkedHashSet(productIds.toSeq: _*)
      val limited = unique.toSeq.take(config.maxProductsPerOrder)

      if (limited.length < 2) {
        Seq.empty
      } else {
        for {
          (src, idx) <- limited.zipWithIndex
          dst <- limited.drop(idx + 1)
        } yield {
          val weight = 1.0
          Seq(
            Edge(src, dst, weight),
            Edge(dst, src, weight)
          )
        }
      }
    }.flatMap(identity)

    Graph(vertices, edges, "unknown")
  }

  private def writeNodesToMySql(): Unit = {
    productsDs
      .toDF("node_id", "label")
      .write
      .mode(SaveMode.Overwrite)
      .option("truncate", "true")
      .jdbc(config.jdbcUrl, "nodes", jdbcProps)
  }

  private def deleteExistingPpr(sourceId: Long): Unit = {
    Class.forName("com.mysql.cj.jdbc.Driver")
    val conn = DriverManager.getConnection(config.jdbcUrl, config.dbUser, config.dbPassword)
    val statement = conn.prepareStatement("DELETE FROM ppr WHERE source_id = ?")
    try {
      statement.setLong(1, sourceId)
      statement.executeUpdate()
    } finally {
      statement.close()
      conn.close()
    }
  }
}



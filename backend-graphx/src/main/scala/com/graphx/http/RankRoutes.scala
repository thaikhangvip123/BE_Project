package com.graphx.http

import cats.effect.IO
import com.graphx.config.AppConfig
import com.graphx.service.{AlgorithmResult, GraphAnalytics}
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.dsl.io._

object QueryParams {
  object IterationsParam extends OptionalQueryParamDecoderMatcher[Int]("iterations")
  object NodeIdParam extends OptionalQueryParamDecoderMatcher[Long]("nodeId")
}

final case class HealthPayload(
    status: String,
    vertices: Long,
    edges: Long
)

class RankRoutes(analytics: GraphAnalytics, config: AppConfig) {
  import QueryParams._

  implicit val algorithmEncoder: Encoder[AlgorithmResult] = deriveEncoder
  implicit val healthEncoder: Encoder[HealthPayload] = deriveEncoder

  private val defaultIterations = config.pagerankIterations

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      val payload = HealthPayload(
        status = "ok",
        vertices = analytics.vertexCount,
        edges = analytics.edgeCount
      )
      Ok(payload.asJson)

    case GET -> Root / "pagerank" :? IterationsParam(iterOpt) =>
      val iterations = iterOpt.getOrElse(defaultIterations)
      IO.blocking(analytics.computePageRank(iterations, config.pagerankResetProb))
        .flatMap(result => Ok(result.asJson))

    case GET -> Root / "ppr" :? NodeIdParam(Some(nodeId)) +& IterationsParam(iterOpt) =>
      val iterations = iterOpt.getOrElse(defaultIterations)
      IO.blocking(analytics.computePersonalizedPageRank(nodeId, iterations, config.pagerankResetProb))
        .flatMap(result => Ok(result.asJson))

    case GET -> Root / "ppr" =>
      BadRequest(
        Map("message" -> "Thiếu tham số nodeId").asJson
      )
  }
}



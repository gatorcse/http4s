package org.http4s
package server
package middleware

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.server.syntax._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.headers._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

class GZipSpec extends Http4sSpec {
  implicit val S = IO.ioSemigroup[MaybeResponse[IO]]

  "GZip" should {
    "fall through if the route doesn't match" in {
      val service = GZip(HttpService.empty[IO]) |+| HttpService[IO] {
        case GET -> Root => Ok("pong")
      }
      val req =
        Request[IO](Method.GET, Uri.uri("/")).putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      val resp = service.orNotFound(req).unsafeRunSync()
      resp.status must_== (Status.Ok)
      resp.headers.get(`Content-Encoding`) must beNone
    }

    checkAll(
      "encoding",
      new Properties("GZip") {
        property("middleware encoding == GZIPOutputStream encoding") = forAll {
          vector: Vector[Array[Byte]] =>
            val service: HttpService[IO] = HttpService[IO] {
              case GET -> Root => Ok(Stream.emits(vector).covary[IO])
            }
            val gzipService: HttpService[IO] = GZip(service)
            val req: Request[IO] = Request[IO](Method.GET, Uri.uri("/"))
              .putHeaders(`Accept-Encoding`(ContentCoding.gzip))
            val actual: IO[Array[Byte]] = gzipService.orNotFound(req).as[Chunk[Byte]].map(_.toArray)

            val byteArrayStream = new ByteArrayOutputStream()
            val gzipStream = new GZIPOutputStream(byteArrayStream)
            vector.foreach(gzipStream.write)
            gzipStream.close()
            val expected = byteArrayStream.toByteArray

            actual must returnValue(expected)
        }
      }
    )
  }
}

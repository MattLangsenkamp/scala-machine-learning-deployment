import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import java.nio.file.{Files, Paths}
import io.gatling.core.feeder.BatchableFeederBuilder
import scala.concurrent.duration.*

class MultiImageSimulation extends Simulation:

  val jwtKey: String = sys.env("IT_JWT_KEY")

  val feeder = csv("images.csv").transform { case ("file", f) =>
    getFileBytes(f)
  }.circular

  def getFileBytes(path: String): Array[Byte] =
    Files.readAllBytes(Paths.get(this.getClass().getClassLoader().getResource(path).toURI()))

  val headers = Map(
    """Accept""" -> """text/html,application/json,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8""",
    """Content-Type""" -> """multipart/form-data; boundary=---------------------------19577276091468095107188555610""",
    """Authorization""" -> s"""Bearer $jwtKey"""
  )

  val httpProtocol = http
    .baseUrl("http://localhost:8080/")
    .headers(headers)
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val files = (0 until 10).map(ind =>
    ByteArrayBodyPart("file", "#{file}")
      .fileName(f"#{label}_$ind.jpg")
      .contentType("image/jpeg")
  )

  val scn = scenario("Simple Inference")
    .exec(http("model details").get("/infer/model_info"))
    .pause(1)
    .feed(feeder)
    .exec {
      http("data")
        .post("/infer/infer?model=yolov8_1&top_k=10&batch_size=1")
        .bodyParts(
          files*
        )
    }

  setUp(
    scn.inject(rampUsers(100).during(1.seconds)).protocols(httpProtocol)
  )

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import java.nio.file.{Files, Paths}
import io.gatling.core.feeder.BatchableFeederBuilder
import scala.concurrent.duration.*

class BasicSimulation extends Simulation:

  val jwtKey: String = sys.env("IT_JWT_KEY")

  val feeder = csv("data.csv").transform { case ("file", f) =>
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
    .baseUrl("http://localhost:8009")
    .headers(headers)
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")

  val scn = scenario("Simple Inference")
    // .exec(http("model details").get("/"))
    // .pause(1)
    .feed(feeder)
    .exec {
      http("data")
        .post("/infer")
        .bodyPart(
          ByteArrayBodyPart("file", "#{file}")
            .fileName("#{fname}.jpg")
            .contentType("image/jpeg")
        )
        .check(substring("#{label}"))
    }

  setUp(
    scn.inject(rampUsers(10).during(5.seconds)).protocols(httpProtocol)
  )

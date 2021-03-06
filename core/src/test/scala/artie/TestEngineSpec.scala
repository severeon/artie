package artie

import artie.TestEngine._
import artie.implicits._

import scalaj.http._
import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

import scala.concurrent.Future
import scala.concurrent.duration._

final class TestEngineSpec(implicit ee: ExecutionEnv) extends Specification {

  val read = new Read[User] {
    def apply(raw: String): Either[String, User] = {
      val Array(name, age) = raw.split(",")

      if (name == "fail")
        Left("expected")
      else
        Right(User(name, age.toInt))
    }
  }

  "TestEngine" >> {
    "RequestT to HttpRequest" >> {
      def compare(l: HttpRequest, r: HttpRequest) = {
        l.copy(options = Nil) === r.copy(options = Nil)
      }

      compare(toHttpRequest("base", get("/uri"), 1.second), Http("base/uri").option(HttpOptions.readTimeout(1000)))
      compare(toHttpRequest("base", post("/uri", contentO = Some("content")), 1.second), Http("base/uri").option(HttpOptions.readTimeout(1000)).postData("content"))
      compare(toHttpRequest("base", put("/uri", contentO = Some("content")), 1.second), Http("base/uri").option(HttpOptions.readTimeout(1000)).put("content"))
      compare(toHttpRequest("base", delete("/uri"), 1.second),  Http("base/uri").option(HttpOptions.readTimeout(1000)).method("DELETE"))
    }

    "compare HttpResponses from base and refactored" >> {
      val initState = TestState(0, 0, 0)

      val req   = get("/test")
      val resp0 = HttpResponse("John,10", 200, Map.empty)
      val resp1 = HttpResponse("Jo,20", 200, Map.empty)

      compareResponses(req, resp0, resp0, read, initState, 1) === TestState(1, 0, 0)
      compareResponses(req, resp0.copy(code = 404), resp0.copy(code = 404), read, initState, 1) === TestState(0, 1, 0, Seq(
        ResponseCodeDiff(req, "invalid:\n  HttpResponse(John,10,404,Map())\n  HttpResponse(John,10,404,Map())"))
      )
      compareResponses(req, resp0.copy(code = 404), resp0.copy(code = 500), read, initState, 1) === TestState(0, 0, 1, Seq(
        ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())"),
        ResponseCodeDiff(req, "refactored service status error:\n  HttpResponse(John,10,500,Map())")
      ))
      compareResponses(req, resp0.copy(code = 404), resp0, read, initState, 1) === TestState(0, 0, 1, Seq(
        ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())"))
      )
      compareResponses(req, resp0, resp0.copy(code = 404), read, initState, 1) === TestState(0, 0, 1, Seq(
        ResponseCodeDiff(req, "refactored service status error:\n  HttpResponse(John,10,404,Map())"))
      )
      compareResponses(req, resp0, resp1, read, initState, 1).copy(reasons = Nil) === TestState(0, 0, 1, Nil)

      val previousState = TestState(0, 0, 1, Seq(ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())")))
      compareResponses(req, resp0.copy(code = 405), resp0, read, previousState, 1) === TestState(0, 0, 2, Seq(
        ResponseCodeDiff(req, "base service status error:\n  HttpResponse(John,10,404,Map())"))
      )
    }

    "run test cases in batches of size `n`" >> {
      import shapeless._
      import scala.util.Random

      val conf = Config("base", 0, "ref", 1)

      val prov0 = provide[Unit].static(())
      val provs = Providers ~ ('ignore, prov0)

      val resp0 = HttpResponse("John,10", 200, Map.empty)

      def gen[P <: HList](pf: Future[P]): Random => P => RequestT = 
        _ => _ => get("test")

      run(null, provs, conf, gen(provs), read, _ => resp0) must beEqualTo(TestState(1, 0, 0)).awaitFor(1.second)
      run(null, provs, conf.repetitions(10), gen(provs), read, _ => resp0) must beEqualTo(TestState(10, 0, 0)).awaitFor(1.second)
      run(null, provs, conf.repetitions(10).parallelism(2), gen(provs), read, _ => resp0) must beEqualTo(TestState(10, 0, 0)).awaitFor(1.second)

      // exception handling
      val req = gen(provs)
      run(null, provs, conf, req, read, _ => throw new java.net.SocketTimeoutException("test")) must beEqualTo(TestState(0, 0, 1, Seq(RequestTimeoutDiff(req(null)(null), "test")))).awaitFor(1.second)
      run(null, provs, conf, req, read, _ => throw new NullPointerException("test")) must throwA(new NullPointerException("test")).awaitFor(1.second)

      val fakeIo: HttpRequest => HttpResponse[String] = {
        var counter = 0

        request => {
          if (counter == 0) {
            counter += 1
            resp0.copy(code = 500)
          }
          else
            resp0
        }
      }

      run(null, provs, conf.repetitions(2).stopOnFailure(true), gen(provs), read, fakeIo).map(_.copy(reasons = Nil)) must beEqualTo(TestState(0, 0, 1)).awaitFor(1.second)
    }
  }
}

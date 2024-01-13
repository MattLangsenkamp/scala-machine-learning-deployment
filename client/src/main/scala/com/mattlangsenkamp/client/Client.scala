import tyrian.*
import tyrian.CSS
import tyrian.CSS.*
import tyrian.Sub.*
import tyrian.Html.*
import tyrian.cmds.*
import tyrian.syntax.*
import cats.effect.IO
import cats.syntax.*
import cats.implicits.*
import org.scalajs.dom.{RequestCredentials, RequestMode, document}
import scala.scalajs.js.annotation.*
import org.http4s.dom.FetchClientBuilder

import cats.effect.std.Env
import cats.effect.implicits.*
import cats.effect.syntax.*
import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.*

import org.scalajs.dom.HTMLImageElement
import tyrian.cmds.File
import org.scalajs.dom
import org.scalajs.dom.html
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.syntax.*
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.scalajs.dom.{Fetch, HttpMethod, BodyInit, RequestInit, FormData, HeadersInit}
import scalajs.js.Thenable.Implicits.thenable2future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => jsGlobal}
import scala.scalajs.js.`import`.meta
import org.scalajs.dom.HTMLFormElement
import scala.concurrent.Await
import cats.effect.Deferred
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.*
import scala.concurrent.Future
import com.mattlangsenkamp.core.ImageClassification.*
import org.http4s.Header
import org.http4s.Headers

@JSExportTopLevel("TyrianApp")
object Client extends TyrianApp[Msg, Model]:

  private val client = FetchClientBuilder[IO]
    .withMode(RequestMode.cors)
    .withCredentials(RequestCredentials.include)
    .create

  type Filename    = String
  type Label       = String
  type Probability = Float

  type LabelProbabilities = Map[Label, Probability]

  type ClassificationOutput = Map[Filename, LabelProbabilities]

  def myFetch(
      token: String,
      model: Model
  ) =
    model.currentModel.fold(Cmd.emit(Msg.NoOp)) { curModel =>
      val init = new RequestInit {}
      val headers =
        Map
          .empty[String, org.scalajs.dom.ByteString]
          .asInstanceOf[js.Dictionary[org.scalajs.dom.ByteString]]
      headers("Authorization") = s"Bearer ${token}"

      val body = FormData(document.querySelector("form").asInstanceOf[HTMLFormElement])

      init.method = HttpMethod.POST
      init.body = body
      init.headers = headers

      val co = for
        jsPromise <- dom.fetch(
          f"${model.serverUri}/infer/infer?model=${curModel.modelName}&top_k=10&batch_size=${curModel.batchSize}",
          init
        )
        text <- jsPromise.text()
      yield Msg.SetResults(parse(text).toOption.flatMap(_.as[ClassificationOutput].toOption))
      Cmd.Run(IO.fromFuture(IO(co)))
    }

  def router: Location => Msg =
    case loc: Location.Internal =>
      loc.pathName match
        case "/" =>
          Msg.NoOp
        case oauthCodeParam if oauthCodeParam.contains("callback") =>
          Msg.ConsumeOauthCode(loc.fullPath)
        case _ => Msg.NoOp

    case loc: Location.External =>
      Msg.NavigateToUrl(loc.href)

  def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    (
      Model(
        Option.empty,
        Option.empty,
        "http://localhost:5173/callback",
        "http://localhost:8080",
        Option.empty,
        List.empty,
        Option.empty
      ),
      Cmd.Batch(Cmd.emit(Msg.LookForJWT), Cmd.emit(Msg.GetEnvVars))
    )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) =
    case Msg.JumpToHome          => (model, Nav.pushUrl("/"))
    case Msg.NavigateToUrl(href) => (model, Nav.loadUrl(href))
    case Msg.SetJWT(token) =>
      val cmd = LocalStorage.setItem[IO, Msg]("Authorization", token) {
        case LocalStorage.Result.Success => Msg.UseJWT(token)
        case e                           => Msg.ConsoleLog(e.toString)
      }
      (model, cmd)
    case Msg.LookForJWT =>
      val cmd = LocalStorage.getItem[IO, Msg]("Authorization") {
        case Right(LocalStorage.Result.Found(value)) => Msg.UseJWT(value)
        case Left(LocalStorage.Result.NotFound(e))   => Msg.ConsoleLog(e.toString)
      }
      (model, cmd)
    case Msg.UseJWT(token) =>
      (
        model.copy(authorizationJWT = Some(token)),
        Cmd.Batch(Cmd.emit(Msg.JumpToHome), Cmd.emit(Msg.GetAvailableModels))
      )
    case Msg.ConsumeOauthCode(pathWithCode) =>
      val code = pathWithCode.replace("/callback?code=", "")
      val io: IO[Msg] =
        client
          .expect[String](f"${model.serverUri}/auth/access_token?code=$code")
          .map(s => Msg.SetJWT(s.replace("\"", "")))
          .handleError(_ => Msg.ConsoleLog(f"failed to login with $code"))
      (model, Cmd.Run(io))
    case Msg.NoOp => (model, Cmd.None)
    case Msg.ConsoleLog(log) =>
      (
        model,
        Cmd.SideEffect {
          println(log)
        }
      )
    case Msg.LoadImage =>
      (
        model,
        FileReader.readText("image-upload") {
          case FileReader.Result.Error(msg)                 => Msg.ConsoleLog(msg)
          case FileReader.Result.File(name, path, contents) => Msg.UseImage(contents)
        }
      )
    case Msg.UseImage(image) =>
      (model.copy(image = Some(image)), Cmd.None)
    case Msg.GetEnvVars =>
      // if it is in dev mode then use the defaults from init,
      // if it is from docker local, then use env file, else use mldemo uri
      if meta.env.MODE.toString == "production" then
        val vcb = meta.env.VITE_CALLBACK.toString
        val cb =
          if vcb != "undefined" then vcb
          else "https://mldemo.mattlangsenkamp.com/callback"
        val v_server_uri = meta.env.VITE_SERVER_URI.toString
        val server_uri =
          if v_server_uri != "undefined" then v_server_uri
          else "https://mldemo.mattlangsenkamp.com"
        (model.copy(callbackUri = cb), Cmd.None)
      else (model, Cmd.None)

    case Msg.UploadImage =>
      val cmd = model.authorizationJWT.fold(Cmd.None)(myFetch(_, model))
      (model, cmd)

    case Msg.SetResults(res) =>
      (model.copy(classificationOutput = res), Cmd.None)
    case Msg.GetAvailableModels =>
      val io = model.authorizationJWT.fold(Msg.NoOp.pure[IO]) { token =>
        val h = Headers(Header("Authorization", s"Bearer ${token}"))
        val r = Request[IO](
          Method.GET,
          uri = Uri.fromString(f"${model.serverUri}/infer/model_info").right.get,
          headers = h
        )
        client
          .expect[String](r)
          .map(str => parse(str).toOption.flatMap(_.as[List[ModelInfo]].toOption).get)
          .map(mis => Msg.SetAvailableModels(mis))
          .handleError(_ => Msg.ConsoleLog(f"failed get available models"))
      }
      (model, Cmd.Run(io))
    case Msg.SetAvailableModels(models) =>
      (model.copy(models = models), Cmd.Emit(Msg.SetCurrentModel(models.head)))
    case Msg.SetCurrentModel(curModel) =>
      (model.copy(currentModel = Some(curModel)), Cmd.None)
  def githubPage(callback: String) =
    s"https://github.com/login/oauth/authorize?scope=user:email&client_id=1a9ebd723f6ce63aef11&redirect_uri=$callback"

  def view(model: Model): Html[Msg] =

    val wrapperStyle = style(
      CSS.width("100%") |+| CSS.height("100%") |+|
        CSS.display("flex")
    )

    val bStyle = style(
      CSS.margin("auto") |+| CSS.width("fit-content") |+| CSS.height("fit-content") |+|
        CSS.display("flex")
    )

    val iStyle = style(
      CSS.margin("auto") |+| CSS.width("fit-content") |+| CSS.display("flex") |+|
        CSS.flexDirection("column") |+| CSS.height("fit-content")
    )

    val fStyle = style(CSS.display("flex") |+| CSS.flexDirection("column"))

    val s = model.classificationOutput.fold(Empty) { co =>
      val curImg = co.head
      val fname  = curImg._1
      div(fStyle)(curImg._2.toList.sortBy(_._2)(Ordering.Float.IeeeOrdering.reverse).map {
        case (label, prob) => span(s"$label, $prob")
      })
    }

    def getModelInfo(m: String, model: Model): ModelInfo =
      model.models.find(_.modelName == m).get

    div(wrapperStyle)(
      if model.authorizationJWT.isEmpty then
        button(bStyle, onClick(Msg.NavigateToUrl(githubPage(model.callbackUri))))("click me!")
      else
        div(iStyle)(
          img(style(CSS.maxHeight("25em")), src := model.image.getOrElse("")),
          form(
            fStyle,
            method  := "post",
            enctype := "multipart/form-data"
          )(
            label("Select a model for inference"),
            select(
              style(CSS.`margin-bottom`("1em")),
              onChange(m => Msg.SetCurrentModel(getModelInfo(m, model)))
            )(
              model.models.map(m => Html.option(m.modelName))
            ),
            input(
              id     := "image-upload",
              name   := "image",
              `type` := "file",
              accept := "image/png, image/jpeg, image/webp",
              onInput(_ => Msg.LoadImage)
            ),
            button(onClick(Msg.UploadImage), disabled(model.image.isEmpty))("Upload")
          ),
          s
        )
    )

  def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None

case class Model(
    authorizationJWT: Option[String],
    image: Option[String],
    callbackUri: String,
    serverUri: String,
    classificationOutput: Option[ClassificationOutput],
    models: List[ModelInfo],
    currentModel: Option[ModelInfo]
)

enum Msg:
  case JumpToHome
  case NavigateToUrl(href: String)
  case SetJWT(token: String)
  case LookForJWT
  case UseJWT(token: String)
  case ConsoleLog(log: String)
  case ConsumeOauthCode(pathWithCode: String)
  case LoadImage
  case UseImage(image: String)
  case UploadImage
  case SetResults(res: Option[ClassificationOutput])
  case GetAvailableModels
  case SetAvailableModels(models: List[ModelInfo])
  case SetCurrentModel(model: ModelInfo)
  case NoOp
  case GetEnvVars

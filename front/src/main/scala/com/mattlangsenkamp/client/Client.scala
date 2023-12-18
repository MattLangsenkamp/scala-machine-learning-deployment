package com.mattlangsenkamp.client

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

import org.scalajs.dom.HTMLImageElement
import tyrian.cmds.File
import org.scalajs.dom
import org.scalajs.dom.html
import org.http4s.UrlForm
import org.http4s.Request
import org.http4s.{Method, EntityBody}
import org.http4s.Entity
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.scalajs.dom.{Fetch, HttpMethod, BodyInit}
import org.scalajs.dom.RequestInit
import org.scalajs.dom.FormData
import scalajs.js.Thenable.Implicits.thenable2future
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportTopLevel("TyrianApp")
object Client extends TyrianApp[Msg, Model]:

  private def consoleLog(msg: String): Cmd[IO, Nothing] =
    Cmd.SideEffect {
      println(msg)
    }

  private val client = FetchClientBuilder[IO]
    .withMode(RequestMode.cors)
    .withCredentials(RequestCredentials.include)
    .create

  def myFetch(): Unit =
    val init = new RequestInit {}
    val body = document.querySelector("form").asInstanceOf[FormData];
    init.method = HttpMethod.POST
    init.body = body
    for
      p <- dom.fetch("", init)
      t <- p.text()
    yield t

    ???

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
    (Model(false, Option.empty), lookForJWT())

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) =
    case Msg.JumpToHome          => (model, Nav.pushUrl("/"))
    case Msg.NavigateToUrl(href) => (model, Nav.loadUrl(href))
    case Msg.LookForJWT          => (model, lookForJWT())
    case Msg.ConsumeOauthCode(pathWithCode) =>
      val code = pathWithCode.replace("/callback?code=", "")
      val io: IO[Msg] =
        client
          .expect[String](f"http://localhost:8080/auth/access_token?code=$code")
          .map(s => Msg.LookForJWT)
          .handleError(_ => Msg.ConsoleLog(f"failed to login with $code"))
      (model, Cmd.Run(io))
    case Msg.NoOp => (model, Cmd.None)
    case Msg.ConsoleLog(log) =>
      (
        model,
        consoleLog(log)
      )
    case Msg.SetLoggedIn(bool) =>
      (model.copy(isLoggedIn = bool), if bool then Cmd.emit(Msg.JumpToHome) else Cmd.None)

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

  val githubPage =
    "https://github.com/login/oauth/authorize?scope=user:email&client_id=1a9ebd723f6ce63aef11"
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

    div(wrapperStyle)(
      if !model.isLoggedIn then button(bStyle, onClick(Msg.NavigateToUrl(githubPage)))("click me!")
      else
        div(iStyle)(
          img(style(CSS.maxHeight("25em")), src := model.image.getOrElse("")),
          form(
            fStyle,
            action  := "https://httpbin.org/post",
            method  := "post",
            enctype := "multipart/form-data"
          )(
            input(
              id     := "image-upload",
              name   := "image",
              `type` := "file",
              accept := "image/png, image/jpeg, image/webp",
              onInput(_ => Msg.LoadImage)
            ),
            button(`type` := "submit", disabled(model.image.isEmpty))("Upload")
          )
        )
    )

  def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None

case class Model(isLoggedIn: Boolean, image: Option[String])

enum Msg:
  case JumpToHome
  case NavigateToUrl(href: String)
  case LookForJWT
  case ConsoleLog(log: String)
  case ConsumeOauthCode(pathWithCode: String)
  case SetLoggedIn(bool: Boolean)
  case LoadImage
  case UseImage(image: String)
  case NoOp

def lookForJWT(key: String = "Authorization"): Cmd[IO, Msg] =
  Cmd.emit {
    val loggedIn = document.cookie
      .split(";")
      .exists(_.split("=")(0) == key)
    if loggedIn then Msg.SetLoggedIn(true)
    else Msg.SetLoggedIn(false)
  }

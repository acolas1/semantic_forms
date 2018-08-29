package controllers

import deductions.runtime.jena.ImplementationSettings
import deductions.runtime.services.html.Form2HTMLObject
import deductions.runtime.user.RegisterPage
import deductions.runtime.utils.DefaultConfiguration
import play.api.mvc.{Action, Controller, Request}
import deductions.runtime.utils.FormModuleBanana

/** Dummy controller for helping in creating new web pages or services */
object DummyController extends Controller
    with ImplementationSettings.RDFCache
    with LanguageManagement
    with RegisterPage[ImplementationSettings.Rdf, ImplementationSettings.DATASET]
    with FormModuleBanana[ImplementationSettings.Rdf] {

  override implicit val config = new DefaultConfiguration {}
  override lazy val htmlGenerator =
    Form2HTMLObject.makeDefaultForm2HTML(config)(ops)

  def page() =
    Action { implicit request: Request[_] =>
      val requestCopy = getRequestCopy()
      val userid = requestCopy.userId()
      val title = "My view"
      val lang = chooseLanguage(request)
      val userInfo = displayUser(userid, "", title, lang)
      // outputMainPage()
      Ok("OK from DummyController")
    }

}
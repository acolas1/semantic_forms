package controllers

import deductions.runtime.services.html.Form2HTMLObject
import deductions.runtime.utils.DefaultConfiguration
import play.api.Play
import deductions.runtime.utils.FormModuleBanana
import deductions.runtime.jena.ImplementationSettings

/** object Application in another file to facilitate redefinition */
object Application extends {
  override implicit val config = new DefaultConfiguration {

    /** CAUTION: after activating this, be sure to to run
     * deductions.runtime.jena.lucene.TextIndexerRDF */
    override val useTextQuery = true

    override val serverPort = {
      val port = Play.current.configuration.
        getString("http.port")
      port match {
        case Some(port) =>
          println( s"Running on port $port")
          port
        case _ =>
          val serverPortFromConfig = super.serverPort
          println(s"Could not get port from Play configuration; retrieving default port from SF config: $serverPortFromConfig")
          serverPortFromConfig
      }
    }
  }
} with Services
  with WebPages
  with SparqlServices
  with FormModuleBanana[ImplementationSettings.Rdf] {
  override lazy val htmlGenerator =
    Form2HTMLObject.makeDefaultForm2HTML(config)(ops)
}

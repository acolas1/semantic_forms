package deductions.runtime.views

import scala.io.Source
import scala.xml.Elem
import scala.xml.Node
import scala.xml.XML

//import controllers.routes
// import deductions.runtime.views.MainXml
import scala.xml.NodeSeq

/** HTML page skeleton */
trait MainXmlWithHead extends MainXml {

  private def add(e: Elem, c: Node): Elem = e.copy(child = e.child ++ c)
  private val basicHead =
    scala.xml.Unparsed(
      // this way, head.html is in forms/ module, removing a dependency in forms_play/ module
      Source.fromURL(getClass.getResource("/deductions/runtime/html/head.html")).getLines().mkString("\n"))

  /** HTML head content */
  override def head(title: String = "")(implicit lang: String = "en"): NodeSeq = {
    val titleTag =
      <title>
        {
          val default = messageI18N("Welcome")
          if (title != "")
            s"$title - $default"
          else
            default
        }
      </title>
    basicHead ++
    titleTag ++
    <meta name="Description" content={title}/>
  }

}

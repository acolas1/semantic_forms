package deductions.runtime.services

import java.net.URLEncoder

import org.w3.banana.RDF

import scala.concurrent.Future
import scala.xml.NodeSeq
import deductions.runtime.core.HTTPrequest

/** Show named graphs */
trait NamedGraphsSPARQL[Rdf <: RDF, DATASET]
    extends ParameterizedSPARQL[Rdf, DATASET] {

  private implicit val searchStringQueryMaker = new SPARQLQueryMaker[Rdf] {
    override def makeQueryString(search: String*): String = {
      // TODO show # of triples
      val filterClause = if( search.size > 0 ) {
        s"  FILTER (CONTAINS(STR(?thing),'${search(0)}'))"
      }
      else ""
      s"""
         |SELECT DISTINCT ?thing 
         |    # ?CLASS
         |    WHERE {
         |  graph ?thing {
         |    [] ?p ?O .
         |    # TODO: lasts very long with this
         |    # OPTIONAL { ?thing a ?CLASS . }
         |  }
         |  $filterClause
         |}""".stripMargin
  }

    /** add columns in response */
    override def columnsForURI(node: Rdf#Node, label: String): NodeSeq = {
//      println("RDFDashboardSPARQL.columnsForURI")
      <a href={
        "/showTriplesInGraph?uri=" +
          URLEncoder.encode(node.toString(), "UTF-8")
      } class="form-value">
        Show Triples in graph
      </a>
    }
  }
  
  def showNamedGraphs(httpRequest: HTTPrequest): Future[NodeSeq] = {
    val lang = httpRequest.getLanguage()
    val patternOption = httpRequest.getHTTPparameterValue("pattern")
    val searchArg = patternOption  match {
      case Some(patt) => Seq(patt)
      case None => Seq()
    }
    search("/showTriplesInGraph?uri=", lang, searchArg,
        httpRequest=httpRequest)
  }

}

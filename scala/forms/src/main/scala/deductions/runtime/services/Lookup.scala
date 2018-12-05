package deductions.runtime.services

import deductions.runtime.abstract_syntax.{InstanceLabelsInferenceMemory, PreferredLanguageLiteral}
import deductions.runtime.sparql_cache.SPARQLHelpers
import deductions.runtime.utils.RDFStoreLocalProvider
import deductions.runtime.utils.RDFPrefixes
import org.w3.banana.RDF
import play.api.libs.json.Json

/**
 * API for a lookup web service similar to dbPedia lookup
 * 
 * TODO common code with StringSearchSPARQL
 */
trait Lookup[Rdf <: RDF, DATASET]
    extends RDFStoreLocalProvider[Rdf, DATASET]
    with InstanceLabelsInferenceMemory[Rdf, DATASET]
    with PreferredLanguageLiteral[Rdf]
    with SPARQLHelpers[Rdf, DATASET]
    with RDFPrefixes[Rdf]
    with StringSearchSPARQLBase[Rdf] {

  type Results = List[(Rdf#Node, String, String, String, String, String)]

  /**
   * This is dbPedia's output format, that is used when `mime` = "application/xml"
   * (otherwise a JSON similar in structure is returned)
   *
   * <ArrayOfResult xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
   * xmlns:xsd="http://www.w3.org/2001/XMLSchema"
   * xmlns="http://lookup.dbpedia.org/">
   *  <Result>
   *    <Label>Jimi Hendrix</Label>
   *    <URI>http://dbpedia.org/resource/Jimi_Hendrix</URI>
   *    <Description> This article is about the guitarist. For the band, see The Jimi Hendrix Experience.</Description>
   *   <Classes>
   *    <Class>
   *     <Label>Person</Label>
   *     <URI>http://xmlns.com/foaf/0.1/Person</URI>
   *    </Class>
   */
  def lookup(search: String, lang: String = "en", clas: String = "", mime: String): String = {
    
    val rawResult = searchStringOrClass(search, clas)
    logger.info(s"lookup(search=$search, clas=<$clas> => ${rawResult.take(5)}")

    makeJSONorXML(rawResult, lang, mime)
//    rawResult.mkString("\n")
  }

  /** make JSON or XML */
  def makeJSONorXML(res: List[Iterable[Rdf#Node]], lang: String, mime: String): String = {
    logger.debug(s"lookup: after searchStringOrClass, starting TRANSACTION for dataset $dataset")
    val transaction = rdfStore.rw( dataset, {
      val urilabels = res.map {
        uris =>
          val uri = uris.head
          val label = makeInstanceLabel(uri, allNamedGraph, lang)
          val desc = instanceDescription(uri, allNamedGraph, lang)
          val img = instanceImage(uri, allNamedGraph, lang)
          val typ = instanceTypeLabel(uri, allNamedGraph, lang)
          val refCount = instanceRefCount(uri, allNamedGraph, lang)
          (uri, label, desc, img, typ, refCount)
      }
      urilabels
    })
    logger.debug(s"lookup: leaved TRANSACTION for dataset $dataset")
    val list = transaction.get

    if (mime.contains("xml"))
      formatXML(list)
    else
      formatJSON(list)
  }

  /** search String Or Class
   * transactional
   */
  private def searchStringOrClass(search: String, clas: String = ""): List[Iterable[Rdf#Node]] = {
    val queryString = indexBasedQuery.makeQueryString(search, clas)
    logger.debug(
        s"""searchStringOrClass(search="$search", clas <$clas>, queryString "$queryString" """)
    val rawResult: List[Seq[Rdf#Node]] = sparqlSelectQueryVariables(queryString, Seq("thing"))
//    val res = sparqlSelectQuery(queryString) ; res . get
    rawResult
  }

  private def formatXML(list: Results): String = {
    val elems = list.map {
      case (uri, label, desc, img, typ, refCount) =>
        <Result>
          <Label>{ label }</Label>
          <URI>{ uri }</URI>
          <Description>{ desc }</Description>
          <Image>{ img }</Image>
          <Type>{ typ }</Type>
          <Refcount>{ refCount }</Refcount>
        </Result>
    }
    val xml =
      <ArrayOfResult>
        { elems }
      </ArrayOfResult>
    xml.toString
  }

  /** The keys are NOT exactly the same as the XML tags :( */
  private def formatJSON(list: Results): String = {
    val list2 = list.map {
      case (uri, label, desc, img, typ, refCount) => Json.obj(
          "label" -> label,
          "uri" -> uri.toString(),
          "description" -> desc,
          "image" -> img,
          "type" -> typ,
          "refCount" -> refCount
          )
    }
    logger.trace(s"list2 $list - $list2")
    val results =  Json.obj( "results" -> list2 )
    Json.prettyPrint(results)
  }

  /**
   * use Lucene
   *  see https://jena.apache.org/documentation/query/text-query.html
   *  TODO output rdf:type also
   */
  private val indexBasedQuery = new SPARQLQueryMaker[Rdf] {
    override def makeQueryString(searchStrings: String*): String = {

      val search = searchStrings(0)
      val clas = if( searchStrings.size > 1 ) {
        logger.debug(
          s"makeQueryString searchStrings $searchStrings , searchStrings(1) = ${searchStrings(1)}")
        searchStrings(1)
      } else ""

//      // UNUSED
//      val queryWithlinksCount_NoPrefetch =
//        queryWithlinksCountNoPrefetch(search, clas)
//
//      // UNUSED
//      val queryWithoutlinks_Count = 
//        queryWithoutlinksCount(search, clas)

      val queryWithlinks_Count =
        queryWithlinksCount(search, clas)
     return queryWithlinks_Count
    }
  }
}
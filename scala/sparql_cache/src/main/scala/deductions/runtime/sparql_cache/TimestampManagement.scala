package deductions.runtime.sparql_cache

import java.io.{File, IOException}
import java.net.{HttpURLConnection, URL}
import java.util.Date

import deductions.runtime.utils.RDFStoreLocalProvider
import org.apache.http.client.utils.DateUtils

import org.w3.banana.RDF

import scala.util.{Success, Try}

import scalaz._
import Scalaz._

/** SF compares the document timestamp from HTTP header e.g. :
  Last-Modified: Fri, 26 Jan 2018 11:12:18 GMT
  to preceeding time in database, also from HTTP header.
  The Last-Modified header can be any time zone,
  but Apache client generates a Date object, necessarily in UTC timezone,
  so SF is putting times to a unique common timezone (UTC) for comparing timestamps.
 */
trait TimestampManagement[Rdf <: RDF, DATASET]
extends RDFStoreLocalProvider[Rdf, DATASET]
    with SPARQLHelpers[Rdf, DATASET]
{

  /** timestamp Graph URI and property */
  val timestampGraphURI = "http://deductions-software.com/timestampGraph"

  import ops._
  import rdfStore.sparqlEngineSyntax._

  //// Expires ////

  def isDocumentExpired(connectionOption: Option[HttpURLConnection]) = {
    val opt = connectionOption.map {
      conn =>
        val expires = getHeaderField("Expires", conn)
        println( s"""isDocumentExpired: Expires: "$expires"""" )
        if( expires === "" )
          false
        else {
          val resulTest = Try {
            val expireDate = DateUtils.parseDate(expires)
            val currentDate = new Date
            currentDate.getTime > expireDate.getTime
          }
          resulTest.getOrElse(false)
        }
    }
    opt.getOrElse(false)
  }

    //// Last-Modified ////

  /**
   * add or replace timestamp for URI in dataset (actually a dedicated Graph timestampGraphURI ),
   *  with transaction
   */
  def addTimestampToDataset(uri: Rdf#URI, dataset: DATASET) = {
    rdfStore.rw( dataset, {
      addTimestampToDatasetNoTransaction(uri, dataset)
    })
  }

  /** replace Timestamp for URI in Dataset
   *  No Transaction */
  def addTimestampToDatasetNoTransaction(uri: Rdf#URI, dataset: DATASET) = {
	  val time = lastModified(fromUri(uri), 1000)
	  println("addTimestampToDatasetNoTransaction: " + time._2 )
    replaceRDFTriple(
      makeTriple(
        uri,
        makeUri(timestampGraphURI),
        makeLiteral(time._2.toString, xsd.integer)),
      URI(timestampGraphURI),
      dataset)
  }

    /**
   * get timestamp from dataset (actually a dedicated Graph timestampGraphURI ),
   *  No Transaction
   */
  def getTimestampFromDataset(uri: Rdf#URI, dataset: DATASET): Try[Long] = {
    println(s"getTimestampFromDataset: uri: $uri")
    import java.math.BigInteger
    val queryString = s"""
         |SELECT DISTINCT ?ts WHERE {
         |  graph <$timestampGraphURI> {
         |    <$uri> <$timestampGraphURI> ?ts .
         |  }
         |}""".stripMargin
    val result = for {
      query <- sparqlOps.parseSelect(queryString)
      solutions <- dataset.executeSelect(query, Map())
    } yield {
      solutions.toIterable.map {
        row =>
          {
            val node = row("ts").get
            val r1 = foldNode(node)(
              _ => Success(new BigInteger("0")),
              _ => Success(new BigInteger("0")),
              lit => lit.as[BigInteger])
            r1.get
            // getOrElse sys.error("getTimestampFromDataset: " + row)
          }
      }
    }
    println(s"getTimestampFromDataset: result: $result")
    result.map { x => x.next.longValue() }
  }
  
  /** get lastModified HTTP header
   * @return tuple:
   * _1 : true <=> no error
   * _2 : timestamp from HTTP HEAD request or local file
   * _3 : Option[HttpURLConnection] ;
   * return Long.MaxValue if no timestamp is available;
   *  NOTE elsewhere akka HTTP client is used
   */
  def lastModified(url0: String, timeout: Int): (Boolean, Long, Option[HttpURLConnection]) = {
    val url = url0.replaceFirst("https", "http"); // Otherwise an exception may be thrown on invalid SSL certificates.
    try {
      val connection0 = new URL(url).openConnection()
      connection0 match {
        case connection: HttpURLConnection if( ! url.startsWith("file:/") ) =>
          println(s"lastModified: HTTP URL")
          connection.setConnectTimeout(timeout)
          connection.setReadTimeout(timeout)
          connection.setRequestMethod("HEAD")
          val responseCode = connection.getResponseCode()

          def tryHeaderField(headerName: String): (Boolean, Boolean, Long) = {
            val dateString = getHeaderField( headerName, connection)
            if (dateString != "") {
              // from Apache http client - Date objects are in coordinated universal time (UTC)
              val dateFromHTTPHeader: java.util.Date = DateUtils.parseDate(dateString)
              println("TimestampManagement.lastModified(): responseCode: " + responseCode +
                ", date: " + dateFromHTTPHeader + ", dateString " + dateString)
              (true, 200 <= responseCode && responseCode <= 399, dateFromHTTPHeader.getTime())
            } else (false, false, Long.MaxValue)
          }
          
          val lm = tryHeaderField("Last-Modified")
          val r = if (lm._1) {
            (lm._2, lm._3, Some(connection) )
          } else (false, Long.MaxValue, Some(connection) )
          return r

        case _ if(url.startsWith("file:/") ) =>
          val f = new File( new java.net.URI(url) )
          println(s"lastModified: file:// ${new java.util.Date(f.lastModified)}")
          (true,  f.lastModified(), None )
          
        case _ =>
          println( s"lastModified(): Case not implemented: $url - $connection0")
          (false, Long.MaxValue, None )

      }
    } catch {
      case exception: IOException =>
        println(s"lastModified($url0")
//        logger.warn("")
        (false, Long.MaxValue, None)
      case e: Throwable           => throw e
    }
  }

  private [sparql_cache] def getHeaderField(headerName: String, connection: HttpURLConnection):
  String = {
    val headerString = connection.getHeaderField(headerName)
    if (headerString != null) {
      println("TimestampManagement.tryHeaderField: " +
        s", header: $headerName = " + headerString +
        "; url: " + connection.getURL )
        headerString
    } else ""
  }

  //// ETag ////
  
  /** No Transaction */
  def getETagFromDataset(uri: Rdf#URI, dataset: DATASET): String = {
	  val queryString = s"""
         |SELECT DISTINCT ?etag WHERE {
         |  GRAPH <$timestampGraphURI> {
         |    <$uri> <ETag> ?etag .
         |  }
         |}""".stripMargin
    val list = sparqlSelectQueryVariablesNT(queryString, Seq("etag"), ds=dataset )
    val v = list.headOption.getOrElse(Seq())
    val vv = v.headOption.getOrElse(Literal(""))
    nodeToString(vv)
  }

  /** No Transaction */
  def addETagToDatasetNoTransaction(uri: Rdf#URI, etag: String, dataset: DATASET) = {
    replaceRDFTriple(
      makeTriple(
        uri,
        makeUri("ETag"),
        makeLiteral( etag, xsd.string )),
      URI(timestampGraphURI),
      dataset)
  }

}

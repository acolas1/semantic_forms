package deductions.runtime.services

import scala.util.{ Failure, Success, Try }

import deductions.runtime.core.HTTPrequest
import deductions.runtime.sparql_cache.RDFCacheDependencies

import org.w3.banana.io.RDFReader
import org.w3.banana.io.RDFWriter
import org.w3.banana.RDF

trait RDFContentNegociationIO[Rdf <: RDF, DATASET]
    extends RDFContentNegociation
    with RDFCacheDependencies[Rdf, DATASET] {

  /** get RDF Reader From MIME */
  def getRDFReader(request: HTTPrequest): RDFReader[Rdf, Try, _] = {
    val mimeType = request.getHTTPheaderValue("Content-Type").getOrElse("")
    getReaderFromMIME(mimeType)
  }

  def getReaderFromMIME(mimeType: String): RDFReader[Rdf, Try, _] = {
    println( s">>>> getRDFReader: mimeType $mimeType")
    val rdfReader: RDFReader[Rdf, Try, Object] =
      foldRdfSyntax(mimeType)(
        _ => rdfXMLReader,
        _ => turtleReader,
        _ => jsonldReader,
        _ => turtleReader // N3 actually (N3 exists in Jena but not Banana)
        ) . _1
      rdfReader
  }

  def getReaderFromURI(uri: String): Option[RDFReader[Rdf, Try, _]] =
    for( mime <- getMimeFromURI(uri) ) yield getReaderFromMIME(mime)

  /** get RDF Writer From MIME */
  def getRDFWriter(request: HTTPrequest): RDFWriter[Rdf, Try, _] = {
		val mimeType = request.getHTTPheaderValue("Accept").getOrElse("")
		getWriterFromMIME(mimeType)
  }

  /** get RDF Writer From MIME */
  def getWriterFromMIME(accept: String): RDFWriter[Rdf, Try, _] =
    foldRdfSyntax(accept)(
        _ => rdfXMLWriter,
        _ => turtleWriter,
        _ => jsonldCompactedWriter,
        _ => turtleWriter
    ) . _1
}
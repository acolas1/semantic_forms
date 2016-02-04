package deductions.runtime.jena

import scala.collection.JavaConversions.asScalaIterator
import org.apache.log4j.Logger
import org.w3.banana.jena.Jena
import org.w3.banana.jena.JenaModule
import com.hp.hpl.jena.tdb.TDBFactory
import deductions.runtime.dataset.RDFStoreLocalProvider
import org.w3.banana.jena.JenaDatasetStore
import org.w3.banana._
import org.w3.banana.diesel._
import deductions.runtime.utils.Timer

import org.apache.jena.query.text.TextDatasetFactory
import org.apache.solr.client.solrj.impl.HttpClientUtil
import org.apache.jena.query.text.EntityDefinition
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.impl.HttpSolrServer
import deductions.runtime.services.Configuration
import deductions.runtime.services.DefaultConfiguration
import org.apache.lucene.store.NIOFSDirectory
import java.io.File
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.util.Version

// TODO rename RDFStoreLocalJenaProvider

/** singleton for implementation settings */
object ImplementationSettings {
  // pave the way for migration to Jena 3 ( or BlazeGraph )
  type DATASET = com.hp.hpl.jena.query.Dataset
  type Rdf = Jena
}

/** For user data and RDF cache, sets a default location for the Jena TDB store directory : TDB */
trait RDFStoreLocalJena1Provider extends RDFStoreLocalJenaProvider

trait RDFStoreLocalJenaProvider
    extends RDFStoreLocalProvider[
      ImplementationSettings.Rdf,
      ImplementationSettings.DATASET]
    with JenaModule with JenaRDFLoader
    with Timer
    with DefaultConfiguration {
  import ops._
  type DATASET = ImplementationSettings.DATASET
  override val rdfStore = new JenaDatasetStore(false)
  import rdfStore.graphStoreSyntax._

  override def createDatabase(database_location: String) = {
    val dts = TDBFactory.createDataset(database_location)
    Logger.getRootLogger.info(s"RDFStoreLocalJena1Provider $database_location, dataset created: $dts")

    try {
      if (useTextQuery) {
        val rdfs = RDFSPrefix[Rdf]
        val entDef = new EntityDefinition("uri", "text", rdfs.label)
        val entMap = entDef
        if (solrIndexing) {
          val server: SolrServer = new HttpSolrServer("http://localhost:7983/new_core")
          //      val pingResult = server.ping
          //      println("pingResult.getStatus " + pingResult.getStatus) // 7983
          TextDatasetFactory.createSolrIndex(dts, server, entMap)
        } else {
          val directory = new NIOFSDirectory(new File("LUCENE"))
          TextDatasetFactory.createLucene(dts, directory, entMap,
            new StandardAnalyzer(Version.LUCENE_46))
        }
      }
    } catch {
      case t: Throwable =>
        println(t.getLocalizedMessage)
        println(t.getCause)
    }
    dts
  }

  /**
   * NOTES:
   *  - no need of a transaction here, as getting Union Graph is anyway part of a transaction
   *  - Union Graph in Jena should be re-done for each use (not 100% sure, but safer anyway)
   */
  override def allNamedGraph: Rdf#Graph = {
    time(s"allNamedGraph dataset $dataset", {
      //      println(s"Union Graph: entering for $dataset")

      // NOTE: very important to use the properly configured rdfStore (with defensiveCopy=false)
      val ang = rdfStore.getGraph(dataset, makeUri("urn:x-arq:UnionGraph")).get
      //      println(s"Union Graph: hashCode ${ang.hashCode()} : size ${ang.size}")
      ang
    })
    //    union(dataset.getDefaultModel.getGraph :: unionGraph :: Nil)
  }

  /** List the names of graphs */
  def listNames(ds: DATASET): Iterator[String] = ds.listNames()

}

/** TODO implement independently of Jena */
trait RDFGraphPrinter extends RDFStoreLocalJena1Provider {
  import rdfStore.transactorSyntax._
  def printGraphList {
    dataset.r({
      val lgn = dataset.asDatasetGraph().listGraphNodes()
      Logger.getRootLogger().info(s"listGraphNodes size ${lgn.size}")
      for (gn <- lgn) {
        Logger.getRootLogger().info(s"${gn.toString()}")
      }
      Logger.getRootLogger().info(s"afer listGraphNodes")
    })
  }
}

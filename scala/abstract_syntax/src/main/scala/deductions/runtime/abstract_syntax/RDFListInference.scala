package deductions.runtime.abstract_syntax

import deductions.runtime.core.{FormModule, Textarea, WidgetType}
import deductions.runtime.utils.RDFStoreLocalProvider
import org.w3.banana.RDF

/**
 * populate Fields in form by inferencing from RDF Lists
 */
trait RDFListInference[Rdf <: RDF, DATASET]
    extends RDFStoreLocalProvider[Rdf, DATASET]
    with FormModule[Rdf#Node, Rdf#URI]
    with PreferredLanguageLiteral[Rdf]
    with InstanceLabelsInference2[Rdf] {

  def makeRDFListEntry(
    label: String, comment: String,
    property: ObjectProperty = nullURI,
    value: Rdf#Node = nullURI,
    alreadyInDatabase: Boolean = true,
    possibleValues: Seq[(Rdf#Node, Rdf#Node)] = Seq(),
    valueLabel: String = "",
    type_ : Rdf#Node = nullURI,
    inverseTriple: Boolean = false,
    subject: Rdf#Node = nullURI,
    subjectLabel: String = "",
    mandatory: Boolean = false,
    openChoice: Boolean = true,
    widgetType: WidgetType = Textarea): Option[RDFListEntry] = {

    val graph = allNamedGraph
    val nodesListForRDFList = nodeSeqToURISeq(rdfListToSeq(Some(value))(allNamedGraph))
    logger.debug(s">>>> makeRDFListEntry for property <$property>, list $nodesListForRDFList")
    val entriesListForRDFList: Seq[Entry] = nodesListForRDFList . map {
      node => ops.foldNode(node)(
          uri => ResourceEntry(value=uri, valueLabel=makeInstanceLabel(node, graph, "en")),
          bn => BlankNodeEntry(value=bn, valueLabel=makeInstanceLabel(node, graph, "en")),
          lit => LiteralEntry(value=lit)
      )
    }
    val list = FormSyntax(nullURI, entriesListForRDFList)

    nodesListForRDFList match {
      case l if !l.isEmpty => Some(
          RDFListEntry(
        label, comment,
        property: ObjectProperty,
        value: Rdf#Node,
        alreadyInDatabase: Boolean,
        possibleValues: Seq[(Rdf#Node, Rdf#Node)],
        valueLabel: String,
        Seq(type_),
        inverseTriple: Boolean,
        subject: Rdf#Node,
        subjectLabel: String,
        mandatory: Boolean,
        openChoice: Boolean,
        widgetType: WidgetType,
        values = list))
      case _ => None
    }
  }
}
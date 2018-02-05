package deductions.runtime.html

import deductions.runtime.utils.RDFPrefixesInterface
import org.joda.time.DateTime

import scala.xml.{NodeSeq, Unparsed}
import deductions.runtime.core.HTTPrequest
import java.net.URLEncoder
import deductions.runtime.utils.I18NMessages

/** generate HTML from abstract Form for Display (Read only) */
trait Form2HTMLDisplay[NODE, URI <: NODE]
  extends Form2HTMLBase[NODE, URI]
  with RDFPrefixesInterface {

  import config._


  private[html] def createHTMLiteralReadonlyField(
      l: formMod#LiteralEntry,
      request: HTTPrequest = HTTPrequest()): NodeSeq =
    <xml:group>
      {
        val valueDisplayed =
          if (l.type_.toString().endsWith("dateTime"))
            l.valueLabel.replaceFirst("T00:00:00$", "")
          else
            l.valueLabel
        Unparsed(valueDisplayed)
      }
    	{makeUserInfoOnTriples(l, request.getLanguage())}
      <div>{ if (l.lang != "" && l.lang != "No_language") " > " + l.lang }</div>
    </xml:group>

  /** create HTML Resource Readonly Field
    * PENDING if inner val should need to be overridden, they should be directly in trait
    *  */
  def createHTMLResourceReadonlyField(
                                       resourceEntry: formMod#ResourceEntry,
                                       hrefPrefix: String = hrefDisplayPrefix,
                                       request: HTTPrequest = HTTPrequest()
                                     ): NodeSeq = {

    import resourceEntry._

    val objectURIstringValue = value.toString()
    val css = cssForURI(objectURIstringValue)

    val typ = firstNODEOrElseEmptyString(type_)
//  println(s"==== createHTMLResourceReadonlyField: typ: $typ")

    val widgets =
      hyperlinkToField(resourceEntry) ++
      hyperlinkToURI(hrefDisplayPrefix, objectURIstringValue, valueLabel,
          typ, // TODO pass type_
          resourceEntry) ++
      backLinkButton (resourceEntry) ++
        showHideHTMLOnclick(
          normalNavigationButton(resourceEntry) ++
            makeDrawGraphLink(objectURIstringValue) ++
            displayThumbnail(resourceEntry) ++
            makeUserInfoOnTriples(resourceEntry, request.getLanguage()) ++
            creationButton(
              objectURIstringValue,
              type_.map { t => t.toString() },
              request.getLanguage()) ++
              makeClassTableButton(resourceEntry) ++
              hyperlinkForEditingURIinsideForm(objectURIstringValue, request.getLanguage()),
              resourceEntry)

      <span class="sf-statistics">{widgets}</span>
  }

  private def showHideHTMLOnclick(html: NodeSeq, resourceEntry: formMod#ResourceEntry): NodeSeq = {
    val resourceId = resourceEntry.value.toString()
    val wrapperId = resourceId+"-wrap"
    val buttonId = resourceEntry.value.toString()+"-button"
    <button id={buttonId}>...</button> ++
    <span id={wrapperId} style="display: none">{
      // TODO this script should not be repeated at every field in the form !
      <script>{
        Unparsed(s"""
          var button = document.getElementById("$buttonId")
          button . addEventListener("click", function(event){
            var button = event.target
            var wrapper = button . nextSibling
            wrapper .style.removeProperty("display")
            window.console.log("OK removed Property display on " + "$wrapperId" )
          })
          """)
      }</script> ++
    html
    }</span>
  }
  /** hyperlink To RDF property */
  private def hyperlinkToField(resourceEntry: formMod#ResourceEntry
//      , objectURIstringValue: String
      ) = {
    val id = urlEncode(resourceEntry.property).replace("%", "-")
    /*  ID and NAME tokens must begin with a letter ([A-Za-z]) and may be followed by any number of letters,
     *  digits ([0-9]), hyphens ("-"), underscores ("_"), colons (":"), and periods ("."). */

    val objectURIstringValue = resourceEntry.value.toString()
    if( objectURIstringValue != "" && id != "") {
      <a href={ "#" + id } draggable="true">
        <i class="glyphicon glyphicon-link"></i>
      </a>
        <a id={ id }></a>
    } else NodeSeq.Empty
  }

  private[html] def hyperlinkToURI(hrefPrefix: String, objectURIstringValue: String, valueLabel: String,
      type_ : String, resourceEntry: formMod#ResourceEntry) = {
    val types0 = resourceEntry.type_.mkString(", ")
    val types = if(types0 == "") type_ else types0
    addTripleAttributesToXMLElement(
      <a href={createHyperlinkString(hrefPrefix, objectURIstringValue)} class={cssForURI(objectURIstringValue)} title=
      {s"""Value ${if (objectURIstringValue != valueLabel) objectURIstringValue else ""}
              of type(s) ${types}"""} draggable="true">
        {valueLabel}
      </a>,
      resourceEntry)
  }

  private def backLinkButton(resourceEntry: formMod#ResourceEntry) = {
    import resourceEntry._
    val objectURIstringValue = resourceEntry.value.toString()
    (if (objectURIstringValue.size > 0 && showExpertButtons) {
      val title = s""" Reverse links reaching "$valueLabel" <$value> """
      makeBackLinkButton(objectURIstringValue, title = title)
    } else NodeSeq.Empty)
  }

  private def normalNavigationButton(resourceEntry: formMod#ResourceEntry) = {
    val objectURIstringValue = resourceEntry.value.toString()
    (if (objectURIstringValue.size > 0 &&
        isDownloadableURL(objectURIstringValue) && showExpertButtons) {
    	val value = resourceEntry.value
      <a class="btn btn-primary btn-xs" href={objectURIstringValue} title={s"Normal HTTP link to $value"}
         draggable="true">
        <i class="glyphicon glyphicon-share-alt"></i>
      </a>
    } else NodeSeq.Empty)
  }

  private def displayThumbnail(resourceEntry: formMod#ResourceEntry): NodeSeq = {
    import resourceEntry._
    val imageURL = if (isImage) Some(value)
    else thumbnail
    if (isImage || thumbnail.isDefined) {
      <a class="image-popup-vertical-fit" href={ imageURL.get.toString() } title={ s"Image of $valueLabel: ${value.toString()}" }>
        <img src={ imageURL.get.toString() } css="sf-thumbnail" height="40" alt={ s"Image of $valueLabel: ${value.toString()}" }/>
      </a>
    } else NodeSeq.Empty
  }

  private[html] def createHTMLBlankNodeReadonlyField(
                                                      r: formMod#BlankNodeEntry,
                                                      hrefPrefix: String = config.hrefDisplayPrefix) =
    <a href={ Form2HTML.createHyperlinkString(hrefPrefix, r.value.toString, true) }>{
      r.valueLabel
      }</a> ++
      {makeUserInfoOnTriples(r)}

  private def creationButton(objectURIstringValue: String, types: Seq[String], lang: String): NodeSeq = {
    val imageURL = "/assets/images/create-instance.svg"
    def messageI18N(key: String) = I18NMessages.get(key, lang)
    val mess = messageI18N("Create_instance_of") + s" <$objectURIstringValue>"
    if ( types.exists { t => t.endsWith("#Class") } ) {
//      println(s"==== creationButton: typ: $typ")
        <a href={
          "/create?uri=" + URLEncoder.encode(objectURIstringValue, "UTF-8")
        } title={ mess }>
          <img src={ imageURL } css="sf-thumbnail" height="40" alt={ mess }/>
        </a>
    } else NodeSeq.Empty
  }

  private def makeClassTableButton(resourceEntry: FormEntry // ResourceEntry
  ): NodeSeq = {
    val classURI = toPlainString(resourceEntry.value)
    val triple =
      if (resourceEntry.isClass) {
        s"?S a <$classURI> ."
      } else
        s"?S ?P1 <$classURI> ."

    val sparlqlQuery = s"""
      CONSTRUCT {
        ?S ?P ?O .
      } WHERE {
        GRAPH ?G {
        ?S ?P ?O .
        $triple
      } }
      """
    <a href={ "/table?query=" + URLEncoder.encode(sparlqlQuery, "UTF-8") } target="_blank">TABLE</a>
  }

}

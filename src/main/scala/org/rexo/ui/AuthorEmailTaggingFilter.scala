package org.rexo.ui

import scala.xml.{Node,NodeSeq,XML,Elem,Attribute,Text,Null}
import org.jdom.Document
import java.io
import scala.collection.mutable.Stack
import org.rexo.pipeline.components.RxDocument
import org.slf4j.{Logger, LoggerFactory}

class Email(email: String, refid: Int, metatag: String) {
  val id = refid
  val tag = metatag

  def this(email: String, refid: Int) {
    this(email, refid, "")
  }

  override def toString: String = {
    email
  }

  def ==(emailStr: String): Boolean = {
    !Email.isValid(emailStr) && emailStr == email
  }

  def userMatches (username : String) : Boolean = {
    val parts = email split "@"
    val user = parts(0) replaceAll("[^\\x41-\\x5a | ^\\x61-\\x7a]", "")
    username.toLowerCase == user.toLowerCase
  }

  def getDomain: String = {
    val domain = (email split "@")(1)
    domain
  }
}

object Email {
  val logger = LoggerFactory.getLogger(Email.getClass)
  private val username_regex = """^[-a-z0-9!#$%&'*+/=?^_`{|}~]+(\.[-a-z0-9!#$%&'*+/=?^_`{|}~]+)*"""
  private val email_regex = (username_regex + """@(([a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?)\.+)+([a-z])+$""").r

  // Returns an Either object - String if Left (error), Email Object if Right
  def getEmailObj(email: String, id: Int): Either[String, Email] = {
    if (isValid(email))
      Right(new Email(email, id))
    else
      Left(s"Invalid Email address: $email")
  }

  def isValid(email: String): Boolean = email_regex.findFirstIn(email) != None

  def extractEmails(emailStr: String, refid: Int): Either[String, List[Email]] = {
    val id = refid
    val parts = emailStr split "@"
    if (parts.length >= 2) {

      // This is a first pass through the email address to separate out the
      // users, if there is more then one user in an email address.
      // Further down we will verify that the username is of the appropriate content (see isValid())
      val caseOne = """^f([^,]+)(,[^,]+)+?g$""".r         /* ffred,bob,jessg */
      val caseTwo = """^[^f]([^,]+)(,[^,])+[^g]$""".r     /* fred,bob,jess */
      val caseThree = """^([^,][^|]+)(|[^,][^|]+)+$""".r  /* fred|bob|jess */
      //val caseFour = ("""^(""" + username_regex + """)$""").r /* jess */

      val names = parts(0)  // maybe be just one name, or more then one

      val users = names match {
        case caseOne( _* ) =>
          names.drop(1).dropRight(1).split(",").toList
        case caseTwo( _* ) =>
          names.split(",").toList
        case caseThree( _* ) =>
          names.split('|').toList
        case _ => List(names)
        //case caseFour( _* ) =>
        // List(names) // do nothing
      }

      val domain = parts(1)

      def newEmail(usr: String, dom: String): Either[String, Email] = {
        val str = usr + "@" + dom
        Email.getEmailObj(str, id) match {
          case Left(s) => Left("Email creation failed:" + s)
          case Right(e) => Right(e)
        }
      }
      val results: List[Either[String, Email]] = (users.map(usr => newEmail(usr.trim, domain.trim))).toList
      // right now, I'm not doing anything with the error list -- this may or may not be a bad thing
      val error: List[String] = (results.collect({ case Left(s) => s})).toList
      if (error.length > 0) 
		  logger.info("getEmailObj errors:" + error.map(_.toString))
      val emailList: List[Email] = (results.collect({ case Right(e) => e})).toList
      if (emailList.length == 0) {
        Left("Found zero real email addresses")
      } else {
        Right(emailList)
      }
    } else Left("Bad email string. Unable to extract email address(es) from it. ")
  }

  def emailPossibilities(author: Author) : List[String] = {
    // ie: First Middle Last ; for hyphen matching: Fi-rst Last
    // The code that does the matching will ignore any '.' in the incoming email
    // address, so no need to list those here as well.
    List[String](
      /* last */ author.name_last,
      /* first */ author.name_first, /* in the case of a hyphenated name, this will be unique */
      /* fmlast */ (((author.name_first split "-") map (_.head)).mkString + author.name_last), // assumes only one hyphen
      /* lastfm */ (author.name_last + ((author.name_first split "-") map (_.head)).mkString), // assumes only one hyphen
      /* first */ (author.name_first split "-").mkString, /* TODO this is a dup, if name has no hyphen */
      /* flast */ (author.name_first.head + author.name_last),
      /* lastf */ (author.name_last + author.name_first.head),
      /* firstlast */ (author.name_first + author.name_last),
      /* lastfirst */ (author.name_last + author.name_first)
    )
  }
}

class Institution(instName: String, refid: Int) {
  val id : Int = refid
  val name : String = instName
  var address : Option[String] = None
  var note : Option[Note] = None

  def addNote(newNote : Note) {
    note = new Some(newNote)
  }

  def addAddress(newAddr: String) {
    address = new Some(newAddr)
  }

  def toXML() : NodeSeq = {
    <institution-name>
      {name}
    </institution-name>
      <institution-address>
        {address.getOrElse("")}
      </institution-address>
  }
}

object Institution {
  private val map = scala.collection.mutable.Map[String,(String,String)]()
  private var filename : Option[String] = None

  def toXML(instOpt : Option[Institution]) : NodeSeq = {
    instOpt.map(_.toXML).getOrElse(NodeSeq.Empty)
  }

  def readInstitutionDictionary (instFilename : String)  {
    if (instFilename == "")  return
    filename = Some(instFilename)
    val instData = scala.io.Source.fromFile(instFilename).mkString

    val lines = instData split "\n"
    for(line <- lines) {
      val pair = line split ";;" // split on domain, name, address
      map += pair(0).trim -> (pair(1).trim, pair(2).trim)
    }
  }

  def lookupInstitution(key: String): Option[(String,String)] = {
    // passed in key might be a subdomain of the actual key, hence
    // the key find here.
    // This might not be specific enough, if a institution is linked to several sub-domains.
    // But start here for now.

    def instMatch (mapKey : String) : Boolean =  {
      key.r.findFirstIn(mapKey).nonEmpty || mapKey.r.findFirstIn(key).nonEmpty
    }

    try {
      val theKey : String = (map.keys.find((keyVal : String)=> instMatch(keyVal))).getOrElse("")
      if (theKey != "")
        Some(map(theKey))
      else {
        None
      }
    } catch {
      case e: NoSuchElementException => None
    }
  }
}

class Note(noteXML: NodeSeq) {
  val note: String = noteXML.text
  val attributes = Map("llx" -> (noteXML \ "@llx").text,
    "lly" -> (noteXML \ "@lly").text,
    "urx" -> (noteXML \ "@urx").text,
    "ury" -> (noteXML \ "@ury").text)

  def ==(noteObj: Note): Boolean = {
    noteObj.note == note
  }

  override def toString : String = {
    note
  }
}

object cleaner
{
  // TODO - this sort of thing should probably be done in PDF2Meta...
  // consider foreign characters in this string (umlaut, etc)
  def cleanName(name: String) : String = {
    val nameRe = """([a-zA-Z-]+)""".r
    nameRe.findFirstIn(name).getOrElse("")
  }
}

class Author (xmlseq: NodeSeq) {
  val id = (xmlseq \ "@id").text.toInt
  val name_first = cleaner.cleanName((xmlseq \ "author-first").text)
  val name_middle = cleaner.cleanName((xmlseq \ "author-middle").text)
  val name_last = cleaner.cleanName((xmlseq \ "author-last").text)
  var note : Option[Note] =  Some (new Note(xmlseq \ "note"))
  val attributes = Map(
    "pagenum"->(xmlseq \ "@pageNum").text,
    "llx"->(xmlseq \ "@llx").text,
    "lly"->(xmlseq \ "@lly").text,
    "urx"->(xmlseq \ "@urx").text,
    "ury"->(xmlseq \ "@ury").text)

  var email: Option[Email] = None
  var institution: Option[Institution] = None

  override def toString() : String =  {
    val attrs = (for ((attr, value) <- attributes ) yield { s"$attr: $value"}).mkString("\n")

    s"""|
       |Author Name: $name_first $name_last
       |Author ID: $id
       |PDF Attributes:  \n$attrs
       """.stripMargin
  }

  def getNote() : Option[Note] = { note }

  def addEmail(email : Email) { this.email = new Some(email)}
  def addInstitution(inst : Institution) { this.institution = new Some(inst)}

  def toXML() : Elem = {
    val instID = institution.map(f => f.id).getOrElse(-1)
    val emailID = email.map(f => f.id).getOrElse(-1)
    val instxml =
      <author-inst>
        {Institution.toXML(institution)}
      </author-inst>

    val emailxml =
      <author-email>
        {email.getOrElse(None).toString}
      </author-email>

    val node =
      <authorsummary>
        <author-first>{name_first}</author-first>
        <author-middle>{name_middle}</author-middle>
        <author-last>{name_last}</author-last>
        {if (institution != None) instxml % Attribute(None, "refid", Text(instID.toString), Null)}
        {if (email != None) emailxml % Attribute(None, "refid", Text(emailID.toString), Null)}
      </authorsummary>

    node

  }
}


object AuthorEmailTaggingFilter {
  val logger = LoggerFactory.getLogger(AuthorEmailTaggingFilter.getClass()) // hmm... not sure this is correct
  val metrics = new Metrics("AuthorEmailTaggingFilter")

  //def main(args: Array[String]) {
   // new AuthorEmailTaggingFilter().run(args)
 // }


  def usage() {
    println("Usage: authoremailtaggerfilter -d dict_filename -i filename")
  }

  // XMLPreProcess adds an id attribute to the author, email, institution xml tags.
  // For email tag: it will also split out the email addresses and put them into an attribute on
  // the email tag
  def XMLPreProcess(node : Node) : Node = {
    var authorID = -1
    var emailID = -1
    var instID = -1

    def updateNodes(ns: Seq[Node], mayChange: Boolean): Seq[Node] = {
      for (subnode <- ns) yield {

        subnode match {

          case Elem(prefix, "author", metadata, scope, children@_*) if mayChange =>
            authorID += 1
            val meta = metadata.append(Attribute(None, "id", Text(authorID.toString), Null))
            Elem(prefix, subnode.label, meta, scope, updateNodes(children, mayChange): _*)

          case Elem(prefix, "email", metadata, scope, children@_*) if mayChange =>
            emailID += 1

            var meta = metadata

            Email.extractEmails(subnode.text, emailID) match {
              case Left(e) => logger.warn("email extraction failed for: " + subnode.text)
                List[scala.xml.MetaData]()
              case Right(e) =>
                for ((el, index) <- e.zipWithIndex) yield {
                  meta = meta.append(Attribute(None, s"email$index", Text(el.toString), Null))
                }
            }
            meta = meta.append(Attribute(None, "id", Text(emailID.toString), Null))
            Elem(prefix, subnode.label, meta, scope, updateNodes(children, mayChange): _*)

          case Elem(prefix, "institution", metadata, scope, children@_*) if mayChange =>
            instID += 1
            val meta = metadata.append(Attribute(None, "id", Text(instID.toString), Null))
            Elem(prefix, subnode.label, meta, scope, updateNodes(children, mayChange): _*)

          // I'm only interested in tagging things in the header right now, so let's not
          // touch anything else
          case Elem(prefix, "headers", metadata, scope, children@_*) =>
            Elem(prefix, "headers", metadata, scope, updateNodes(children, true): _*)

          // catch all for every other element - things outside of the header element
          // cannot change (ie, author in another section will not get an ID
          case Elem(prefix, label, metadata, scope, children@_*) =>
            Elem(prefix, label, metadata, scope, updateNodes(children, mayChange): _*)

          // preserve text
          case other => other
        }
      }
    }
    updateNodes(node.theSeq, false)(0)

  }

  def XMLPostProcess(node : Node, authorList : List[Author]) : Node = {

    def updateNodes(ns: Seq[Node], mayChange: Boolean): Seq[Node] = {

      for (subnode <- ns) yield {
        subnode match {

          case Elem(prefix, "author", metadata, scope, children@_*) if mayChange =>
            var meta = metadata;
            val author = authorList.filter(
              x => {
                x.id == metadata.get("id").get.text.toInt
              })(0)

            if (author.email != None) {
              meta = meta.append(Attribute(None, "email", Text(author.email.get.id + "-" + author.email.get.tag), Null))
            }

            if (author.institution != None) {
              meta = meta.append(Attribute(None, "institution", Text(author.institution.get.id.toString), Null))
            }

            Elem(prefix, subnode.label, meta, scope, updateNodes(children, mayChange): _*)

          // I'm only interested in adding things to the header right now, so let's not
          // touch anything else
          case Elem(prefix, "headers", metadata, scope, children@_*) =>
            Elem(prefix, "headers", metadata, scope, updateNodes(children, true): _*)

          // catch all for every other element - things outside of the header element
          // will not change
          case Elem(prefix, label, metadata, scope, children@_*) =>
            Elem(prefix, label, metadata, scope, updateNodes(children, mayChange): _*)

          // preserve text
          case other =>  other
        }
      }
    }
    updateNodes(node.theSeq, false)(0)
  }
}

class AuthorEmailTaggingFilter extends ScalaPipelineComponent {
  val logger = LoggerFactory.getLogger(AuthorEmailTaggingFilter.getClass())

  override def apply(xmldata: Node): Node = {
    AuthorEmailTaggingFilter.metrics.logStart("AuthorEmailTaggingFilter")
    //val xmldata = XML.loadString(doc.toString)

    val newXML = run_filter(xmldata)

    newXML
  }
/*
  def run(args: Array[String]) {

    AuthorEmailTaggingFilter.metrics.logStart("AuthorEmailTaggingFilter")

    val argMap = ParseArgs.parseArgs("AuthorEmailTaggingFilter", args, AuthorEmailTaggingFilter.usage)
    // need exception handling here!!
    val infile = argMap("-i")
    var dictFile = ""

    logger.info("Current directory is: " + (new java.io.File(".")).getCanonicalPath)

    try {
      dictFile = argMap("-d") // may not be set
    } catch {
      case e: NoSuchElementException =>
    }

    if (dictFile != "")
      Institution.readInstitutionDictionary(dictFile)

    val newXML = run_filter(XML.loadFile(infile))
    XML.save((infile split ".xml")(0) + ".summary.xml", newXML, "UTF-8", true)
  }
*/
  def run_filter(xmldata : Node) : Node = {

    val refXML = AuthorEmailTaggingFilter.XMLPreProcess(xmldata)
    val headerXML = refXML \ "content" \ "headers"

    var authorList: List[Author] = List()
    var emailList: List[Email] = List()
    var instList: List[Institution] = List()

    AuthorEmailTaggingFilter.metrics.logStart("Parsing Header (Method B)")

    /* maybe pay attention to notes in the future. Currently they are not useful */
    authorList = getAuthors(headerXML)
    emailList = getEmails(headerXML)
    instList = getInstitutions(headerXML)

    AuthorEmailTaggingFilter.metrics.logStop("Parsing Header (Method B)")

    if (authorList.length == 0) {
      logger.info("****** Document has no authors listed in it. Exiting ******")
      sys.exit(-1)
    }

    val emailInstMap = mapEmailToInst(emailList, instList)
    mapAuthorToEmail(authorList, emailList, emailInstMap)

    AuthorEmailTaggingFilter.metrics.logStop("AuthorEmailTaggingFilter")

    logger.info(AuthorEmailTaggingFilter.metrics.summary())

    AuthorEmailTaggingFilter.XMLPostProcess(refXML, authorList)
  }

  def mapEmailToInst(emailList: List[Email], instList: List[Institution]) : Map[Email,Institution] = {

    val thelist : List[(Email, Option[Institution])] =
      (for (email <- emailList;
            instTuple <- Institution.lookupInstitution(email.getDomain).toList;
            //instReg = instTuple._1.toLowerCase.r()
            //inst <- instList if instReg.  ) yield {
            inst <- instList) yield {
        // does any inst in the instList match the name in our dictionary?

        val instReg = instTuple._1.toLowerCase.r()

        if (instReg.findFirstIn(inst.name.toLowerCase).nonEmpty) {
          // match!!
          email -> Some(inst)
        } else {
          // TODO add domain ;; institution name / addr to dictionary
          email -> None
        }
      }).toList

    thelist.filter(_._2.isDefined).map(x => x._1 -> x._2.get).toMap
  }

  def mapAuthorToEmail(authorList : List[Author], emailList: List[Email], emailInstMap : Map[Email,Institution]) {

    var matchedSet = scala.collection.mutable.Set[Author]()

    for (email <- emailList;
         author <- authorList;
         if !matchedSet.contains(author);
         emailPossibility <- Email.emailPossibilities(author) ) {

      if (email.userMatches(emailPossibility)) {
        AuthorEmailTaggingFilter.metrics.logSuccess()
        // yeah, matches! I'm sure this is not scala like... must learn more! :)
        // this may not even work.
        // Ideally we'd remove email & author from the search list once we've paired them. How to?
        author.addEmail(email)
        matchedSet add(author)

        emailInstMap.get(email) match {
          case None => // no mapping, ignore!
          case Some(i) => author.addInstitution(i)
        }
      }
    }
    /*
      // just maybe they're matched by notes.  Rare, probably
      if (author.note != None) {
        val auth_note = author.note.getOrElse("") // Should be set at this point, big time error condition if not.

        // find institution if possible
        for (inst <- instList) yield {

          val inst_note = inst.note.getOrElse("")
          if (inst_note == auth_note) {
            author.institution = new Some(inst)
          }
        }
      }
      */
  }

  // this getAuthors will work on the headers xml. Not used (yet) and not quite right yet
  def getAuthors(xml : NodeSeq) : List[Author] = {
    val authorXML = xml \ "authors" \ "author"
    (for (authorTag @ <author>{_*}</author> <- authorXML) yield {
      authorTag.label match {
        case author =>
          new Author(authorTag)
      }
    }).toList
  }

  def getEmails(xml : NodeSeq) : List[Email] = {

    val emailXML = xml \ "email"
    var emailList = List[Email]()
    for (emailTag @ <email>{_*}</email> <- emailXML) yield {
      emailTag match {
        case Elem(prefix, "email", metadata, scope, children @ _*) =>
          // parse metadata
          metadata.map(m => {
            if(m.key.contains("email")) {

            }
          })
          var index = 0;
          val id = metadata.get("id").get.text.toInt
          var emailaddr = metadata.get(s"email$index")
          while (emailaddr != None) {
            emailList ::= new Email(emailaddr.get.text, id, s"email$index")
            index += 1
            emailaddr = metadata.get(s"email$index")
          }
      }
    }
    emailList
  }

  def getInstitutions(xml : NodeSeq) : List[Institution] = {
    val instXML = xml \ "institution"
    (for (instTag @ <institution>{_*}</institution> <- instXML) yield {
      instTag.label match {
        case institution =>
          new Institution(instTag.text, (instTag \ "@id").text.toInt)
      }
    }).toList
  }
}

/**
 * This is a class to help keep some simple metrics about a program and how it's running.
 * This will give us an idea of timing, but if we want to be more accurate we should probably
 * switch to using Metrics Core or Criterium (might be Java only)
 * @param project
 */
class Metrics (project : String) {
  val logger = LoggerFactory.getLogger(Metrics.getClass())
  val projectName = project
  var success : Int = 0
  var failure = scala.collection.mutable.Map[String, String]()
  var timestampMap = scala.collection.mutable.Map[String, (Long, Long)]()

  def logStart(tag: String) : Unit = {
    timestampMap += tag -> (System.nanoTime(), -1.asInstanceOf[Long])
  }

  def logStop(tag: String) : Unit = {
    //val tagInfo = timestampMap.get(tag)
    val timeval = timestampMap.getOrElse(tag, (-1L, -1L))

    if (timeval._1 != -1) {
      timestampMap += tag -> (timeval._1, System.nanoTime())
    }
  }

  def getTimeMS(tag: String) : Double = {
    val timeval = timestampMap.getOrElse(tag, (-1L,-1L))
    (timeval._2 - timeval._1) / 1e6
  }

  // tell the time it takes to run a function
  def getFunctionTime[A](func: => A) = {
    val s = System.nanoTime()
    val ret = func
    logger.info("time: " + (System.nanoTime() - s) / 1e6 + "ms")
    ret
  }

  def logSuccess() : Unit = { success += 1 }

  def logFailure(fname: String, errMsg: String) : Unit = {
    if (fname != "" && errMsg != "") {
      failure += fname -> errMsg
    }
  }

  def successCount() : Int = {success}
  def failureCount() : Int = {failure.size}

  def summary() : String = {
    s"Successfully matched $success emails(s).\n"+
      s"Failed on the following ${failure.size}: \n" +
      (for ((key,value) <- failure) yield {s"\t$key: $value\n"}).toList.mkString  +
      "Time Values: \n" +
      (for ((key,(start, stop)) <- timestampMap) yield {s"\t$key: " + this.getTimeMS(key) +  "ms\n"}).toList.mkString
  }
}

object Metrics {
  val logger = LoggerFactory.getLogger(Metrics.getClass())
  def main(args : Array[String]) {
    TestMetric("Test Run")
  }

  def TestMetric (projectName : String) : Unit = {
    val metric = new Metrics(projectName)
    metric.logStart("program run time")

    metric.logStart("success")
    metric.logSuccess()
    metric.logStop("success")
    metric.logStart("failure")
    metric.logFailure("foo.txt", "file does not exist")
    metric.logStop("failure")

    //metric.getFunctionTime(metric.logSuccess())
    metric.logStop("program run time")

    logger.info(metric.summary())
  }
}

// quick and dirty command line argument parser...
// todo- make this more robust at some point

object ParseArgs {

  def parseArgs(progname : String, args : Array[String], usage : () => Unit) : scala.collection.mutable.Map[String,String] = {
    var map = scala.collection.mutable.Map[String, String]()
    val stack = scala.collection.mutable.Stack[String](args: _*) // _* factory method to make the args array viewed as list of strings

    if (stack.isEmpty) {
      usage()
      sys.error("Missing arguments") // exit
    }

    try {
      do {
        val arg = stack.pop //stack.headOption.getOrElse("unknown")
        println(s"arg is $arg")
        arg match {
          case "-i" | "-d" | "-f" | "-o" => map += arg -> stack.pop() //stack.headOption.getOrElse("")
          case `progname` => // ignore
          case "unknown" => println(s"Shouldn't reach here!: $arg")
          case _ => usage(); //sys.error(s"Unknown argument: $arg")
        }
      } while (stack.nonEmpty)

      map

    } catch {
      case e: NoSuchElementException => usage(); System.err.print("Missing argument: " + e); map
      case e: Exception => System.err.print("Unable to parse command line arguments: " + e); map
    }
  }
}

package org.rexo.util

import java.io.{PrintStream, File}

import scala.collection.immutable.List
import scala.xml.{Node, NodeSeq, XML, Elem, Attribute, Text, Null}
import org.rexo.ui.AuthorEmailTaggingFilter
import org.slf4j.{Logger, LoggerFactory}

object Analyzer {
  var testList : List[TestFilter] = List[TestFilter]()
  val logger = LoggerFactory.getLogger(Analyzer.getClass)

  def addTest(test : TestFilter) = {
    testList ::= test
  }

  def usage() {
    println("Program Usage:")
    println("\tFilterAnalyzer -d <directory> -r <csv results file> [-f <outfilename>]")
    println("   -d    directory where processed files are. Will only operate on *.meta.xml files")
    println("   -r    CSV results file. One row per file")
    println("   -f    optional filename specifing where to print results to. Default is stdout")
  }

  def main(args: Array[String]) {

    val arguments: scala.collection.mutable.Map[String,String] = ParseArgs.parseArgs("MetaTaggerAnalyzer", args, "d:r:f:", usage)
    val dir = arguments.getOrElse("-d", "")
    val csvFilename = arguments.getOrElse("-r", "")
    val outfile = arguments.getOrElse("-f", "")

    if (dir == "" || csvFilename == "") {
      println("Missing arguments, unable to proceed.")
      usage()
      sys.exit()
    }

    // get pdf.meta.xml list
    val directory = new File(dir)
    val fileList = getFileList(directory, "pdf.meta.xml")
    fileList.foreach(x => println(x.getName() + "\n"))

    // open csv file
    val resultsMap = parseCSVData(csvFilename)

    Analyzer.addTest(new AnalyzeAuthorEmailTagging())

    // 23 elements in header
    val results =
      //for ((filename, info) <- resultsMap;
       //   test <- testList) yield {
      for (file <- fileList;
           test <- testList)  yield {
        val info = resultsMap.getOrElse(file.getName().stripSuffix(".meta.xml"), Map.empty[String, Map[String, String]]);
        logger.info("Looking at: " + test.getName)

        if (info.nonEmpty) {
          test.apply(file, info)
        } else {
          val emptyResults = new AuthorEmailFilterResults(file.getName)
          emptyResults.registerErrorMsg("No expected information found for this file. Unable to compare.  Is this a good file?")
          emptyResults
        }
      }

    var totalSuccesses = 0
    var totalFiles = 0
    var totalNumberAuthors = 0
    var totalPartialEmail = 0
    var totalPartialInst = 0
    var totalNumberEmails = 0

    results.foreach (x => {
      totalSuccesses += x.fullSuccesses
      totalFiles += 1
      totalNumberAuthors += x.totalSamples
      totalPartialEmail += x.asInstanceOf[AuthorEmailFilterResults].partialEmail
      totalPartialInst += x.asInstanceOf[AuthorEmailFilterResults].partialInst
      x.prettyPrint(System.out)
    })

    val emailPercentage : Float =  if(totalPartialEmail > 0) (totalPartialEmail.toFloat/totalNumberAuthors.toFloat * 100) else 0
    val instPercentage : Float =  if(totalPartialInst > 0) (totalPartialInst.toFloat/totalNumberAuthors.toFloat * 100) else 0
    val matchPercentage : Float  = if (totalSuccesses > 0) (totalSuccesses.toFloat / totalFiles.toFloat * 100) else 0

    println("\n--------------------------------------------------------\n")
    println(s"\nTotal number of files analyzed: $totalFiles")
    println(f"Complete Matches: $totalSuccesses%d   $matchPercentage%.2f%%")
    println("Average authors per file: " + totalNumberAuthors/totalFiles)
    println(f"Partial Match - Email: $totalPartialEmail%d  $emailPercentage%.2f%%")
    println(f"Partial Match - Institution: $totalPartialInst%d  $instPercentage%.2f%%")
    println("\n--------------------------------------------------------\n")
  }

  def parseCSVData(csvFilename: String) : Map[String, Map[String,Map[String,String]]] = {
    val csvData = scala.io.Source.fromFile(csvFilename).getLines()

    val header = csvData.next() /* header line */
    val mapAssoc = header.split(",")
    val MAX_NUM_AUTHORS = 7
    // for the rest of the entries
    (for (data <- csvData) yield {
      val splitLine = data.split(",")
      var i : Int = 2 /* ignore 'filename' and 'applicable' for now */
      var infoMap : Map[String, Map[String, String]] = Map[String, Map[String,String]]()
      while (i < splitLine.length && splitLine.isDefinedAt(i+1) && splitLine.isDefinedAt(i+2)) {
        infoMap += (splitLine(i) -> Map("Email" -> splitLine(i+1), "Institute" -> splitLine(i+2)))
        i += 3
      }
      splitLine(0) -> infoMap
    }).toMap
  }

  def getFileList(dir: File, suffix: String): Array[File] = {
    val these = dir.listFiles

    val reg = ("""^.*""" + suffix + """$""").r
    these.filter(f => reg.findFirstIn(f.getName).isDefined)
  }
}


class TestFilterResults(filename : String, filtername: String) {
  val name = filtername
  var totalSamples = 0
  var fullSuccesses: Int = 0
  // filter found a match, but it wasn't the expected results
  var falseMatches: Int = 0
  //val time_ms: Double = 0 // unused

  def upSampleCount() = {
    totalSamples += 1
  }

  def registerSuccess() = {
    fullSuccesses += 1
  }

  def registerFalseMatch() = {
    falseMatches += 1
  }

  def prettyPrint(stream: PrintStream) = {
    val info = s"\nFiltername: $name\n"
      s"\tTotal samples: $totalSamples\n"
      s"\tSuccesses: $fullSuccesses\n"
      s"\tFalse Matches: $falseMatches\n"

    stream.print(info)
  }
}

class AuthorEmailFilterResults(filename : String) extends TestFilterResults (filename, "AuthorEmailFilter") {
  var partialEmail: Int = 0
  var partialInst: Int = 0

  var foundRecordList : List[Map[String,String]] = List[Map[String, String]]()
  var expectedRecordList : List[Map[String, String]] = List[Map[String,String]]()
  var noMatchList : List[Map[String, String]] = List[Map[String,String]]()
  var errorMsgs : List[String] = List[String]()

  def registerPartialSuccess(kind: String) = {
    if (kind == "EMAIL") {
      partialEmail += 1
    } else if (kind == "INST") {
      partialInst += 1
    }
  }

  def registerSampleInfo(found : Map[String,String], expected: Map[String,String]) {
    foundRecordList ::= found
    expectedRecordList ::= expected
  }

  def registerNoMatch(found : Map[String, String]) {
    noMatchList ::= found
  }

  def registerErrorMsg(msg : String) {
    errorMsgs ::= msg
  }

  override def prettyPrint(stream: PrintStream) = {
    var info = s"\n\nFilename: $filename\n" +
      s"\tFilter: $name\n" +
      s"\tNumber of authors looked at: $totalSamples\n" +
      s"\tNumber fully matched: $fullSuccesses " + (if (fullSuccesses > 0) fullSuccesses/totalSamples +"%") + "\n" +
      s"\tPartial matches:\n" +
      s"\t\t(email only): $partialEmail\n" +
      s"\t\t(inst only): $partialInst\n" +
      s"\tFalse Matches: $falseMatches\n"

    if (foundRecordList.nonEmpty && expectedRecordList.nonEmpty) {

      val str = (for((frecord,index) <- foundRecordList.zipWithIndex) yield {
          val erecord = expectedRecordList(index)

          "\t\t%-50s  %-50s\n\t\t%-50s  %-50s\n\t\t%-50s  %-50s\n\n".format(frecord("AUTHOR"), erecord("AUTHOR"),
            frecord("EMAIL"), erecord("EMAIL"), frecord("INST"), erecord("INST"))

        }).mkString

      info = info.concat(s"\n\tFilter Found:\t\t\t\t\tExpected:\n\n$str")
    }

    if (noMatchList.nonEmpty) {

      val str = noMatchList.map(frecord =>
        s"\t\t" + frecord("AUTHOR") + "\n" +
        s"\t\t" + frecord("EMAIL") + "\n" +
        s"\t\t" + frecord("INST") + "\n"
      ).mkString

      info = info.concat(s"\tUnmatched information:\n $str")
    }

    if (errorMsgs.nonEmpty) {
      val str = (errorMsgs.map(x => s"\t\t$x\n")).mkString
      info = info.concat(s"\tError Messages: \n$str")
    }

    stream.print(info)
  }
}

abstract class TestFilter() {
  def apply(XMLfile : File, expectedResults : Map[String,Map[String,String]]) : TestFilterResults

  def getName : String

}

class AnalyzeAuthorEmailTagging() extends TestFilter {

  val logger = LoggerFactory.getLogger(AnalyzeAuthorEmailTagging.this.getClass)

  override val getName = "AuthorEmailTaggingFilter"

  def apply(XMLFile : File, expResults : Map[String,Map[String,String]]) : TestFilterResults = {

    val authorEmailFilter = new AuthorEmailTaggingFilter()

    // open the XML and read in the header information:
    val xmlFile = XML.loadFile(XMLFile)
    val pdfName = XMLFile.getName.stripSuffix(".meta.xml")

    logger.info(s"Analyzing results for file: $pdfName")

    val results = new AuthorEmailFilterResults(XMLFile.getName)

    val headerXML = xmlFile \ "content" \ "headers"

    val authorList = authorEmailFilter.getAuthors(headerXML)
    val emailList = authorEmailFilter.getEmails(headerXML)
    val instList = authorEmailFilter.getInstitutions(headerXML)

    if (authorList.isEmpty) {
      results.registerErrorMsg("No authors found in pdf.meta.xml file.  No author tags in file?")
    }
    if (emailList.isEmpty) {
      results.registerErrorMsg("No email addresses found in pdf.meta.xml file.  No email tags in file?")
    }
    if (instList.isEmpty) {
      results.registerErrorMsg("No institution found in pdf.meta.xml file. No institution tags in file?")
    }

    for ((author, index) <- authorList.zipWithIndex) {

      results.upSampleCount()

      // get author's name, email and inst from the xml document.
      // compare them to the expected results.

      val xmlAuthor = author.getFullName
      var xmlEmail = ""
      var xmlInst = ""

      if (author.emailMeta != None) {
        // Get the email A
        val emailInfo = author.emailMeta.get.split("-")
        val id = emailInfo(0).toInt
        val elList = emailList.filter(e => e.id == id && e.tag == emailInfo(1))

        if (elList.length == 1) {
          logger.info("found matching email for " + xmlAuthor)
          xmlEmail = elList(0).toString()
        } else {
          if (elList.length == 0) {
            logger.info("no email for author " + xmlAuthor)
          } else {
            logger.info("TROUBLE: filtered email list has more then one matching email!")
          }
        }
      }

      if (author.instMeta != None) {
        val instID = author.instMeta.get.toInt
        val filteredInstList = instList.filter(_.id == instID)

        if (filteredInstList.length == 1) {
          logger.info("Found matching institution for " + xmlAuthor)
          xmlInst = filteredInstList(0).name
        } else {
          if (filteredInstList.length == 0) {
            logger.info("no institution found for author " + xmlAuthor)
          }
          logger.info("")
        }
      }

      var matched = false

      val resultSet : Map[String, String]= expResults.getOrElse(xmlAuthor, Map.empty[String, String])

      if (resultSet.nonEmpty) {

        results.registerSampleInfo(Map("AUTHOR"->xmlAuthor, "EMAIL"->xmlEmail,"INST"->xmlInst),
          Map("AUTHOR"->xmlAuthor, "EMAIL"->resultSet("Email"), "INST"->resultSet("Institute")))

        matched = true  // at least to some degree
        val emailMatch = (resultSet("Email").nonEmpty && resultSet("Email") == xmlEmail)
        val instMatch = (resultSet("Institute").nonEmpty && resultSet("Institute") == xmlInst)

        if (emailMatch && instMatch) {
          results.registerSuccess()
        } else if (emailMatch) {
          results.registerPartialSuccess("EMAIL")
        } else if (instMatch) {
          results.registerPartialSuccess("INST")
        }

        if (xmlEmail != ""  && !emailMatch)  {
          logger.info("Have email related to author, but it doesn't match expected results:")
          logger.info(s"\tAuthor: $xmlAuthor")
          logger.info(s"\txmlEmail: $xmlEmail : expected ${resultSet("Email")}")
          logger.info(s"\txmlInst: $xmlInst : expected ${resultSet("Institute")}")
          results.registerFalseMatch()
        }
      } else {
        results.registerNoMatch(Map("AUTHOR"->xmlAuthor, "EMAIL"->xmlEmail, "INST"->xmlInst))
      }
/*
      // now compare!
      val csvIterator = expResults.iterator
      while (csvIterator.hasNext && !matched) {
        val set = csvIterator.next()
        if (set._2 == xmlAuthor) {
          // found author, now compare email, etc
          matched = true

          val csvEmail = csvIterator.next()._2
          val csvInst = csvIterator.next()._2

          val emailMatch = csvEmail == xmlEmail
          val instMatch = csvInst == xmlInst

          if (emailMatch && instMatch) {
            results.registerSuccess()
          } else if (emailMatch) {
            results.registerPartialSuccess("EMAIL")
          } else if (instMatch) {
            results.registerPartialSuccess("INST")
          }

          if (xmlEmail != ""  && !emailMatch)  {
            logger.info("Have email related to author, but it doesn't match expected results:")
            logger.info(s"\txmlAuthor: $xmlAuthor  : expected ${set._2}")
            logger.info(s"\txmlEmail: $xmlEmail : expected $csvEmail")
            logger.info(s"\txmlInst: $xmlInst : expected $csvInst")
            results.registerFalseMatch()
          }
        }
      }
*/
      if (!matched)
        logger.info(s"Failed to find expected results for author: $xmlAuthor")
    }
    results
  }
}

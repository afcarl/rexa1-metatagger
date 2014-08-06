package edu.umass.cs.rexo.ghuang.segmentation;

import edu.umass.cs.mallet.base.extract.Span;
import edu.umass.cs.mallet.base.extract.StringSpan;
import edu.umass.cs.mallet.base.pipe.Pipe;
import edu.umass.cs.mallet.base.types.Instance;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.rexo.ghuang.segmentation.utils.LayoutUtils;
import edu.umass.cs.rexo.ghuang.segmentation.utils.LayoutUtils.ColumnData;
import org.rexo.extraction.NewHtmlTokenization;
import org.rexo.span.CompositeSpan;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by klimzaporojets on 8/4/14.
 * Adds useful features to identify parts of the body of a particular paper.
 */
public class Token2BodyFeatureSequence  extends Pipe implements Serializable {

    private static String lonelyNumbers = "[1-9][\\.]{0,1}[\\s]+]"; //"[1-9][\\.]{0,1}";
    private static String lonelyLetters = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ][\\.]{0,1}[\\s]+]"; //"[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ][\\.]{0,1}";

    static Pattern ptrnLonelyNumbers = Pattern.compile(lonelyNumbers);
    static Pattern ptrnLonelyLetters = Pattern.compile(lonelyLetters);

    @Override
    public Instance pipe(Instance carrier) {
        NewHtmlTokenization data = (NewHtmlTokenization)carrier.getData();

        List<CompositeSpan> lineSpans = data.getLineSpans();
        NewHtmlTokenization2LineInfo nhtml2LineInfo = new NewHtmlTokenization2LineInfo();
        Instance onlyLines =  nhtml2LineInfo.pipe(carrier);
        computeFeatures((LineInfo [])onlyLines.getData(),data);

        return carrier;
}


    private void computeFeatures(LineInfo[] lineInfos, NewHtmlTokenization data)
    {
        computeLexiconFeatures(data);
        computeLayoutFeatures(lineInfos, data);
    }

    private static void computeLayoutFeatures(LineInfo[] lineInfos, NewHtmlTokenization data) {
        List <LayoutUtils.Entry<Integer>> verticalDistance = new ArrayList<LayoutUtils.Entry<Integer>>();
        List <LayoutUtils.Entry<Integer>> widthLine = new ArrayList<LayoutUtils.Entry<Integer>>();

        //it can be the case when the first page has one column and the rest of the pages two for example, this is why it is important
        //to have a per-page width stats
        Map<Integer, List<LayoutUtils.Entry<Integer>>> widthLinePerPage = new HashMap <Integer, List<LayoutUtils.Entry<Integer>>>();

        Map <Integer, List<LayoutUtils.Entry<ColumnData>>> columnsData = new HashMap<Integer,List<LayoutUtils.Entry<ColumnData>>>();

        Map <Integer, List<LayoutUtils.Entry<ColumnData>>> leftMarginsData = new HashMap<Integer,List<LayoutUtils.Entry<ColumnData>>>();

        Map <Integer, LayoutUtils.PageData> pagesData = new HashMap<Integer,LayoutUtils.PageData>();

        Map <Integer, List<ColumnData>> columns = new HashMap<Integer,List<ColumnData>>();

        //first scan to calculate general statistics of the paper (such as line width, or vertical distances between lines)
        for(int i =0; i<lineInfos.length; i++ )
        {
            LayoutUtils.adjustVerticalDistance(lineInfos, i, verticalDistance);

            LayoutUtils.adjustLineWidth(lineInfos, i, widthLine);

            LayoutUtils.adjustLineWidthPerPage(lineInfos, i, widthLinePerPage);

            LayoutUtils.adjustColumnData(lineInfos, i, columnsData, true, 3);

            LayoutUtils.adjustColumnData(lineInfos, i, leftMarginsData, false,0);

            LayoutUtils.adjustPageData(lineInfos, i, pagesData);
        }
        Collections.sort(verticalDistance);
        Collections.sort(widthLine);
        for(Integer page: widthLinePerPage.keySet())
        {
            Collections.sort(widthLinePerPage.get(page));
            Collections.sort(columnsData.get(page));
            Collections.sort(leftMarginsData.get(page));

//            List<ColumnData> currentPageCols = LayoutUtils.getColumns(columnsData.get(page),pagesData.get(page));
            List<ColumnData> currentPageCols = LayoutUtils.getColumnsV2(columnsData.get(page),pagesData.get(page));

            Collections.sort(currentPageCols, new Comparator<ColumnData>() {
                @Override
                public int compare(ColumnData o1, ColumnData o2) {
                    if(o1.getLeftX()>o2.getLeftX())
                    {
                        return 1;
                    }
                    else if (o1.getLeftX()<o2.getLeftX())
                    {
                        return -1;
                    }
                    else
                    {
                        return 0;
                    }

                }
            });
            columns.put(page,currentPageCols);
        }

        //second scan to calculate more detailed features based on the statistics of the first scan
        for(int i =0; i<lineInfos.length; i++ )
        {
            ColumnData currentLineColumn  = LayoutUtils.getCurrentLineColumn(lineInfos,i,columns.get(lineInfos[i].page)) ;
            Span lineSpan = (Span)data.getLineSpans().get(i);
            if(currentLineColumn == null)
            {
                //add "noColumnAssociated" feature
                LayoutUtils.setFeatureValue(lineSpan,"noColumnAssociated",1.0);
//                lineSpan.setFeatureValue("noColumnAssociated", 1.0);
            }
        }

        System.out.print("sorted vertical distances");

    }

    private static void computeLexiconFeatures(/*LineInfo[] lineInfos,*/ NewHtmlTokenization data) {
        // high correlation with non-bibliographic content
        String[] nonSectionWords = {"^(Table).*", "^(Figure).*", "^(Fig\\.).*"};
        String allCaps = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ1-9]+";
        String initCap = "[A-ZÁÉÍÓÚÀÈÌÒÙÇÑÏÜ].*";
        String finalDot = "((.*)\\.)$";
        String firstLevelSection = "^((\\s).*([\\d]+)([\\.]{0,1})([\\s]+).*)";
        String secondLevelSection = "^((\\s).*([\\d]+)(\\.)([\\d]+)([\\.]{0,1})([\\s]+).*)";
        String thirdLevelSection = "^((\\s).*([\\d]+)(\\.)([\\d]+)(\\.)([\\d]+)([\\.]{0,1})([\\s]+).*)";


//        String fourthLevelSection = "^((\\s).*([\\d]+)(\\.)([\\d]+)(\\.)([\\d]+)([\\.]{0,1})([\\s]+).*)";

                /*{ "^[^A-Za-z]*Received[^A-Za-z]",
                "^[A-Za-z]*Figure(s)?[^A-Za-z]",
                "^[A-Za-z]*Table(s)?[^A-Za-z]", "^[A-Za-z]*Graph(s)?[^A-Za-z]",
                "We ", " we ", "She ", " she ", "He ", " he ", "Our ", " our ",
                "Her ", " her ", "His ", " his ", "These ", " these ", "Acknowledgements" };*/

        for(int i =0; i<data.getLineSpans().size(); i++ )
        {
            List<String> lexiconFeatures = new ArrayList<String>();
            Span ls = (Span)data.getLineSpans().get(i);

            String currentLineText = ls.getText().trim();
            String squishedLineText = currentLineText.replaceAll("\\s", "");

            for (int j = 0; j < nonSectionWords.length; j++) {
                if (currentLineText.matches(nonSectionWords[j])) {
                    LayoutUtils.setFeatureValue(ls, "startsNonSectionWord", 1.0);
                    break;
                }
            }

            if(squishedLineText.matches(allCaps))
            {
                LayoutUtils.setFeatureValue(ls, "allCaps", 1.0);
            }
            if(currentLineText.matches(initCap))
            {
                LayoutUtils.setFeatureValue(ls, "startsCap", 1.0);
            }
            if(currentLineText.matches(finalDot))
            {
                LayoutUtils.setFeatureValue(ls, "endsInDot", 1.0);
            }

            if(isUpFlagCount(currentLineText,ptrnLonelyLetters,0.5))
            {
                LayoutUtils.setFeatureValue(ls, "manyLonelyLetters", 1.0);
            }

            if(isUpFlagCount(currentLineText,ptrnLonelyNumbers,0.5))
            {
                LayoutUtils.setFeatureValue(ls, "manyLonelyNumbers", 1.0);
            }

            if(currentLineText.matches(firstLevelSection))
            {
                LayoutUtils.setFeatureValue(ls, "firstLevelSectionPtrn", 1.0);
            }

            if(currentLineText.matches(secondLevelSection))
            {
                LayoutUtils.setFeatureValue(ls, "secondLevelSectionPtrn", 1.0);
            }

            if(currentLineText.matches(thirdLevelSection))
            {
                LayoutUtils.setFeatureValue(ls, "thirdLevelSectionPtrn", 1.0);
            }
        }
    }
    private static boolean isUpFlagCount(String text, Pattern pattern, Double ratioActivation)
    {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while(matcher.find())
        {
            count++;
        }
        if(Double.valueOf(count)/Double.valueOf(text.split(" ").length) > ratioActivation)
        {
            return true;
        }
        return false;
    }

}

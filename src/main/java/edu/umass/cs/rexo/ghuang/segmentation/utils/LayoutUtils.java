package edu.umass.cs.rexo.ghuang.segmentation.utils;

import edu.umass.cs.mallet.base.extract.Span;
import edu.umass.cs.mallet.base.extract.StringSpan;
import edu.umass.cs.rexo.ghuang.segmentation.LineInfo;
import org.rexo.extraction.NewHtmlTokenization;
import org.rexo.span.CompositeSpan;
import org.rexo.util.EnglishDictionary;

import java.util.*;

/**
 * Created by klimzaporojets on 8/5/14.
 * Some utility functions related to layout.
 */
public class LayoutUtils {


    public static Object getProperty(Object span, String property )
    {
        if(span instanceof StringSpan )
        {
            return ((StringSpan)span).getProperty(property);
        }
        else if(span instanceof CompositeSpan)
        {
            return ((CompositeSpan)span).getProperty(property);
        }
        else if(span instanceof NewHtmlTokenization)
        {
            return ((NewHtmlTokenization)span).getProperty(property);
        }
        else
        {
            //todo: see what to do here
            return null;
        }
    }
    public static boolean isAnyOfFeaturesInFuture(NewHtmlTokenization data, int i, List<String> features, int minOcurrences, int linesLookForward)
    {
        int qtyOcurrences = 0;
        for (int cnt = i; cnt<i+linesLookForward && cnt<data.getLineSpans().size(); cnt++) {
            if(cnt>=data.getLineSpans().size())
            {
                break;
            }
            Span lineSpan = (Span) data.getLineSpans().get(cnt);
            for(String feature:features)
            {
                if(isActiveFeature(lineSpan,feature))
                {
                    qtyOcurrences++;
                    break;
                }
            }
        }
        return qtyOcurrences>=minOcurrences;
    }

    public static boolean isFigureInTheFuture(NewHtmlTokenization data, int i, int linesLookForward)
    {
        int linesProcessed = 0;
        for (int cnt = i; cnt<i+linesLookForward && cnt<data.getLineSpans().size(); cnt++) {
            linesProcessed++;


            Span lineSpan = (Span) data.getLineSpans().get(cnt);
            if(lineSpan == null)
            {
                //todo: check if it is ok to return true in this case
                return true;
            }
            Span nextSpan = null;

            if(cnt+1<data.getLineSpans().size())
            {
                nextSpan = (Span)data.getLineSpans().get(cnt+1);
            }

            if (cnt >= data.getLineSpans().size()) {
                break;
            }

            if(linesProcessed==1)
            {
                if(isActiveFeature(lineSpan, "rightMarginToTheLeft") && (nextSpan==null || isActiveFeature(nextSpan, "up") || isActiveFeature(nextSpan, "right")
                                  || isActiveFeature(lineSpan, "verticalDistance4pxGreater")))
                {
                    return true;
                }
            }
            else
            {
                if(!isActiveFeature(lineSpan, "shorterThanPreviousLine") && nextSpan!=null && (isActiveFeature(nextSpan, "up") || isActiveFeature(nextSpan, "right")
                        || isActiveFeature(lineSpan, "verticalDistance4pxGreater")))
                {
                    return false;
                }
                if(isActiveFeature(lineSpan, "sameLeftMarginAsPreviousLine") && isActiveFeature(lineSpan, "shorterThanPreviousLine") &&
                        (nextSpan == null || isActiveFeature(nextSpan, "up") || isActiveFeature(nextSpan, "right")
                                          || isActiveFeature(lineSpan, "verticalDistance4pxGreater")))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setFeatureValue(Span span, String property, double value)
    {
        if(span instanceof CompositeSpan)
        {
            ((CompositeSpan)span).setFeatureValue(property,value);
        }
        else
        {
            ((StringSpan)span).setFeatureValue(property,value);
        }
    }

    public static boolean isPropertySet(Span span, String property)
    {
        if(span instanceof CompositeSpan)
        {
            return ((CompositeSpan)span).getNumericProperty (property) == 1.0;
        }
        else
        {
            return ((StringSpan)span).getNumericProperty(property) == 1.0;
        }
    }

    public static boolean isActiveFeature(Span span, String property)
    {
        try
        {

            if(span instanceof CompositeSpan)
            {
                if(((CompositeSpan)span).getFeatureValue(property) == 1.0)
                {
                    return true;
                }
            }
            else
            {
                if(((StringSpan)span).getFeatureValue(property) == 1.0)
                {
                    return true;
                }
            }
            return false;
        }
        catch(Exception ex )
        {
            ex.printStackTrace();
            return false;
        }
    }

    public static ColumnData getCurrentLineColumn(LineInfo[] lineInfos, int i, List<ColumnData> columns,
                                                 boolean equalsBothMargins, int acceptableMarginErrorLeft, int acceptableMarginErrorRight,
                                                    ColumnData currentColumn, int modeVerticalDistance)
    {
        LineInfo lineInfo = lineInfos[i];
        ColumnData columnData = getColumnData(equalsBothMargins,acceptableMarginErrorLeft, acceptableMarginErrorRight,lineInfos[i]);
        ColumnData sloppyColumn = null;
        for(ColumnData col:columns)
        {
            boolean doesBelong = doesBelongToColumnStrict(col, columnData);
            if(doesBelong)
            {
                return col;
            }
            else if(doesBelongToColumnSloppy(col,columnData,false,false,-1) && doesBelongToColumnVert(col, columnData))
            {
                return col;
            }
            else if(doesBelongToColumnSloppy(col,columnData,false,false,-1))
            {
                int distanceFromColumnVert = getVerticalDistanceFromColumn(col,columnData);
                if(distanceFromColumnVert <= modeVerticalDistance
                        //at most 2 px bigger than the mode distance
                        + 2)
                {
                    sloppyColumn = col;
                }

                //
            }
            //todo: more else if to take into account more situations
        }
        return sloppyColumn;
//        return null;
    }

    public static ColumnData getClosestCurrentLineColumn(LineInfo[] lineInfos, int i, List<ColumnData> columns,
                                                         boolean equalsBothMargins, int acceptableMarginErrorLeft, int acceptableMarginErrorRight,
                                                         boolean strictLeft, boolean rightTops, int acceptableRightTopMarginError)
    {
        ColumnData columnData = getColumnData(equalsBothMargins,acceptableMarginErrorLeft, acceptableMarginErrorRight,lineInfos[i]);
        ColumnData colLeastDistance = null;
        int distance = -1;

        for(ColumnData col:columns)
        {
            if(doesBelongToColumnSloppy(col, columnData,strictLeft, rightTops, acceptableRightTopMarginError))
            {
                int currDistance = getVerticalDistanceFromColumn(col,columnData);
                if(colLeastDistance==null || distance ==-1 || currDistance < distance)
                {
                    colLeastDistance = col;
                }
            }
        }

        return colLeastDistance;
    }

//    public static ColumnData getClosestCurrentLineColumnRight(LineInfo[] lineInfos, int i, List<ColumnData> columns,
//                                                         boolean equalsBothMargins, int acceptableMarginError,
//                                                         boolean strictLeft)
//    {
//        ColumnData columnData = getColumnData(equalsBothMargins,acceptableMarginError,lineInfos[i]);
//        ColumnData colLeastDistance = null;
//        int distance = -1;
//
//        for(ColumnData col:columns)
//        {
//            if(doesBelongToColumnSloppy(col, columnData,strictLeft))
//            {
//                int currDistance = getVerticalDistanceFromColumn(col,columnData);
//                if(colLeastDistance==null || distance ==-1 || currDistance < distance)
//                {
//                    colLeastDistance = col;
//                }
//            }
//        }
//
//        return colLeastDistance;
//    }

    private static boolean doesBelongToColumnStrict(ColumnData col, ColumnData colToCompare)
    {
        return col.equals(colToCompare);
    }
    /*
    * Steps to detect if sloppily belongs to a column
    * 1- if "newColumn", check the column of the following 2 lines
    * 2- if not "newColumn", check the column worked so far and if it sloppily can belong to that column
    * */
    private static boolean doesBelongToColumnSloppy(ColumnData col, ColumnData colToCompare, boolean strictLeft,
                                                    boolean rightTops, int acceptableRightTopMarginError)
    {
        int relaxedColLeft = col.getLeftX() - col.getErrorMarginLeft();

        int relaxedColRight = col.getRightX() + col.getErrorMarginRight();
        int relaxedColRightLeft = col.getRightX() - col.getErrorMarginRight();
        int relaxedColRightTops = col.getRightX() + acceptableRightTopMarginError;

        if(!strictLeft && !rightTops && colToCompare.getLeftX() > relaxedColLeft && colToCompare.getRightX() < relaxedColRight)
        {
            return true;
        }
        else if(strictLeft && !rightTops && //only 1 px of error margin allowed
                (colToCompare.getLeftX() >= col.getLeftX()-1 && colToCompare.getLeftX() <= col.getLeftX()+1)
                && colToCompare.getRightX() < relaxedColRight)
        {
            return true;
        }
        else if(strictLeft && rightTops && //only 1 px of error margin allowed
                (colToCompare.getLeftX() >= col.getLeftX()-1 && colToCompare.getLeftX() <= col.getLeftX()+1)
                && colToCompare.getRightX() >= relaxedColRightLeft && colToCompare.getRightX()<=relaxedColRightTops)
        {
            return true;
        }
        else if(!strictLeft && rightTops && //only 1 px of error margin allowed
                colToCompare.getLeftX() > relaxedColLeft
                && colToCompare.getRightX() >= relaxedColRightLeft && colToCompare.getRightX()<=relaxedColRightTops)
        {
            return true;
        }
        return false;
    }

    public static boolean isRightMarginToTheLeft(LineInfo lineInfo, ColumnData columnData, int margin)
    {
        if(columnData==null) {
            return false;
        }
        if(lineInfo.urx <= columnData.getRightX()-margin)
        {
            return true;
        }
        return false;
    }

    public static boolean isLeftMarginTabbed(LineInfo lineInfo, ColumnData columnData, int margin)
    {
        if(columnData==null) {
            return false;
        }
        if(lineInfo.llx >= columnData.getLeftX()+margin)
        {
            return true;
        }
        return false;
    }

    public static boolean isShorterThanPrevious(LineInfo [] lineInfo, int i, int margin)
    {
        LineInfo previousLine = null;
        if(i<=0 || lineInfo[i].page!= lineInfo[i-1].page)
        {
            return false;
        }
        else
        {
            previousLine = lineInfo[i-1];
        }
        LineInfo currentLine = lineInfo[i];

        if(previousLine.urx - previousLine.llx - margin > currentLine.urx - currentLine.llx)
        {
            return true;
        }
        return false;
    }

    public static boolean isSameLeftMarginAsPrevious(LineInfo [] lineInfo, int i, int errMargin)
    {
        LineInfo previousLine = null;
        if(i<=0 || lineInfo[i].page!= lineInfo[i-1].page)
        {
            return false;
        }
        else
        {
            previousLine = lineInfo[i-1];
        }
        LineInfo currentLine = lineInfo[i];

        if(previousLine.llx - errMargin <= currentLine.llx && previousLine.llx + errMargin >= currentLine.llx)
        {
            return true;
        }
        return false;

    }


    private static boolean doesBelongToColumnVert(ColumnData col, ColumnData colToCompare)
    {
        if(colToCompare.getBottomY() >= col.getBottomY() &&
                colToCompare.getTopY() <= col.getTopY())
        {
            return true;
        }
        return false;
    }


    private static int getVerticalDistanceFromColumn(ColumnData col, ColumnData colToCompare)
    {
        if(colToCompare.getBottomY() < col.getBottomY() )
        {
            return col.getBottomY() - colToCompare.getBottomY();
        }
        else if (colToCompare.getTopY() > col.getTopY())
        {
            return colToCompare.getTopY() - col.getTopY();
        }
        else
        {
            return -1;
        }
    }


    public static List<ColumnData> getColumns(List<Entry<ColumnData>> allSpans, PageData pageData)
    {
        List<ColumnData> columnList = new ArrayList<ColumnData>();

        //by default, considers that the first column, is the one that appears first in the list of allSpans grouped by qty
        ColumnData firstColumn = allSpans.get(0).getKey();
        columnList.add(firstColumn);

//        int columnsSoFar = 1;
        int widthSoFar = firstColumn.getWidth();

        if(firstColumn.getWidth()>((double)pageData.getWidth())/2.0)
        {
            return columnList;
        }

        for(Entry<ColumnData> colData:allSpans)
        {
            if(!isOverlapping(columnList,colData.getKey()) && isWidthSimilar(columnList,colData.getKey(),0.05))
            {
                //add column
                columnList.add(colData.getKey());
                //update the accumulated width and counter of cols
                widthSoFar =+ colData.getKey().getWidth();
//                columnsSoFar++;
                //check if continue the loop by checking the accumulatedWidth
                if(widthSoFar + firstColumn.getWidth() > pageData.getWidth())
                {
                    break;
                }
            }
        }
        return columnList;
    }


    //this version takes into account that the width of the column can vary on a particular page of a paper
    public static List<ColumnData> getColumnsV2(List<Entry<ColumnData>> allSpans, PageData pageData)
    {
        List<ColumnData> columnList = new ArrayList<ColumnData>();

        //by default, considers that the first column, is the one that appears first in the list of allSpans grouped by qty
        ColumnData firstColumn = allSpans.get(0).getKey();
        //only columns with width > 50 are considered as columns
        if(firstColumn.getWidth()>50) {
            columnList.add(firstColumn);
        }

//todo: see if by commenting the first page in 2009Jugovic works, also check other docs
//        if(firstColumn.getWidth()>((double)pageData.getWidth())/2.0)
//        {
//            return columnList;
//        }

        for(Entry<ColumnData> colData:allSpans)
        {
            //to be considered as a new column:
            //1- ratio of contiguousCounterpart/qty should be large enough (0.8?)
            //2- If it overlaps, then at leas one border should be equal
            //3- can not overlap more than 1 column
            //4- the qty >=3?
            //5- minimum width of 50?
            if(colData.qty >= 3 && contiguousCounterpartRatio(colData,0.8) &&
                    smartOverlaps(columnList, colData.getKey()) && colData.key.getWidth()>50)
            {
                //add column
                columnList.add(colData.getKey());
                //update the accumulated width and counter of cols
//                widthSoFar =+ colData.getKey().getWidth();
//                //check if continue the loop by checking the accumulatedWidth
//                if(widthSoFar + firstColumn.getWidth() > pageData.getWidth())
//                {
//                    break;
//                }
            }
            if(colData.qty<3)
            {
                break;
            }
        }
        return columnList;
    }


    private static boolean isEqualMargin(int margin1, int margin2, int acceptedErr)
    {
        return (margin1 >= margin2-acceptedErr && margin1 <= margin2+acceptedErr);
    }

    private static boolean smartOverlaps(List<ColumnData> columns, ColumnData columnToCheck)
    {
        int numberOverlapping=0;
        int isSmart= -1;
        for(ColumnData col: columns)
        {
            if((col.getLeftX()>=columnToCheck.getLeftX() && col.getLeftX()<=columnToCheck.getRightX()) ||
                    (col.getRightX()>=columnToCheck.getLeftX() && col.getRightX()<=columnToCheck.getRightX()) ||
                    (col.getLeftX()<=columnToCheck.getLeftX() && col.getRightX()>=columnToCheck.getRightX()) ||
                    (col.getLeftX()>=columnToCheck.getLeftX() && col.getRightX()<=columnToCheck.getRightX()))
            {
                numberOverlapping++;
                if(isEqualMargin(col.getLeftX(),columnToCheck.getLeftX(),2) &&
                        //the other margin must be at least 15% away
                        ((col.getRightX()>columnToCheck.getRightX() &&
                                        col.getRightX()-columnToCheck.getRightX() >= ((double)col.getWidth()) * 0.15) ||
                                (col.getRightX()<columnToCheck.getRightX() &&
                                        columnToCheck.getRightX()-col.getRightX() >= ((double)columnToCheck.getWidth()) * 0.15))
                        )
                {
                    isSmart = isSmart==-1?1:isSmart;
                }
                else if(isEqualMargin(col.getRightX(),columnToCheck.getRightX(),2) &&
                        //the other margin must be at least 15% away
                        ((col.getLeftX()>columnToCheck.getLeftX() &&
                                col.getLeftX()-columnToCheck.getLeftX() >= ((double)columnToCheck.getWidth()) * 0.15) ||
                                (col.getLeftX()<columnToCheck.getLeftX() &&
                                        columnToCheck.getLeftX()-col.getLeftX() >= ((double)col.getWidth()) * 0.15))
                        )
                {
                    isSmart = isSmart==-1?1:isSmart;
                }
                //todo: check if the following else if works:
                //for some documents such as 1999Fey_Synthesis... (this one, when parsed on MAC), some tables are taken as columns first.
                //here, the idea is that as long as it doesnt overlap vertically, it is ok, see if works well
                else if(col.getBottomY()>columnToCheck.getTopY() || columnToCheck.getBottomY()>col.getTopY())
                {
                    isSmart = isSmart==-1?1:isSmart;
                }
                else
                {
                    isSmart = 0;
                }
            }

        }
        if(numberOverlapping==0 || (numberOverlapping==1 && isSmart==1))
        {
            return true;
        }
        return false;
    }

    private static boolean contiguousCounterpartRatio(Entry<ColumnData> columnData, double ratio)
    {
        return ((double)columnData.getKey().getContiguousCounterparts())/((double)columnData.getQty()) >= ratio;
    }
    private static boolean isOverlapping(List<ColumnData> columns, ColumnData columnToCheck)
    {
        for(ColumnData col: columns)
        {
            if((col.getLeftX()>=columnToCheck.getLeftX() && col.getLeftX()<=columnToCheck.getRightX()) ||
                    (col.getRightX()>=columnToCheck.getLeftX() && col.getRightX()<=columnToCheck.getRightX()) ||
                    (col.getLeftX()<=columnToCheck.getLeftX() && col.getRightX()>=columnToCheck.getRightX()) ||
                    (col.getLeftX()>=columnToCheck.getLeftX() && col.getRightX()<=columnToCheck.getRightX()))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean isNearTheTop(LineInfo lineInfo, PageData page, double percentFromTop)
    {
        int diffTops = page.getTopY() - lineInfo.ury;
        if(((double)diffTops)/((double)page.getHeight())<percentFromTop)
        {
            return true;
        }
        return false;
    }

    public static boolean isNearTheTop(LineInfo lineInfo, PageData page, int pixels)
    {
        int diffTops = page.getTopY() - lineInfo.ury;
        if(diffTops<=pixels)
        {
            return true;
        }
        return false;
    }

    public static boolean isNearTheBottom(LineInfo lineInfo, PageData page, int pixels)
    {
        int diffTops = lineInfo.lly - page.getBottomY();
        if(diffTops<=pixels)
        {
            return true;
        }
        return false;
    }

    private static boolean isWidthSimilar(List<ColumnData> columns, ColumnData columnToCheck, double errorRatio)
    {
        for(ColumnData col: columns)
        {
            if(columnToCheck.getWidth() < ((double)col.getWidth())*(1.0-errorRatio) ||
                    columnToCheck.getWidth() > ((double)col.getWidth())*(1.0 + errorRatio))
            {
                return false;
            }
        }
        return true;

    }

    public static void adjustPageData(int urx, int llx, int ury, int lly, int page, Map <Integer, PageData> pagesData)
    {
        PageData pageData = pagesData.get(page);

        if(pageData==null)
        {
            pageData = new PageData();
            pageData.setBottomY(lly);
            pageData.setTopY(ury);
            pageData.setLeftX(llx);
            pageData.setRightX(urx);
            pageData.setPageNumber(page);
            pagesData.put(page,pageData);
        }
        else
        {
            pageData.setBottomY(pageData.getBottomY()>lly?lly:pageData.getBottomY());
            pageData.setTopY(pageData.getTopY()<ury?ury:pageData.getTopY());
            pageData.setLeftX(pageData.getLeftX()>llx?llx:pageData.getLeftX());
            pageData.setRightX(pageData.getRightX()<urx?urx:pageData.getRightX());
        }
    }


    public static void adjustPageData(LineInfo[] lineInfos, int i, Map <Integer, PageData> pagesData)
    {
        adjustPageData(lineInfos[i].urx,lineInfos[i].llx,  lineInfos[i].ury,lineInfos[i].lly, lineInfos[i].page, pagesData );
    }

    public static void adjustLineHeight(LineInfo[] lineInfos, int i, List <Entry<Integer>> lineHeight)
    {
        int height =  getCurrentLineHeight(lineInfos, i); //lineInfos[i].ury - lineInfos[i].lly;
        Entry<Integer> currentHeightEntry = new Entry<Integer>(height,1);
        int iOf = lineHeight.indexOf(currentHeightEntry);
        if(iOf>-1)
        {
            Entry actualData = lineHeight.get(iOf);
            actualData.setQty(actualData.getQty()+1);
        }
        else
        {
            lineHeight.add(currentHeightEntry);
        }
    }

    public static int getCurrentLineHeight(LineInfo[] lineInfos, int i)
    {
        return lineInfos[i].ury - lineInfos[i].lly;
    }

    public static boolean isCentered(LineInfo lineInfo, int columnLeftMargin, int columnRightMargin, int errRatio)
    {
        int leftDiff = lineInfo.llx - columnLeftMargin;
        int rightDiff = columnRightMargin - lineInfo.urx;

        if(
                //only if there is at least 10 px padding on each of the sides
                leftDiff>=10 && rightDiff>=10 &&
                //and if it is centered with the err ratio passed as parameter
                leftDiff >= rightDiff - errRatio && leftDiff <= rightDiff + errRatio)
        {
            return true;
        }
        return false;
    }

    public static int getWordsInDictionary(String lineOfText, EnglishDictionary dictionary, boolean exact)
    {
        String cleanedText = lineOfText.replaceAll("[,\\.\\(\\)\\[\\]]", "").toLowerCase();
        String [] tokenized = cleanedText.split(" ");

        Set<String> tokSet = new HashSet<String>();
        //to avoid counting twice the same word
        for(String word:tokenized)
        {
            tokSet.add(word.trim());
        }
        int cont = 0;
        //at least 3 words are expected in the line
        if(tokenized.length<3&&!exact)
        {
            return -1;
        }
//        for (String word:tokenized)
        for(String word:tokSet)
        {
            if (dictionary.contains(word))
            {
                cont++;
            }
        }
//        if(cont==0)
//        {
//            System.out.println(cleanedText);
//        }
        return cont;
    }
    public static void adjustWordsInDictionaryPerLine(String currentLineText,
                        List <LayoutUtils.Entry<Integer>> wordsInDictionaryPerLine, EnglishDictionary dictionary)
    {
        int dictWordsInLine = getWordsInDictionary(currentLineText, dictionary, false);
        if(dictWordsInLine==-1)
        {
            return;
        //    System.out.println("zero");
        }

        Entry currEntry = new Entry(dictWordsInLine,1 );

        int iOf = wordsInDictionaryPerLine.indexOf(currEntry);
        if(iOf>-1)
        {
            Entry actualData = wordsInDictionaryPerLine.get(iOf);
            actualData.setQty(actualData.getQty()+1);
        }
        else
        {
            wordsInDictionaryPerLine.add(currEntry);
        }
    }

    public static int getPixelsPerCharacter(LineInfo[] lineInfos, int i)
    {
        int width = lineInfos[i].urx - lineInfos[i].llx;
        String text = lineInfos[i].text;
        if (width == 1)
        {
            return -1;
        }
        int pxlsXCharacter = (int) Math.round(((double)width)/((double)text.length()));
        return pxlsXCharacter;
    }
    public static void adjustPixelsPerCharacter(LineInfo[] lineInfos, int i, List <Entry<Integer>> pixelsPerCharacter)
    {
        int pxlsXCharacter = getPixelsPerCharacter(lineInfos, i);

        if(pxlsXCharacter==-1)
        {
            return;
        }

        Entry currEntry = new Entry(pxlsXCharacter,1 );

        int iOf = pixelsPerCharacter.indexOf(currEntry);
        if(iOf>-1)
        {
            Entry actualData = pixelsPerCharacter.get(iOf);
            actualData.setQty(actualData.getQty()+1);
        }
        else
        {
            pixelsPerCharacter.add(currEntry);
        }
    }

    public static void adjustLineWidth(LineInfo[] lineInfos, int i, List <Entry<Integer>> lineWidth)
    {
        int width = getCurrentLineWidth(lineInfos,i);
        //width should be at least 10 px
        if(width<=10)
        {
            return;
        }
        Entry<Integer> currentWidthEntry = new Entry<Integer>(width,1);
        int iOf = lineWidth.indexOf(currentWidthEntry);
        if(iOf>-1)
        {
            Entry actualData = lineWidth.get(iOf);
            actualData.setQty(actualData.getQty()+1);
        }
        else
        {
            lineWidth.add(currentWidthEntry);
        }
    }

    public static int getCurrentLineWidth(LineInfo[] lineInfos, int i)
    {
        return lineInfos[i].urx - lineInfos[i].llx;
    }

    public static void adjustLineWidthPerPage(LineInfo[] lineInfos, int i, Map<Integer, List<Entry<Integer>>> widthLinePerPage)
    {
        LineInfo lineInfo = lineInfos[i];
        List<Entry<Integer>> entry = widthLinePerPage.get(lineInfo.page);

        if(entry == null)
        {
            entry = new ArrayList<Entry<Integer>>();
        }
        adjustLineWidth(lineInfos, i, entry);
        widthLinePerPage.put(lineInfo.page,entry);
    }

    public static int getCurrentVerticalDistance(LineInfo[] lineInfos, int i)
    {
        if (i+1 < lineInfos.length && lineInfos[i].page == lineInfos[i + 1].page && lineInfos[i].lly > lineInfos[i+1].lly) {
            return (lineInfos[i].lly - lineInfos[i + 1].lly);
        }
        return -1;
    }

    public static int getCurrentVerticalDistanceUry(LineInfo[] lineInfos, int i)
    {
        if (i+1 < lineInfos.length && lineInfos[i].page == lineInfos[i + 1].page && lineInfos[i].ury > lineInfos[i+1].ury) {
            return (lineInfos[i].ury - lineInfos[i + 1].ury);
        }
        return -1;
    }

    public static void adjustVerticalDistance(LineInfo[] lineInfos, int i,  List<Entry<Integer>> verticalDistance)
    {

        if (i+1 < lineInfos.length && lineInfos[i].page == lineInfos[i + 1].page && lineInfos[i].lly > lineInfos[i+1].lly) {
            Integer vertDistance = lineInfos[i].lly - lineInfos[i + 1].lly;
            Entry<Integer > initialEntry = new Entry<Integer>(vertDistance,1);
            int iOf = verticalDistance.indexOf(initialEntry);
            if(iOf > -1)
            {
                //verticalDistance.put(vertDistance,verticalDistance.get(vertDistance)+1);
                Entry<Integer> exEntry = verticalDistance.get(iOf);
                exEntry.setQty(exEntry.getQty()+1);
            }
            else
            {
                verticalDistance.add(initialEntry);
            }
        }
    }




    private static ColumnData getColumnData(boolean equalsBothMargins, int acceptableMarginErrorLeft, int acceptableMarginErrorRight,
                                            LineInfo lineInfo)
    {
        ColumnData columnData = new ColumnData(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight);
        columnData.setLeftX(lineInfo.llx);
        columnData.setRightX(lineInfo.urx);
        columnData.setTopY(lineInfo.ury);
        columnData.setBottomY(lineInfo.lly);
        return columnData;
    }
    public static void checkCounterparts(boolean equalsBothMargins, int acceptableMarginErrorLeft, int acceptableMarginErrorRight,
                                         ColumnData columnData, LineInfo[] lineInfos, int i)
    {
        ColumnData columnData1 = null;
        if(i>0)
        {
            columnData1 = getColumnData(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, lineInfos[i-1]);
            if(columnData.equals(columnData1))
            {
                columnData.incrementContiguous();
                return;
            }
        }
        if(i<lineInfos.length-1)
        {
            columnData1 = getColumnData(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, lineInfos[i+1]);
            if(columnData.equals(columnData1))
            {
                columnData.incrementContiguous();
                return;
            }
        }
    }
    public static void adjustColumnData(LineInfo[]lineInfos, int i, Map <Integer, List<Entry<ColumnData>>> columnsData, boolean equalsBothMargins,
                                        int acceptableMarginErrorLeft, int acceptableMarginErrorRight, Span currentSpan)
    {
        //if lots of subindexes, then pstotext fails to calculate the line width accurately, so increase the margin error for column identification
//        if(currentSpan != null && isActiveFeature(currentSpan, "wordsWithSubindex5OrMore") && equalsBothMargins)
//        {
//            acceptableMarginErrorRight += 10;
//        }


        ColumnData columnData = getColumnData(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, lineInfos[i]);

        if(columnsData.get(lineInfos[i].page)==null)
        {
            List <Entry<ColumnData>> colData = new ArrayList<Entry<ColumnData>>();
            colData.add(new Entry<ColumnData>(columnData, 1));

            checkCounterparts(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, columnData, lineInfos, i);

            columnsData.put(lineInfos[i].page,colData);

        }
        else
        {
            Entry<ColumnData> currEntry = new Entry<ColumnData>(columnData,1);
            List entriesInThePage = columnsData.get(lineInfos[i].page);
            int iOe = entriesInThePage.indexOf(currEntry);
            if(iOe>-1)
            {
                Entry<ColumnData> existentEntry = columnsData.get(lineInfos[i].page).get(iOe);
                existentEntry.setQty(existentEntry.getQty()+1);
                if(lineInfos[i].ury>existentEntry.getKey().getTopY()) {
                    existentEntry.getKey().setTopY(lineInfos[i].ury);
                }
                if(lineInfos[i].lly<existentEntry.getKey().getBottomY()) {
                    existentEntry.getKey().setBottomY(lineInfos[i].lly);
                }
                checkCounterparts(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, existentEntry.getKey(), lineInfos, i);
            }
            else
            {
                checkCounterparts(equalsBothMargins, acceptableMarginErrorLeft, acceptableMarginErrorRight, currEntry.getKey(), lineInfos, i);
                columnsData.get(lineInfos[i].page).add(currEntry);
            }
        }
    }



    public static class Entry<T1> implements Comparable<Entry<T1>>
    {
        T1 key;
        Integer qty;

        public Entry(T1 key, Integer qty)
        {
            this.key = key;
            this.qty = qty;
        }

        public Integer getQty() {
            return qty;
        }

        public void setQty(Integer qty) {
            this.qty = qty;
        }

        public T1 getKey() {
            return key;
        }

        public void setKey(T1 key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object obj)
        {
            return ((Entry)obj).getKey().equals(this.key);
        }

        @Override
        public int hashCode()
        {
            return key.hashCode();
        }

        @Override
        public int compareTo(Entry<T1> entry)
        {
            int otherQty = entry.getQty();
            if(this.qty > otherQty)
            {
                return -1;
            }
            else if (this.qty == otherQty)
            {
                return 0;
            }
            else
            {
                return 1;
            }
        }
    }


    public static class ColumnData
    {

        private int topY=-1;
        private int bottomY=-1;

        private int leftX=-1;
        private int rightX=-1;
        boolean equalsBothMargins = false;

        //accepted margin of error (in pixels) when performing equals
        int errorMarginLeft = 0;
        int errorMarginRight = 0;

        public int getErrorMarginLeft()
        {
            return errorMarginLeft;
        }
        public int getErrorMarginRight()
        {
            return errorMarginRight;
        }
        //only used when ColumnData is used as a key to group , to indicate the number of
        //cases when the same-width column lines have contiguous counterpart.
        //It is used to detect lines such as those tabbed to the right that have the same width and leftX and leftY pos
        //but are still are not representative to be considered as a different column
        int contiguousCounterparts = 0;

        public void incrementContiguous()
        {
            contiguousCounterparts++;
        }

        public int getContiguousCounterparts()
        {
            return contiguousCounterparts;
        }

        public ColumnData()
        {

        }


        public ColumnData(boolean equalsBothMargins, int errorMarginLeft, int errorMarginRight)
        {
            this.equalsBothMargins = equalsBothMargins;
            this.errorMarginLeft = errorMarginLeft;
            this.errorMarginRight = errorMarginRight;
        }


        public boolean isInitialized()
        {
            return !(topY==-1 && bottomY==-1 && leftX == -1 && rightX == -1);
        }
        public boolean isEqualsBothMargins() {
            return equalsBothMargins;
        }

        public void setEqualsBothMargins(boolean equalsBothMargins) {
            this.equalsBothMargins = equalsBothMargins;
        }
        public int getTopY() {
            return topY;
        }

        public void setTopY(int topY) {
            this.topY = topY;
        }

        public int getBottomY() {
            return bottomY;
        }

        public void setBottomY(int bottomY) {
            this.bottomY = bottomY;
        }

        public int getLeftX() {
            return leftX;
        }

        public void setLeftX(int leftX) {
            this.leftX = leftX;
        }

        public int getRightX() {
            return rightX;
        }

        public void setRightX(int rightX) {
            this.rightX = rightX;
        }

        public int getWidth() {
            return (rightX - leftX);
        }

        @Override
        public boolean equals(Object obj )
        {
            if(!equalsBothMargins) {
                return //((ColumnData)obj).rightX == this.rightX &&
                        ((ColumnData) obj).leftX == this.leftX;
            }
            else
            {
                return (((ColumnData)obj).rightX == this.rightX &&
                        ((ColumnData) obj).leftX == this.leftX) ||
                        (((ColumnData)obj).rightX == this.rightX &&
                                (((ColumnData) obj).leftX >= this.leftX - errorMarginLeft &&
                                        ((ColumnData) obj).leftX <= this.leftX + errorMarginLeft)) ||
                        ((((ColumnData) obj).rightX >= this.rightX - errorMarginRight &&
                                ((ColumnData) obj).rightX <= this.rightX + errorMarginRight) &&
//                                ((ColumnData) obj).leftX == this.leftX
                        //todo: manually added +/- 1px for papers such as page 2 of 2014ShenFacile..., if works well, parametrize
                                (((ColumnData) obj).leftX >= this.leftX-1 && ((ColumnData) obj).leftX <= this.leftX+1)
                        );
            }
        }

        @Override
        public int hashCode()
        {
            return Integer.valueOf(leftX).hashCode();
        }
    }


    public static class PageData
    {

        private int topY;
        private int bottomY;

        private int leftX;
        private int rightX;

        private int pageNumber;

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public int getTopY() {
            return topY;
        }

        public void setTopY(int topY) {
            this.topY = topY;
        }

        public int getBottomY() {
            return bottomY;
        }

        public void setBottomY(int bottomY) {
            this.bottomY = bottomY;
        }

        public int getLeftX() {
            return leftX;
        }

        public void setLeftX(int leftX) {
            this.leftX = leftX;
        }

        public int getRightX() {
            return rightX;
        }

        public void setRightX(int rightX) {
            this.rightX = rightX;
        }

        public int getWidth() {
            return (rightX - leftX);
        }
        public int getHeight(){
            return (topY - bottomY);
        }

    }
}

package org.rexo.pipeline.extractors;

import edu.umass.cs.mallet.base.extract.Span;
import edu.umass.cs.mallet.base.extract.StringSpan;
import edu.umass.cs.mallet.base.fst.Transducer;
import edu.umass.cs.mallet.base.types.Sequence;
import edu.umass.cs.mallet.base.types.Token;
import edu.umass.cs.mallet.base.types.TokenSequence;
import edu.umass.cs.rexo.ghuang.segmentation.utils.LayoutUtils;
import org.rexo.extraction.NewHtmlTokenization;
import org.rexo.span.CompositeSpan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by klimzaporojets on 8/4/14.
 */
public class BodyRulesTransducer  {

    //class representing the properties of a particular section marker
    class SectionMarker
    {
        private boolean isAllCaps;

    }


    public Sequence transduce(NewHtmlTokenization data)
    {


        Sequence transducedData = new TokenSequence();
        boolean currentlyInColumn = false;
        int tokenId = 0;

        boolean previousSectionMarker = false;
        String figureOrTableMarker = "";

        List<String> featuresTableContent = new ArrayList<String>();
        featuresTableContent.add("pixelsPerCharacter2pxGreater");
        featuresTableContent.add("pixelsPerCharacterUndefined");
        featuresTableContent.add("noWordsFromDictionary");
        featuresTableContent.add("3wordFromDictLess");

        List<String> relaxedFeaturesTableContent = new ArrayList<String>();
        relaxedFeaturesTableContent.add("pixelsPerCharacter2pxGreater");
        relaxedFeaturesTableContent.add("noWordsFromDictionary");
        relaxedFeaturesTableContent.add("oneWordFromDictionary");


        boolean previousFigure = false;
        boolean debugMe  = false;
        for(int i=0; i<data.getLineSpans().size(); i++)
        {
            previousFigure = false;
            String label = "";
            Span previousSpan = i>0?(Span)data.getLineSpans().get(i-1):null;
            Span currentSpan = (Span)data.getLineSpans().get(i);
            if(currentSpan==null)
            {
                continue;
            }
            Span nextSpan = i<data.getLineSpans().size()-1?(Span)data.getLineSpans().get(i+1):null;
          //  boolean isNoCollumnAssociated = LayoutUtils.isActiveFeature(currentSpan,"noColumnAssociated");
            if(!debugMe)
            {
                debugMe = currentSpan instanceof CompositeSpan && ((Double)((CompositeSpan) currentSpan).getProperty("pageNum")) == 3.0;
            }

            if((((LayoutUtils.isActiveFeature(currentSpan, "firstLevelSectionPtrn") || LayoutUtils.isActiveFeature(currentSpan, "secondLevelSectionPtrn") ||
                    LayoutUtils.isActiveFeature(currentSpan, "thirdLevelSectionPtrn")
                    ||
                    LayoutUtils.isActiveFeature(currentSpan, "allCaps") /*in some papers the titles are in caps*/
                    ||
                    (LayoutUtils.isActiveFeature(currentSpan, "centeredLine"))
                    )
                    && (!LayoutUtils.isActiveFeature(currentSpan, "noColumnAssociated")
                            || (LayoutUtils.isActiveFeature(currentSpan, "noColumnAssociated") &&
                            nextSpan != null && !LayoutUtils.isActiveFeature(nextSpan, "newColumn") &&
                                !LayoutUtils.isActiveFeature(nextSpan, "newPage") &&
                                !LayoutUtils.isActiveFeature(nextSpan, "noColumnAssociated"))
                        ) &&
                    ( (LayoutUtils.isActiveFeature(currentSpan, "verticalDistance2pxGreater") && LayoutUtils.isActiveFeature(currentSpan, "verticalDistanceUry2pxGreater")) || /* with "verticalDistance4pxGreater" doesn't work on INTRODUCTION section
                                    of 2014W%F6hlertSynthesis,_Structures paper*/
                            (previousSpan!=null && LayoutUtils.isActiveFeature(previousSpan, "verticalDistance4pxGreater")) ||
                            (LayoutUtils.isActiveFeature(currentSpan, "lineHeight2pxGreater") && LayoutUtils.isActiveFeature(currentSpan, "verticalDistanceUry2pxGreater") && LayoutUtils.isActiveFeature(currentSpan, "verticalDistance2pxGreater"))
                    ) &&
                    (!LayoutUtils.isActiveFeature(currentSpan, "endsInDot") /*&& in papers such as  2010Song_REcent_p... it makes the section markers to be ignored
                                    LayoutUtils.isActiveFeature(currentSpan, "rightMarginToTheLeft")*/)

                    )
                    /*sometimes doesn't work because pstotext ommits sentences*/
                    /* (previousSpan == null || (LayoutUtils.isActiveFeature(previousSpan, "endsInDot") && !previousSectionMarker) || previousSectionMarker )*/
                    ||
                    (previousSectionMarker && LayoutUtils.isActiveFeature(currentSpan, "verticalDistance2pxGreater") &&
                            LayoutUtils.isActiveFeature(currentSpan, "verticalDistanceUry2pxGreater")) /* in case the section marker has several lines*/


            )   //any section has to have alphabetic characters
                && (!LayoutUtils.isActiveFeature(currentSpan, "noAlphabetic")))
            {
                if (LayoutUtils.isActiveFeature(currentSpan, "firstLevelSectionPtrn") || LayoutUtils.isActiveFeature(currentSpan, "secondLevelSectionPtrn") ||
                        LayoutUtils.isActiveFeature(currentSpan, "thirdLevelSectionPtrn"))
                {
                    label = "section-marker-begin";
                }
                else
                {
                    label = "section-marker-inside";
                }
            }

            if(label.equals("") && LayoutUtils.isActiveFeature(currentSpan, "noColumnAssociated") &&
                    (LayoutUtils.isActiveFeature(currentSpan, "newPage") ||
                            LayoutUtils.isActiveFeature(currentSpan, "newColumn") ||
                            LayoutUtils.isActiveFeature(currentSpan, "upAndToTheLeft")
                                        || (previousSpan==null) ||
                            LayoutUtils.isActiveFeature(previousSpan, "verticalDistance2pxGreater") ||
                            LayoutUtils.isActiveFeature(previousSpan, "noColumnAssociated") ||  //&&
                            LayoutUtils.isActiveFeature(currentSpan, "columnLayoutChange") ||
                            LayoutUtils.isActiveFeature(currentSpan, "lineHeight30pxGreater")
                    ))
            {
                label = "notext";
            }
            else if(label.equals(""))
            {
                label = "text-inside";
            }

            if(!label.contains("section-marker") && LayoutUtils.isActiveFeature(currentSpan,"columnLayoutChange") /*&& !(previousSpan!=null && LayoutUtils.isActiveFeature(previousSpan,"columnLayoutChange"))*/)
            {
                label = "text-begin";
            }


            if((figureOrTableMarker.contains("table-marker") && ( (LayoutUtils.isActiveFeature(previousSpan, "verticalDistance2pxGreater") &&
                                     LayoutUtils.isActiveFeature(previousSpan, "verticalDistanceUry2pxGreater") &&
                                    !LayoutUtils.isActiveFeature(currentSpan, "pixelsPerCharacter2pxGreater") &&
                                    !LayoutUtils.isActiveFeature(currentSpan, "pixelsPerCharacterUndefined") &&
                                    !LayoutUtils.isActiveFeature(currentSpan, "noWordsFromDictionary") &&
                                    !LayoutUtils.isActiveFeature(currentSpan, "3wordFromDictLess") &&
                                    !(LayoutUtils.isActiveFeature(currentSpan, "rightMarginToTheLeft") && !LayoutUtils.isActiveFeature(currentSpan, "endsInDot"))
            ) ||

/*                                LayoutUtils.isActiveFeature(currentSpan, "up") ||*/
                                  LayoutUtils.isActiveFeature(currentSpan, "up20PxGreater") ||
                                    //todo: test well on other documents
                                  LayoutUtils.isActiveFeature(currentSpan, "lineHeight10pxGreater") ||

                                /*LayoutUtils.isActiveFeature(currentSpan, "right") ||*/
                                LayoutUtils.isActiveFeature(currentSpan, "newPage") )))
            {
                figureOrTableMarker = "";
            }


            if((figureOrTableMarker.contains("figure-marker") && ( (LayoutUtils.isActiveFeature(previousSpan, "verticalDistance2pxGreater") &&
                    LayoutUtils.isActiveFeature(previousSpan, "verticalDistanceUry2pxGreater") &&
                    !(LayoutUtils.isActiveFeature(currentSpan, "rightMarginToTheLeft") && !LayoutUtils.isActiveFeature(currentSpan, "endsInDot"))
            ) ||

/*                                LayoutUtils.isActiveFeature(currentSpan, "up") ||*/
                    LayoutUtils.isActiveFeature(currentSpan, "up20PxGreater") ||
                    //todo: test well on other documents
                    LayoutUtils.isActiveFeature(currentSpan, "lineHeight10pxGreater") ||

                                /*LayoutUtils.isActiveFeature(currentSpan, "right") ||*/
                    LayoutUtils.isActiveFeature(currentSpan, "newPage") )))
            {
                figureOrTableMarker = "";
                previousFigure=true;
            }


            if(!figureOrTableMarker.equals("") &&
                    (!LayoutUtils.isActiveFeature(previousSpan, "verticalDistance2pxGreater") ||
                            !LayoutUtils.isActiveFeature(previousSpan, "verticalDistanceUry2pxGreater") ||
                            (LayoutUtils.isActiveFeature(currentSpan, "pixelsPerCharacter2pxGreater")  && figureOrTableMarker.contains("table-marker")) ||
                            (LayoutUtils.isActiveFeature(currentSpan, "pixelsPerCharacterUndefined")  && figureOrTableMarker.contains("table-marker")) ||
                            (LayoutUtils.isActiveFeature(currentSpan, "noWordsFromDictionary")  && figureOrTableMarker.contains("table-marker")) ||
                            (LayoutUtils.isActiveFeature(currentSpan, "3wordFromDictLess") && figureOrTableMarker.contains("table-marker") ) ||
                            (LayoutUtils.isActiveFeature(currentSpan, "rightMarginToTheLeft") && !LayoutUtils.isActiveFeature(currentSpan, "endsInDot"))
                    ))
            {
                label = figureOrTableMarker;
            }
            boolean futureLayout = LayoutUtils.isAnyOfFeaturesInFuture(data, i, relaxedFeaturesTableContent, 3, 10);
            if(LayoutUtils.isActiveFeature(currentSpan, "startsTableWord") && (LayoutUtils.isActiveFeature(currentSpan, "upAndToTheLeft") //

             ||
                    (LayoutUtils.isActiveFeature(currentSpan, "up") && futureLayout) ||
                    (LayoutUtils.isActiveFeature(currentSpan, "up")
                                && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop"))
             ||
                    (LayoutUtils.isActiveFeature(currentSpan, "right") && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop"))
             ||
                    (previousSpan != null && LayoutUtils.isActiveFeature(previousSpan, "verticalDistance100pxGreater") && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop") && !!LayoutUtils.isActiveFeature(currentSpan, "newPage"))
             ||     (previousSpan != null && LayoutUtils.isActiveFeature(previousSpan, "verticalDistance2pxGreater") && futureLayout && !LayoutUtils.isActiveFeature(currentSpan, "newPage"))
            ))
            {
                label = "table-marker-begin";
                figureOrTableMarker = "table-marker-inside";
            }


            boolean futureFigureLayout = LayoutUtils.isFigureInTheFuture(data, i,
                    LayoutUtils.isActiveFeature(currentSpan,"lineWidth20pxLess")?15:6);

            if(LayoutUtils.isActiveFeature(currentSpan, "startsFigureWord") && (LayoutUtils.isActiveFeature(currentSpan, "upAndToTheLeft")
                    //todo: work on it
                     ||
                    (LayoutUtils.isActiveFeature(currentSpan, "up") && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop"))
                    ||
                    (LayoutUtils.isActiveFeature(currentSpan, "right") && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop"))
                    ||
                    (previousSpan != null && LayoutUtils.isActiveFeature(previousSpan, "verticalDistance100pxGreater") && !LayoutUtils.isActiveFeature(currentSpan, "nearThe150PxOfTop"))
                    ||
                    futureFigureLayout
                    || (previousFigure && LayoutUtils.isActiveFeature(currentSpan,"lineWidth20pxLess") && previousSpan != null && LayoutUtils.isActiveFeature(previousSpan, "verticalDistance12pxGreater"))

                    ))
            {
                label = "figure-marker-begin";
                figureOrTableMarker = "figure-marker-inside";
            }




            tokenId = addLabelToAllSpans(currentSpan, label, (TokenSequence)transducedData, data,tokenId);

            if(label.equals("section-marker-begin") || label.equals("section-marker-inside"))
            {
                previousSectionMarker = true;
            }
            else
            {
                previousSectionMarker = false;
            }
        }

        return transducedData;
    }


    public int addLabelToAllSpans(Span span, String label, TokenSequence transducedData, NewHtmlTokenization data,
                                   int tokenId)
    {

        span.getEndIdx();
        Span tok = null;
//        Token tkn = data.getToken(1);
//here add to check "column-inside"
        while(tokenId< data.size() &&  (tok = data.getSpan(tokenId) ) .getEndIdx()<=span.getEndIdx() )
        {
            transducedData.add(label);
            ++tokenId;
            if(label.equals("text-begin"))
            {
                label = "text-inside";
            }
            if(label.equals("section-marker-begin"))
            {
                label = "section-marker-inside";
            }
            if(label.equals("figure-marker-begin"))
            {
                label = "figure-marker-inside";
            }
            if(label.equals("table-marker-begin"))
            {
                label = "table-marker-inside";
            }
        }
        return tokenId;

//        if(span instanceof CompositeSpan)
//        {
//            for(int i =0; i<((CompositeSpan)span).getSpans().size(); i++)
//            {
//
//                transducedData.add(label);
//            }
//        }
//        else
//        {
//            transducedData.add(label);
//        }
//        return lastTokenId;
    }
}

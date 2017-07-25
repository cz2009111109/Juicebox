/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2017 Broad Institute, Aiden Lab
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package juicebox.assembly;

import juicebox.HiCGlobals;
import juicebox.data.Block;
import juicebox.data.ContactRecord;
import juicebox.data.MatrixZoomData;
import juicebox.gui.SuperAdapter;
import juicebox.mapcolorui.Feature2DHandler;
import juicebox.track.feature.Contig2D;
import juicebox.track.feature.Feature2D;
import juicebox.track.feature.Feature2DList;
import juicebox.windowui.HiCZoom;
import juicebox.windowui.NormalizationType;
import org.broad.igv.feature.Chromosome;

import javax.swing.*;
import java.util.*;

/**
 * Created by muhammadsaadshamim on 4/17/17.
 */
public class AssemblyHeatmapHandler {

    private static SuperAdapter superAdapter;

    public static void makeChanges(String[] encodedInstructions, SuperAdapter superAdapter) {

        AssemblyHeatmapHandler.superAdapter = superAdapter;
        Feature2DList features = superAdapter.getMainLayer().getAnnotationLayer().getFeatureHandler()
                .getAllVisibleLoops();
        makeAssemblyChanges(features, superAdapter.getHiC().getXContext().getChromosome(), encodedInstructions);
        superAdapter.getContigLayer().getAnnotationLayer().getFeatureHandler().remakeRTree();
        HiCGlobals.assemblyModeEnabled = true;
        //superAdapter.getHiC().clearMatrixZoomDataCache();
        superAdapter.refresh();
    }

    private static void makeAssemblyChanges(Feature2DList features, Chromosome chromosome, String[] encodedInstructions) {
        final String key = Feature2DList.getKey(chromosome, chromosome);

        features.convertFeaturesToContigs(key);
        List<Feature2D> contigs = features.get(key);

        for (String instruction : encodedInstructions) {
            if (instruction.startsWith("-")) {
                parseInversionInstruction(contigs, instruction);
            } else if (instruction.contains("->")) {
                parseTranslationInstruction(contigs, instruction);
            } else {
                showInvalidInstructionErrorMessage(instruction);
            }
        }
        recalculateAllAlterations(contigs);
    }

    private static void parseInversionInstruction(List<Feature2D> contigs, String instruction) {
        String reformattedInstruction = instruction;
        if (!(reformattedInstruction.contains(":"))) {
            reformattedInstruction = reformattedInstruction.concat(":")
                    .concat(reformattedInstruction);
        }
        String[] contigIndices = reformattedInstruction.split(":");
        String startIndexString = contigIndices[0];
        String endIndexString = contigIndices[1];
        if (!(isNumeric(startIndexString) && isNumeric(endIndexString))) {
            showInvalidInstructionErrorMessage(instruction);
            return;
        }
        Integer startIndex = Math.abs(Integer.parseInt(startIndexString));
        Integer endIndex = Math.abs(Integer.parseInt(endIndexString));
        invertMultipleContiguousEntriesAt(contigs, startIndex, endIndex);
    }

    private static void parseTranslationInstruction(List<Feature2D> contigs, String instruction) {
        String[] indices = instruction.split("->");
        if (!(isNumeric(indices[0]) && isNumeric(indices[1]))) {
            showInvalidInstructionErrorMessage(instruction);
            return;
        }
        int currentIndex = Integer.parseInt(indices[0]);
        int newIndex = Integer.parseInt(indices[1]);
        moveFeatureToNewIndex(contigs, currentIndex, newIndex);
    }

    private static void invertSingleEntryAt(List<Feature2D> contigs, int index) {
        if (!(index >= 0 && index < contigs.size())) {
            return;
        }
        ((Contig2D) contigs.get(index)).toggleInversion();
    }

    public static void invertMultipleContiguousEntriesAt(List<Feature2D> contigs, int startIndex, int endIndex) {
        // Invert each of the sub-contigs
        for (int currentIndex = startIndex; currentIndex <= endIndex; currentIndex++) {
            invertSingleEntryAt(contigs, currentIndex);
        }
        // Reverse the order of the sub-contigs
        for (int currentIndex = startIndex; currentIndex < (startIndex + endIndex) / 2.0; currentIndex++) {
            moveFeatureToNewIndex(contigs, currentIndex, startIndex + endIndex - currentIndex);
            moveFeatureToNewIndex(contigs, startIndex + endIndex - currentIndex - 1, currentIndex);
        }
    }

    public static void invertMultipleContiguousEntriesAt(List<Feature2D> selectedFeatures, List<Feature2D> contigs, int startIndex, int endIndex) {


        // Invert each of the sub-contigs
        for (int currentIndex = startIndex; currentIndex <= endIndex; currentIndex++) {
            invertSingleEntryAt(contigs, currentIndex);
        }

        // Reverse the order of the sub-contigs
        for (int currentIndex = startIndex; currentIndex < (startIndex + endIndex) / 2.0; currentIndex++) {
            moveFeatureToNewIndex(contigs, currentIndex, startIndex + endIndex - currentIndex);
            moveFeatureToNewIndex(contigs, startIndex + endIndex - currentIndex - 1, currentIndex);
        }

    }


    public static void moveFeatureToNewIndex(List<Feature2D> contigs, int currentIndex, int newIndex) {
        // http://stackoverflow.com/questions/4938626/moving-items-around-in-an-arraylist
        if (!((currentIndex >= 0 && currentIndex < contigs.size()) && (newIndex >= 0 && newIndex < contigs.size()))) {
            return;
        }
        Feature2D item = contigs.remove(currentIndex);
        contigs.add(newIndex, item);
    }

    private static boolean isNumeric(String s) {
        String numericRegularExpression = "[-+]?\\d*\\.?\\d+";
        return s != null && s.matches(numericRegularExpression);
    }

    private static void showInvalidInstructionErrorMessage(String instruction) {
        JOptionPane.showMessageDialog(superAdapter.getMainWindow(), "Invalid command could not be processed: \""
                + instruction + "\"", "Error Message", JOptionPane.ERROR_MESSAGE);
    }

    public static void recalculateAllAlterations(List<Feature2D> contigs) {
        int i = 0;
        for (Feature2D feature2D : contigs) {
            Contig2D contig2D = feature2D.toContig();
            i = contig2D.setNewStart(i);
        }
    }

    public static List<Contig2D> retrieveRelevantBlocks(MatrixZoomData mzd, List<Integer> blocksToLoad,
                                                        List<Block> blockList, Chromosome chr1, Chromosome chr2,
                                                        int binX1, int binY1, int binX2, int binY2, int blockBinCount,
                                                        HiCZoom zoom, NormalizationType no) {
        Feature2DHandler handler = superAdapter.getMainLayer().getAnnotationLayer().getFeatureHandler();
        net.sf.jsi.Rectangle currentWindow = new net.sf.jsi.Rectangle(binX1 * zoom.getBinSize(),
                binY1 * zoom.getBinSize(), binX2 * zoom.getBinSize(), binY2 * zoom.getBinSize());
        handler.getContainedFeatures(chr1.getIndex(), chr2.getIndex(), currentWindow);

        List<Feature2D> intersectingFeatures = handler.getIntersectingFeatures(chr1.getIndex(), chr2.getIndex(), currentWindow);
        List<Contig2D> contigs = new ArrayList<>();
        for (Feature2D feature2D : intersectingFeatures) {
            contigs.add(feature2D.toContig());
        }
//        System.out.println(contigs.size());
        Collections.sort(contigs);

        /* Testing for Tile issue  */
//        contigs = mergeRedundantContiguousContigs(new ArrayList<>(new HashSet(contigs)));

//        Set<Pair<Integer,Integer>> tilesToLoad = new HashSet<Pair<Integer,Integer>>();

//        System.out.println(contigs.size());

        /*
        for (int i=0 ;i<contigs.size();i++) {

            for (int j=i; j<contigs.size();j++) {
                int cStart1 = contigs.get(i).getStart1() / zoom.getBinSize() / blockBinCount;
                int cEnd1 = contigs.get(i).getEnd1() / zoom.getBinSize() / blockBinCount;
                int cStart2 = contigs.get(j).getStart1() / zoom.getBinSize() / blockBinCount;
                int cEnd2 = contigs.get(j).getEnd1() / zoom.getBinSize() / blockBinCount;

                for (int r = cStart1; r <= cEnd1; r++) {
                    for (int c = cStart2; c <= cEnd2; c++) {
//                        System.out.println(r+" "+c);
                        tilesToLoad.add(new Pair<Integer,Integer>(r,c));
//                        mzd.populateBlocksToLoad(r, c, no, blockList, blocksToLoad);
                    }
                }
            }
        }
//        List<Pair<Integer,Integer>> tempList= new ArrayList<Pair<Integer,Integer>>(tilesToLoad);
        for(Pair<Integer,Integer> tileToLoad : tilesToLoad){
            System.out.println(tileToLoad.getFirst()+" "+tileToLoad.getSecond());
            mzd.populateBlocksToLoad(tileToLoad.getFirst(), tileToLoad.getSecond(), no, blockList, blocksToLoad);
        }
//        */

        return contigs;
    }

    /**
     * @param preMergeContigs
     * @param blockList
     * @param binSize
     * @return
     */
    public static List<Block> filterBlockList(List<Contig2D> preMergeContigs, Set<Block> blockList, int binSize) {
        List<Contig2D> contigs = mergeRedundantContiguousContigs(preMergeContigs);

        List<Block> alteredBlockList = new ArrayList<>();
        if (contigs.size() < 1) return alteredBlockList;

        for (Block block : blockList) {
            List<ContactRecord> alteredContacts = new ArrayList<>();
            for (ContactRecord record : block.getContactRecords()) {
                boolean includeXRecord = false;
                boolean includeYRecord = false;
                int aX = -1, aY = -1;

                int genomeX = record.getBinX() * binSize;
                int genomeY = record.getBinY() * binSize;

                for (Contig2D contig : contigs) {
                    //System.out.println("contig "+contig);

                    if (contig.hasSomeOriginalOverlapWith(genomeX)) {
                        includeXRecord = true;
                        aX = contig.getAlteredBinIndex(record.getBinX(), binSize);
                        //System.out.println("axed "+record.getBinX()+" "+binSize+" "+aX);
                    }

                    if (contig.hasSomeOriginalOverlapWith(genomeY)) {
                        includeYRecord = true;
                        aY = contig.getAlteredBinIndex(record.getBinY(), binSize);
                        //System.out.println("ayed "+record.getBinY()+" "+binSize+" "+aY);
                    }

                    if (includeXRecord && includeYRecord) {
                        //System.out.println("altered ax and ay ");
                        if (aX > aY) {
                            alteredContacts.add(new ContactRecord(aY, aX, record.getCounts()));
                        } else {
                            alteredContacts.add(new ContactRecord(aX, aY, record.getCounts()));
                        }
                        break;
                    }
                }
            }
            alteredBlockList.add(new Block(block.getNumber(), alteredContacts));
        }
        //System.out.println("num alters "+alteredBlockList.size());
        return alteredBlockList;
    }


    /**
     * if neighboring contigs are not inverted, share original continuity
     * and share current continuity, they can essentially be merged
     * this will reduce the number of contigs, and improve speed
     *
     * @param currentContigs
     * @return mergedContigs
     */
    private static List<Contig2D> mergeRedundantContiguousContigs(List<Contig2D> currentContigs) {

        List<Contig2D> mergedContigs = new ArrayList<>();
        Contig2D growingContig = null;

        for (Contig2D contig : currentContigs) {
            if (growingContig == null) {
                growingContig = contig.deepCopy().toContig();
                continue;
            } else {
                Contig2D result = growingContig.mergeContigs(contig);
                if (result == null) {
                    // cannot be merged
                    if (growingContig != null) mergedContigs.add(growingContig);
                    growingContig = contig.deepCopy().toContig();
                    continue;
                } else {
                    growingContig = result;
                }
            }
        }
        if (growingContig != null) mergedContigs.add(growingContig);

        return new ArrayList<>(new HashSet<>(mergedContigs));
    }

    public static SuperAdapter getSuperAdapter() {
        return AssemblyHeatmapHandler.superAdapter;
    }

    public static void setSuperAdapter(SuperAdapter superAdapter) {
        AssemblyHeatmapHandler.superAdapter = superAdapter;
    }

    public static Set<Block> modifyBlockList(Set<Block> blockList) {
        int binSize = superAdapter.getHiC().getZoom().getBinSize();
        List<Block> alteredBlockList = new ArrayList<>();
        int chr1Idx = superAdapter.getHiC().getXContext().getChromosome().getIndex();
        int chr2Idx = superAdapter.getHiC().getYContext().getChromosome().getIndex();
        for (Block block : blockList) {
//TODO: do some filtering here
            List<ContactRecord> alteredContacts = new ArrayList<>();
            for (ContactRecord record : block.getContactRecords()) {
                int originalGenomeX = record.getBinX() * binSize + 1;
                int originalGenomeY = record.getBinY() * binSize + 1;
                int currentGenomeX = superAdapter.getAssemblyStateTracker().getAssemblyHandler().
                        liftFragmentCoordinateToAsmCoordinate(
                                superAdapter.getAssemblyStateTracker().getAssemblyHandler().liftOriginalAsmCoordinateToFragment(chr1Idx, chr2Idx, originalGenomeX),
                                superAdapter.getAssemblyStateTracker().getAssemblyHandler().liftOriginalAsmCoordinateToFragmentCoordinate(chr1Idx, chr2Idx, originalGenomeX)
                        );
                int currentGenomeY = superAdapter.getAssemblyStateTracker().getAssemblyHandler().
                        liftFragmentCoordinateToAsmCoordinate(
                                superAdapter.getAssemblyStateTracker().getAssemblyHandler().liftOriginalAsmCoordinateToFragment(chr1Idx, chr2Idx, originalGenomeY),
                                superAdapter.getAssemblyStateTracker().getAssemblyHandler().liftOriginalAsmCoordinateToFragmentCoordinate(chr1Idx, chr2Idx, originalGenomeY)
                        );
                //System.out.println("original record: "+originalGenomeX+" "+originalGenomeY);
                //System.out.println("current record: "+currentGenomeX+" "+currentGenomeY);

                if (currentGenomeX == -1 || currentGenomeY == -1) {
                    alteredContacts.add(record);
                } else {
                    if (currentGenomeX > currentGenomeY) {
                        alteredContacts.add(new ContactRecord((currentGenomeY - 1) / binSize, (currentGenomeX - 1) / binSize, record.getCounts()));

                    } else {
                        alteredContacts.add(new ContactRecord((currentGenomeX - 1) / binSize, (currentGenomeY - 1) / binSize, record.getCounts()));
                    }
                }
            }
            alteredBlockList.add(new Block(block.getNumber(), alteredContacts));
        }
        Set<Block> alteredBlockSet = new HashSet<>(alteredBlockList);
        return alteredBlockSet;
    }
}

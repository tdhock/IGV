/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */
package org.broad.igv.sam;

//~--- non-JDK imports --------------------------------------------------------

import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMReadGroupRecord;
import net.sf.samtools.SAMRecord;
import org.apache.log4j.Logger;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.track.WindowFunction;
import org.broad.igv.ui.color.ColorUtilities;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author jrobinso
 */
public class SamAlignment extends AbstractAlignment implements Alignment {

    private static Logger log = Logger.getLogger(SamAlignment.class);

    public static final char DELETE_CHAR = '-';
    public static final char SKIP_CHAR = '=';
    public static final char MATCH = 'M';
    public static final char PERFECT_MATCH = '=';
    public static final char MISMATCH = 'X';
    public static final char INSERTION = 'I';
    public static final char DELETION = 'D';
    public static final char SKIPPED_REGION = 'N';
    public static final char SOFT_CLIP = 'S';
    public static final char HARD_CLIP = 'H';
    public static final char PADDING = 'P';
    public static final char ZERO_GAP = 'O';

    private static final String FLOW_SIGNAL_TAG = "ZF";
    private int start;  // <= Might differ from alignment start if soft clipping is considered
    private int end;    // ditto
    private int alignmentStart;
    private int alignmentEnd;

    /**
     * Picard object upon which this SamAlignment is based
     */
    private SAMRecord record;
    private String mateSequence = null;
    private String pairOrientation = "";
    private Color color = null;
    private String readGroup;
    private String library;
    private String sample;

    private Strand firstOfPairStrand;
    private Strand secondOfPairStrand;

    /**
     * Converts a DNA integer value to its reverse compliment integer value.
     */
    protected static final char NT2COMP[] = {
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'T', 'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'A', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'T', 'N', 'G', 'N', 'N', 'N', 'C', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'A', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N',
            'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N'
    };

    public static final String REDUCE_READS_TAG = "RR";

    public SamAlignment(SAMRecord record) {
        String keySequence = null;

        this.record = record;

        String refName = record.getReferenceName();
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        this.chr = genome == null ? refName : genome.getChromosomeAlias(refName);

        // SAMRecord is 1 based inclusive.  IGV is 0 based exclusive.
        this.alignmentStart = record.getAlignmentStart() - 1;
        this.start = this.alignmentStart;   // might be modified later for soft clipping
        this.alignmentEnd = Math.max(alignmentStart, record.getAlignmentEnd());
        this.end = alignmentEnd;   // might be modified later for soft clipping
        this.setMappingQuality(record.getMappingQuality());
        this.readName = record.getReadName().trim();
        this.setInferredInsertSize(record.getInferredInsertSize());

        setMatePair(genome);
        setPairOrientation();
        setPairStrands();

        SAMFileHeader header = record.getHeader();
        String flowOrder = null;
        if (header != null) {
            readGroup = (String) record.getAttribute("RG");
            if (readGroup != null) {
                SAMReadGroupRecord rgRec = header.getReadGroup(readGroup);
                if (rgRec != null) {
                    this.sample = rgRec.getSample();
                    this.library = rgRec.getLibrary();
                    flowOrder = rgRec.getFlowOrder();
                    keySequence = rgRec.getKeySequence();
                }
            }
        }

        createAlignmentBlocks(record.getCigarString(), record.getReadBases(), record.getBaseQualities(), decodeReduceCounts(record),
                getFlowSignals(flowOrder, keySequence), flowOrder, this.getFlowSignalsStart());

        Object colorTag = record.getAttribute("YC");
        if (colorTag != null) {
            try {
                color = ColorUtilities.stringToColor(colorTag.toString());
            } catch (Exception e) {
                log.error("Error interpreting color tag: " + colorTag, e);
            }
        }
    }      // End constructor

    private void setMatePair(Genome genome) {
        SAMRecord record = getRecord();
        if (record.getReadPairedFlag()) {
            String mateReferenceName = record.getMateReferenceName();
            String mateChr = genome == null ? mateReferenceName : genome.getChromosomeAlias(mateReferenceName);
            this.setMate(new ReadMate(mateChr,
                    record.getMateAlignmentStart() - 1,
                    record.getMateNegativeStrandFlag(),
                    record.getMateUnmappedFlag()));
        }

    }

    private void setPairOrientation() {
        SAMRecord record = getRecord();
        if (record.getReadPairedFlag() &&
                !record.getReadUnmappedFlag() &&
                !record.getMateUnmappedFlag() &&
                record.getReferenceName().equals(record.getMateReferenceName())) {

            char s1 = record.getReadNegativeStrandFlag() ? 'R' : 'F';
            char s2 = record.getMateNegativeStrandFlag() ? 'R' : 'F';
            char o1 = ' ';
            char o2 = ' ';
            if (record.getFirstOfPairFlag()) {
                o1 = '1';
                o2 = '2';
            } else if (record.getSecondOfPairFlag()) {
                o1 = '2';
                o2 = '1';
            }

            final char[] tmp = new char[4];
            int isize = record.getInferredInsertSize();
            int estReadLen = record.getAlignmentEnd() - record.getAlignmentStart() + 1;
            if (isize == 0) {
                //isize not recorded.  Need to estimate.  This calculation was validated against an Illumina
                // -> <- library bam.
                int estMateEnd = record.getAlignmentStart() < record.getMateAlignmentStart() ?
                        record.getMateAlignmentStart() + estReadLen : record.getMateAlignmentStart() - estReadLen;
                isize = estMateEnd - record.getAlignmentStart();
            }

            //if (isize > estReadLen) {
            if (isize > 0) {
                tmp[0] = s1;
                tmp[1] = o1;
                tmp[2] = s2;
                tmp[3] = o2;

            } else {
                tmp[2] = s1;
                tmp[3] = o1;
                tmp[0] = s2;
                tmp[1] = o2;
            }
            // }
            pairOrientation = new String(tmp);
        }
    }

    /**
     * Set pair strands.  Used for strand specific libraries to recover strand of
     * originating fragment.
     */
    private void setPairStrands() {

        if (isPaired()) {
            if (isFirstOfPair()) {
                firstOfPairStrand = getReadStrand();
            } else {
                // If we have a mate, the mate must be the firstOfPair
                ReadMate mate = getMate();
                if (mate != null && mate.isMapped()) {
                    firstOfPairStrand = mate.getStrand();
                } else {
                    // No Mate, or mate is not mapped, FOP strand is not defined
                    firstOfPairStrand = Strand.NONE;
                }
            }

            if (isSecondOfPair()) {
                secondOfPairStrand = isNegativeStrand() ? Strand.NEGATIVE : Strand.POSITIVE;
            } else {
                ReadMate mate = getMate();
                if (mate.isMapped() && isProperPair()) {
                    secondOfPairStrand = mate.isNegativeStrand() ? Strand.NEGATIVE : Strand.POSITIVE;
                } else {
                    // No Mate, or mate is not mapped, FOP strand is not defined
                    secondOfPairStrand = Strand.NONE;
                }
            }

        } else {
            // This alignment is not paired -- by definition "firstOfPair" is this alignment
            firstOfPairStrand = getReadStrand();
            secondOfPairStrand = Strand.NONE;
        }
    }

    /**
     * Create the alignment blocks from the read bases and alignment information in the CIGAR
     * string.  The CIGAR string encodes insertions, deletions, skipped regions, and padding.
     *
     * @param cigarString
     * @param readBases
     * @param readBaseQualities
     */
    private void createAlignmentBlocks(String cigarString, byte[] readBases, byte[] readBaseQualities) {
        createAlignmentBlocks(cigarString, readBases, readBaseQualities, null, null, null, -1);
    }

    /**
     * Create the alignment blocks from the read bases and alignment information in the CIGAR
     * string.  The CIGAR string encodes insertions, deletions, skipped regions, and padding.
     *
     * @param cigarString
     * @param readBases
     * @param readBaseQualities
     * @param readRepresentativeCounts the representative counts of each base in the read (translated from the reduce reads tag)
     * @param flowSignals              from the FZ tag, null if not present
     * @param flowOrder                from the RG.FO header tag, null if not present
     * @param flowOrderStart
     */
    private void createAlignmentBlocks(String cigarString, byte[] readBases, byte[] readBaseQualities, short[] readRepresentativeCounts,
                                       short[] flowSignals, String flowOrder, int flowOrderStart) {

        boolean showSoftClipped = PreferenceManager.getInstance().getAsBoolean(PreferenceManager.SAM_SHOW_SOFT_CLIPPED);

        int nInsertions = 0;
        int nBlocks = 0;

        List<CigarOperator> operators = new ArrayList();
        StringBuffer buffer = new StringBuffer(4);

        if (cigarString.equals("*")) {
            alignmentBlocks = new AlignmentBlock[1];
            alignmentBlocks[0] = new AlignmentBlock(getChr(), getStart(), readBases, readBaseQualities);
            return;
        }

        // Create list of cigar operators
        boolean firstOperator = true;
        int softClippedBaseCount = 0;
        int nGaps = 0;
        char prevOp = 0;
        for (int i = 0; i < cigarString.length(); i++) {
            char next = cigarString.charAt(i);
            if (Character.isDigit(next)) {
                buffer.append(next);
            } else {
                char op = next;
                if (op == HARD_CLIP) {
                    buffer = new StringBuffer(4);
                    continue;  // Just skip hardclips
                }
                int nBases = Integer.parseInt(buffer.toString());
                if (operatorIsMatch(showSoftClipped, op)) {
                    if (operatorIsMatch(showSoftClipped, prevOp)) {
                        nGaps++;   // Consecutive Ms
                    }
                    nBlocks++;

                } else if (op == DELETION || op == SKIPPED_REGION) {
                    nGaps++;
                } else if (op == INSERTION) {
                    nInsertions++;
                    nGaps++; // "virtual" gap, account for artificial block split @ insertion
                } else if (op == PADDING) {
                    nGaps++;
                }


                if (firstOperator && op == SOFT_CLIP) {
                    softClippedBaseCount += nBases;
                }

                operators.add(new CigarOperator(nBases, op));
                buffer = new StringBuffer(4);

                prevOp = op;
                firstOperator = false;
            }
        }

        alignmentBlocks = new AlignmentBlock[nBlocks];
        insertions = new AlignmentBlock[nInsertions];
        if (nGaps > 0) {
            gapTypes = new char[nGaps];
        }

        // Adjust start to include soft clipped bases a
        if (showSoftClipped) {
            start -= softClippedBaseCount;
        }
        int fromIdx = showSoftClipped ? 0 : softClippedBaseCount;
        int blockStart = start;

        // Create blocks
        int blockIdx = 0;
        int insertionIdx = 0;
        int gapIdx = 0;
        FlowSignalContextBuilder fBlockBuilder = null;
        if (null != flowSignals) {
            if (0 < readBases.length) {
                fBlockBuilder = new FlowSignalContextBuilder(flowSignals, flowOrder, flowOrderStart, readBases, fromIdx, this.isNegativeStrand());
            }
        }
        prevOp = 0;
        for (CigarOperator op : operators) {
            try {

                if (op.operator == HARD_CLIP) {
                    continue;
                }
                if (operatorIsMatch(showSoftClipped, op.operator)) {

                    AlignmentBlock block = buildAlignmentBlock(fBlockBuilder, readBases, readBaseQualities,
                            readRepresentativeCounts, getChr(), blockStart, fromIdx, op.nBases, true);

                    if (op.operator == SOFT_CLIP) {
                        block.setSoftClipped(true);
                    }
                    alignmentBlocks[blockIdx++] = block;

                    fromIdx += op.nBases;
                    blockStart += op.nBases;

                    if (operatorIsMatch(showSoftClipped, prevOp)) {
                        gapTypes[gapIdx++] = ZERO_GAP;
                    }

                } else if (op.operator == DELETION || op.operator == SKIPPED_REGION ) {
                    blockStart += op.nBases;
                    gapTypes[gapIdx++] = op.operator;
                } else if (op.operator == INSERTION) {
                    // This gap is between blocks split by insertion.   It is a zero
                    // length gap but must be accounted for.
                    gapTypes[gapIdx++] = ZERO_GAP;
                    AlignmentBlock block = buildAlignmentBlock(fBlockBuilder, readBases, readBaseQualities,
                            readRepresentativeCounts, getChr(), blockStart, fromIdx, op.nBases, false);

                    insertions[insertionIdx++] = block;
                    fromIdx += op.nBases;
                } else if (op.operator == PADDING){
                    //Padding represents a deletion against the padded reference
                    //But we don't have the padded reference
                    gapTypes[gapIdx++] = ZERO_GAP;
                }
            } catch (Exception e) {
                log.error("Error processing CIGAR string", e);
            }
            prevOp = op.operator;
        }

        // Check for soft clipping at end
        if (showSoftClipped && operators.size() > 0) {
            CigarOperator last = operators.get(operators.size() - 1);
            if (last.operator == SOFT_CLIP) {
                end += last.nBases;
            }
        }
    }

    private static AlignmentBlock buildAlignmentBlock(FlowSignalContextBuilder fBlockBuilder, byte[] readBases,
                                               byte[] readBaseQualities,
                                               short[] readRepresentativeCounts, String chr, int blockStart,
                                               int fromIdx, int nBases, boolean checkNBasesAvailable){

        byte[] blockBases = new byte[nBases];
        byte[] blockQualities = new byte[nBases];
        short[] blockCounts = new short[nBases];

        // TODO -- represent missing sequence ("*") explicitly for efficiency.
        int nBasesAvailable = nBases;
        if(checkNBasesAvailable){
            nBasesAvailable = readBases.length - fromIdx;
        }
        if (readBases == null || readBases.length == 0) {
            Arrays.fill(blockBases, (byte) '=');
        } else if (nBasesAvailable < nBases) {
            Arrays.fill(blockBases, (byte) '?');
        } else {
            System.arraycopy(readBases, fromIdx, blockBases, 0, nBases);
        }

        nBasesAvailable = nBases;
        if(checkNBasesAvailable){
            nBasesAvailable = readBaseQualities.length - fromIdx;
        }
        if (readBaseQualities == null || readBaseQualities.length == 0 || nBasesAvailable < nBases) {
            Arrays.fill(blockQualities, (byte) 126);
        } else {
            System.arraycopy(readBaseQualities, fromIdx, blockQualities, 0, nBases);
        }

        if (readRepresentativeCounts != null) {
            System.arraycopy(readRepresentativeCounts, fromIdx, blockCounts, 0, nBases);
        }

        AlignmentBlock block;
        if (fBlockBuilder != null) {
            block = AlignmentBlock.getInstance(chr, blockStart, blockBases, blockQualities,
                    fBlockBuilder.getFlowSignalContext(readBases, fromIdx, nBases));
        } else {
            block = AlignmentBlock.getInstance(chr, blockStart, blockBases, blockQualities);
        }
        if (readRepresentativeCounts != null) {
            block.setCounts(blockCounts);
        }
        return block;
    }

    private boolean operatorIsMatch(boolean showSoftClipped, char operator) {
        return operator == MATCH || operator == PERFECT_MATCH || operator == MISMATCH
                || (showSoftClipped && operator == SOFT_CLIP);
    }

    public boolean isNegativeStrand() {
        return record.getReadNegativeStrandFlag();
    }

    public boolean isDuplicate() {
        return this.record.getDuplicateReadFlag();
    }

    public boolean isMapped() {
        return !this.record.getReadUnmappedFlag();
    }

    @Override
    public int getReadLength() {
        return this.record.getReadLength();
    }

    public boolean isPaired() {
        return this.record.getReadPairedFlag();
    }

    public boolean isProperPair() {
        return isPaired() && this.record.getProperPairFlag();
    }

    public boolean isFirstOfPair() {
        return isPaired() && this.record.getFirstOfPairFlag();
    }

    public boolean isSecondOfPair() {
        return isPaired() && this.record.getSecondOfPairFlag();
    }

    /**
     * @return the unclippedStart
     */
    public int getAlignmentStart() {
        return alignmentStart;
    }

    public String getCigarString() {
        return this.record.getCigarString();
    }

    public String getReadSequence() {
        return this.record.getReadString();
    }

    /**
     * Use blocks to recreate read sequence.
     * As of this comment writing, we don't keep a block
     * for hard-clipped bases, so this won't match what's in the file
     * @return
     */
    String buildReadSequenceFromBlocks(){
        String readSeq = "";
        for(AlignmentBlock block: getAlignmentBlocks()){
            readSeq += new String(block.getBases());
        }
        return readSeq;
    }

    @Override
    public boolean isPrimary() {
        return !this.record.getNotPrimaryAlignmentFlag();
    }

    @Override
    public boolean isSupplementary() {
        return this.record.getSupplementaryAlignmentFlag();
    }

    /**
     * @return the alignmentEnd
     */
    public int getAlignmentEnd() {
        return alignmentEnd;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public String getSample() {
        return sample;
    }

    public String getReadGroup() {
        return readGroup;
    }

    public String getLibrary() {
        return library;
    }

    /**
     * @return The SAMRecord which created this SamAlignment
     */
    public SAMRecord getRecord() {
        return this.record;
    }

    @Override
    public String toString() {
        return getRecord().getSAMString();
    }

    @Override
    public char[] getGapTypes() {
        return gapTypes;
    }

    public Object getAttribute(String key) {
        // SAM alignment tag keys must be of length 2
        return key.length() == 2 ? getRecord().getAttribute(key) :
                (key.equals("TEMPLATE_ORIENTATION") ? pairOrientation : null);
    }

    public String getClipboardString(double location) {
        return getValueStringImpl(location, false);
    }

    /**
     * Return info string for popup text and to copy to clipboard
     *
     * @param position
     * @param windowFunction -- not relevant, ignored
     * @return
     */
    public String getValueString(double position, WindowFunction windowFunction) {
        return getValueStringImpl(position, true);
    }

    String getValueStringImpl(double position, boolean truncate) {

        StringBuffer buf = new StringBuffer(super.getValueString(position, null));
        SAMRecord record = getRecord();
        if (isPaired()) {
            boolean sectionBreak = false;
            if (record.getFirstOfPairFlag()) {
                buf.append("<br>First in pair");
                sectionBreak = true;
            }
            if (record.getSecondOfPairFlag()) {
                buf.append("<br>Second in pair");
                sectionBreak = true;
            }
            if (record.getNotPrimaryAlignmentFlag()) {
                buf.append("<br>Alignment NOT primary");
                sectionBreak = true;
            }
            if (record.getReadFailsVendorQualityCheckFlag()) {
                buf.append("<br>FAILED Vendor quality check");
                sectionBreak = true;
            }
            if (sectionBreak) {
                buf.append("<br>-------------------");
            }
        }

        if(record.getSupplementaryAlignmentFlag()){
            buf.append("<br>Supplementary alignment (chimeric)");
        }

        List<SAMRecord.SAMTagAndValue> attributes = record.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {

            for (SAMRecord.SAMTagAndValue tag : attributes) {
                buf.append("<br>" + tag.tag + " = ");

                if (tag.value.getClass().isArray()) { // ignore array types
                    buf.append("[not shown]<br>");
                    continue;
                }

                // Break tag
                final String tagValue = tag.value.toString();
                final int maxLength = 70;
                if (tagValue.length() > maxLength && truncate) {
                    String[] tokens = tagValue.split("<br>");
                    for (String token : tokens) {
                        if (token.length() > maxLength) {
                            // Insert line breaks
                            String remainder = token;
                            while (remainder.length() > maxLength) {
                                String tmp = remainder.substring(0, maxLength);
                                int spaceIndex = tmp.lastIndexOf(' ');
                                int idx = spaceIndex > 30 ? spaceIndex : maxLength;
                                final String substring = remainder.substring(0, idx);
                                buf.append(substring);
                                buf.append("<br>");
                                remainder = remainder.substring(idx);
                            }
                            buf.append(remainder);
                            buf.append("<br>");

                        } else {
                            buf.append(token);
                            buf.append("<br>");
                        }
                    }
                } else {
                    buf.append(tagValue);
                }

            }
            buf.append("<br>-------------------");
        }

        if (mateSequence != null) {
            buf.append("<br>Unmapped mate sequence: " + mateSequence);
            buf.append("<br>-------------------");
        }
        return buf.toString();
    }

    @Override
    public String getPairOrientation() {
        return pairOrientation;
    }

    @Override
    public void finish() {
        super.finish();

        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        for(AlignmentBlock block: alignmentBlocks){
            block.reduce(genome);
        }
    }

    public boolean isVendorFailedRead() {
        return getRecord().getReadFailsVendorQualityCheckFlag();
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String getMateSequence() {
        return this.mateSequence;
    }

    @Override
    public void setMateSequence(String sequence) {
        this.mateSequence = sequence;
    }


    /**
     * Return the strand of the read marked "first-in-pair" for a paired alignment. This method can return
     * Strand.NONE if the end marked first is unmapped.
     *
     * @return strand of first-of-pair
     */
    public Strand getFirstOfPairStrand() {
        return firstOfPairStrand;
    }

    /**
     * Return the strand of the read marked "second-in-pair" for a paired alignment.  The strand is
     * undefined (Strand.NONE) for non-paired alignments
     *
     * @return strand of second-of-pair
     */
    public Strand getSecondOfPairStrand() {
        return secondOfPairStrand;
    }

    static class CigarOperator {

        int nBases;
        char operator;

        /**
         * Constructs ...
         *
         * @param nBases
         * @param operator
         */
        public CigarOperator(int nBases, char operator) {
            this.nBases = nBases;
            this.operator = operator;
        }
    }

    /**
     * @return start index in the flow signal as specified by the ZF tag, or -1 if not present
     *         or non-numeric
     */
    public int getFlowSignalsStart() {
        Object attribute = getRecord().getAttribute(FLOW_SIGNAL_TAG); // NB: from a TMAP optional tag
        int toRet = -1;
        if (attribute != null && attribute instanceof Integer) {
            toRet = (Integer) attribute;
        }
        return toRet;
    }

    /**
     * @param flowOrder   the flow order corresponding to this read
     * @param keySequence sequence the key sequence corresponding to this read
     * @return the flow signals in 100x format (SFF), only if they exist (FZ tag),
     *         if the key sequence and flow order are found in the read group header tag
     *         (RG.KS and RG.FO).  Note: the array proceeds in the sequencing direction.
     */
    public short[] getFlowSignals(String flowOrder, String keySequence) {
        short[] r = null;
        int i;
        int startFlow, keySignalOverlap;
        char firstBase;

        if (null == flowOrder || null == keySequence) {
            return null;
        }

        startFlow = this.getFlowSignalsStart();
        if (startFlow < 0) {
            return null;
        }

        // get the # of bases that the first base in the read overlaps with the last base(s) in the key
        SAMRecord record = getRecord();
        if (this.isNegativeStrand()) {
            firstBase = (char) NT2COMP[record.getReadBases()[record.getReadLength() - 1]];
        } else {
            firstBase = (char) record.getReadBases()[0];
        }
        keySignalOverlap = 0;
        for (i = keySequence.length() - 1; 0 <= i && keySequence.charAt(i) == firstBase; i--) {
            keySignalOverlap += 100;
        }

        Object attribute = record.getAttribute("FZ");
        if (null == attribute) {
            return null;
        } else if (attribute instanceof short[]) {
            short[] signals = (short[]) attribute;
            r = new short[signals.length - startFlow];
            for (i = startFlow; i < signals.length; i++) {
                r[i - startFlow] = signals[i];
            }
        } else if (attribute instanceof int[]) {
            int[] signals = (int[]) attribute;
            r = new short[signals.length - startFlow];
            System.arraycopy(signals, startFlow, r, 0, r.length);
        } else if (attribute instanceof byte[]) {
            byte[] signals = (byte[]) attribute;
            r = new short[signals.length - startFlow];
            for (i = startFlow; i < signals.length; i++) {
                r[i - startFlow] = signals[i];
            }
        } else {
            return null;
        }
        // Subtract the key's contribution to the first base
        if (0 < keySignalOverlap && 0 < r.length) {
            if (r[0] <= keySignalOverlap) {
                r[0] = 0;
            } else {
                r[0] -= keySignalOverlap;
            }
        }

        return r;
    }

    /**
     * Reduced reads are stored in an array, where the actual
     * number of reads is stored as an offset from the first location.
     * Here we decode that array, so it becomes an array where the value
     * at each location
     *
     * @param record the sam record for this read
     * @return a byte array with the representative counts of each base in this read, or null if this is not a reduced read
     */
    static short[] decodeReduceCounts(SAMRecord record) {
        Object reducedReadsVal = record.getAttribute(REDUCE_READS_TAG);
        // in case this read doesn't have the RR tag (is not a reduced read) return null
        // so the subsequent routines know that this is not a reduced read
        if (reducedReadsVal == null)
            return null;

        short[] encodedCounts;
        if (reducedReadsVal instanceof short[]) {
            encodedCounts = (short[]) reducedReadsVal;
        } else if (reducedReadsVal instanceof byte[]) {
            byte[] rrArr = (byte[]) reducedReadsVal;
            int len = rrArr.length;
            encodedCounts = new short[len];
            for (int ii = 0; ii < len; ii++) {
                encodedCounts[ii] = (short) rrArr[ii];
            }
        } else {
            log.info("Found reduced reads tag, but was unexpected type " + reducedReadsVal.getClass());
            return null;
        }

        short[] decodedCounts = new short[encodedCounts.length];
        short startVal = encodedCounts[0];
        decodedCounts[0] = startVal;
        for (int ii = 1; ii < decodedCounts.length; ii++) {
            decodedCounts[ii] = (short) Math.min(startVal + encodedCounts[ii], Short.MAX_VALUE);
        }
        return decodedCounts;

    }

}

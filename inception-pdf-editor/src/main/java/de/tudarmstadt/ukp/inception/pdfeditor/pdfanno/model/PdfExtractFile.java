/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.pdfeditor.pdfanno.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

/**
 * Represents a PDFExtract file.
 * This file contains information about the content of a PDF document.
 * This includes characters and their order and position but also about
 * draw operations and their positions.
 */
public class PdfExtractFile implements Serializable
{

    private static final long serialVersionUID = -8596941152876909935L;

    private String pdftxt;

    private String stringContent;

    /**
     * Contains position mapping for characters between PDFExtract string
     * including and excluding Draw Operations.
     */
    private BidiMap<Integer, Integer> stringPositionMap;

    private Map<String, String> ligatures;

    private String ligaturelessContent;

    /**
     * Contains position mapping for characters between ligaturelessContent and stringContent
     */
    private BidiMap<Integer, Integer> ligatureStringPositionMap;

    /**
     * Map of line numbers and lines contained in a PDFExtract file.
     */
    private Map<Integer, PdfExtractLine> extractLines;


    public PdfExtractFile(String aPdftxt, Map<String, String> aLigatures)
    {
        initializeStringContent(aPdftxt);
        initializeLigaturelessContent(aLigatures);
    }

    private void initializeLigaturelessContent(Map<String, String> aLigatures)
    {
        ligatures = aLigatures;
        ligaturelessContent = stringContent;
        ligatureStringPositionMap = new DualHashBidiMap<>();

        // build Aho-Corasick Trie to search for ligature occurences and replace them
        Trie.TrieBuilder trieBuilder = Trie.builder();
        ligatures.keySet().forEach(key -> trieBuilder.addKeyword(key));
        Trie trie = trieBuilder.build();
        Collection<Emit> emits = trie.parseText(ligaturelessContent);
        Map<Integer, Emit> occurrences = new HashMap<>();
        for (Emit emit : emits) {
            occurrences.put(emit.getStart(), emit);
        }

        int stringIndex = 0;
        int ligatureIndex = 0;
        StringBuilder sb = new StringBuilder();
        // iterate over the text and create a new string containing replaced ligatures
        // also create a new map that maps from new ligature string to normal string content
        while (stringIndex < ligaturelessContent.length()) {
            char c = ligaturelessContent.charAt(stringIndex);
            if (occurrences.containsKey(stringIndex)) {
                // start of a ligature was found
                Emit emit = occurrences.get(stringIndex);
                String replacement = ligatures.get(emit.getKeyword());
                int keyLen = emit.getKeyword().length();
                int replacementLen = replacement.length();

                // iterate over chars of ligature string and its replacement, create proper mapping
                ligatureStringPositionMap.put(ligatureIndex, stringIndex);
                sb.append(replacement.charAt(0));
                for (int l = 1, r = 1;;) {
                    if (l == keyLen && r == replacementLen) {
                        // end of both strings reached
                        break;
                    }
                    ligatureStringPositionMap.put(ligatureIndex + r, stringIndex + l);
                    if (l < keyLen) {
                        l++;
                    }
                    if (r < replacementLen) {
                        sb.append(replacement.charAt(r));
                        r++;
                    }
                }

                stringIndex += keyLen - 1;
                ligatureIndex += replacementLen - 1;
            } else {
                sb.append(c);
                ligatureStringPositionMap.put(ligatureIndex, stringIndex);
            }
            ligatureIndex++;
            stringIndex++;
        }

        ligaturelessContent = sb.toString();
    }

    private void initializeStringContent(String aPdftxt)
    {
        stringPositionMap = new DualHashBidiMap<>();
        extractLines = new HashMap<>();
        pdftxt = aPdftxt;

        StringBuilder sb = new StringBuilder();
        String[] lines = pdftxt.split("\n");

        int extractLineIndex = 1;
        int strContentIndex = 0;

        for (String line : lines)
        {
            PdfExtractLine extractLine = new PdfExtractLine();
            String[] columns = line.split("\t");
            extractLine.setPage(Integer.parseInt(columns[0].trim()));
            extractLine.setPosition(extractLineIndex);
            extractLine.setValue(columns[1].trim());
            extractLine.setDisplayPositions(columns.length > 2 ? columns[2].trim() : "");
            extractLines.put(extractLineIndex, extractLine);

            // if value of PdfExtractLine is in brackets it is a draw operation and is ignored
            if (!extractLine.getValue().matches("^\\[.*\\]$")
                && !extractLine.getValue().equals("NO_UNICODE"))
            {
                sb.append(extractLine.getValue());
                stringPositionMap.put(strContentIndex, extractLineIndex);
                strContentIndex++;
            }
            extractLineIndex++;
        }

        stringContent = sb.toString();
    }

    public String getPdftxt()
    {
        return pdftxt;
    }

    public String getStringContent()
    {
        return stringContent;
    }

    /**
     * Gets PdfExtractLines between the given range in the string-only content
     */
    public List<PdfExtractLine> getStringPdfExtractLines(int aStart, int aEnd)
    {
        List<PdfExtractLine> lines = new ArrayList<>();
        for (int i = aStart; i <= aEnd; i++)
        {
            lines.add(getStringPdfExtractLine(i));
        }
        return lines;
    }

    /**
     * Gets the PdfExtractLine for a given index in the INCEpTION document text
     */
    public PdfExtractLine getStringPdfExtractLine(int aPosition)
    {
        return extractLines.get(stringPositionMap.get(ligatureStringPositionMap.get(aPosition)));
    }

    /**
     * Gets the index in the actual PdfExtract string content for a PdfExtractLine index.
     * PdfExtract string content does not include draw operations in the index counting.
     */
    public int getStringIndex(int pdfExtractLine)
    {
        return ligatureStringPositionMap.getKey(stringPositionMap.getKey(pdfExtractLine));
    }

    public String getLigaturelessContent()
    {
        return ligaturelessContent;
    }
}

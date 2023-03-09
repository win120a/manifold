/*
 * Copyright (c) 2020 - Manifold Systems LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package manifold.csv.rt;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import manifold.csv.rt.parser.CsvToken;
import manifold.csv.rt.parser.CsvTokenizer;
import manifold.rt.api.util.StreamUtil;
import org.junit.Test;


import static org.junit.Assert.*;

public class CsvTokenizerTest {
    @Test
    public void testPositiveHeaderInference() throws IOException {
        String[] header = {"policyID", "statecode", "county", "eq_site_limit", "hu_site_limit", "fl_site_limit", "fr_site_limit", "tiv_2011", "tiv_2012", "eq_site_deductible", "hu_site_deductible", "fl_site_deductible", "fr_site_deductible", "point_latitude", "point_longitude", "line", "construction", "point_granularity"};

        String[] separatorNames = {"comma", "semicolon", "tab"};
        for (String sepName : separatorNames) {
            InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/insurance_sample_" + sepName + ".csv");
            byte[] content = StreamUtil.getContent(stream);
            CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
            assertTrue(tokenizer.hasHeader());

            int i = 0;
            int row = 0;
            while (true) {
                CsvToken token = tokenizer.nextToken();
                if (row == 0) {
                    assertEquals(header[i], token.getValue());
                }
                i++;
                if (token.isLastInRecord()) {
                    assertEquals(18, i);
                    row++;
                    i = 0;
                }
                if (token.isEof()) {
                    assertEquals(18, row);
                    break;
                }
            }
        }
    }

    @Test
    public void testPositiveHeaderInferenceMisc() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/PRECIP_HLY_sample.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertTrue(tokenizer.hasHeader());

        stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/Nnndss.csv");
        content = StreamUtil.getContent(stream);
        tokenizer = new CsvTokenizer(new String(content));
        assertTrue(tokenizer.hasHeader());

        int recordLength = 1;
        while (!tokenizer.nextToken().isLastInRecord()) {
            recordLength++;
        }
        assertEquals(21, recordLength);

        int rows = 0;
        int fields = 0;
        for (CsvToken t = tokenizer.nextToken(); !t.isEof(); t = tokenizer.nextToken()) {
            fields++;
            assertFalse(fields > recordLength);
            if (t.isLastInRecord()) {
                assertEquals(recordLength, fields);
                fields = 0;
                rows++;
            }
        }
        assertEquals(1469, rows);
    }

    @Test
    public void testNegativeHeaderInference() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/insurance_sample_noheader.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertFalse(tokenizer.hasHeader());

    }

    @Test
    public void testNegativeHeaderInferenceMisc() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/SampleCSVFile_2kb.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertFalse(tokenizer.hasHeader());
    }

    @Test
    public void testEmpty() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/empty.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertFalse(tokenizer.hasHeader());
    }

    @Test
    public void testEmptyLeadingLinebreak() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/empty_leading_linebreak.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertFalse(tokenizer.hasHeader());
    }

    @Test
    public void testTrailingWhitespace() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/trailing_whitespace.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertTrue(tokenizer.hasHeader());
        assertEquals("hello", tokenizer.nextToken().getData());
        assertEquals("bye", tokenizer.nextToken().getData());
        assertTrue(tokenizer.isEol());
        assertEquals("data data", tokenizer.nextToken().getData());
        assertEquals("more data", tokenizer.nextToken().getData());
        assertTrue(tokenizer.isEof());
    }

    @Test
    public void testLeadingWhitespace() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/leading_whitespace.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertTrue(tokenizer.hasHeader());
        assertEquals("name", tokenizer.nextToken().getData());
        assertEquals("age", tokenizer.nextToken().getData());
        assertEquals("cake", tokenizer.nextToken().getData());
        assertTrue(tokenizer.isEol());
        assertEquals("scott", tokenizer.nextToken().getData());
        assertEquals("29", tokenizer.nextToken().getData());
        assertEquals("chocolate", tokenizer.nextToken().getData());
        assertTrue(tokenizer.isEol());
        assertEquals("bob", tokenizer.nextToken().getData());
        assertEquals("27", tokenizer.nextToken().getData());
        assertEquals("strawberry", tokenizer.nextToken().getData());
        assertTrue(tokenizer.isEof());
    }

    @Test
    public void testTypeInference() throws IOException {
        InputStream stream = CsvTokenizerTest.class.getResourceAsStream("/sample/csv/TechCrunchcontinentalUSA.csv");
        byte[] content = StreamUtil.getContent(stream);
        CsvTokenizer tokenizer = new CsvTokenizer(new String(content));
        assertTrue(tokenizer.hasHeader());
        assertArrayEquals(
                new Class[]{String.class, String.class, Integer.class, String.class, String.class,
                        String.class, LocalDate.class, Integer.class, String.class, String.class},
                tokenizer.getTypes().toArray(new Class[0]));
    }
}

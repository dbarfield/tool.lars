/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
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
 *******************************************************************************/

package com.ibm.ws.lars.upload.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mockit.Mock;
import mockit.MockUp;

import org.junit.After;
import org.junit.Test;

import com.ibm.ws.repository.common.enums.ResourceType;
import com.ibm.ws.repository.connections.RepositoryConnection;
import com.ibm.ws.repository.exceptions.RepositoryBackendException;
import com.ibm.ws.repository.resources.EsaResource;
import com.ibm.ws.repository.resources.RepositoryResource;
import com.ibm.ws.repository.resources.internal.EsaResourceImpl;
import com.ibm.ws.repository.resources.internal.RepositoryResourceImpl;
import com.ibm.ws.repository.transport.model.Asset;
import com.ibm.ws.repository.transport.model.WlpInformation;

public class ListAllTest {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteArrayOutputStream ebaos = new ByteArrayOutputStream();
    PrintStream output = new PrintStream(baos);
    PrintStream errorStream = new PrintStream(ebaos);
    InputStream input = new ByteArrayInputStream(new byte[0]);
    Main tested = new Main(input, output);

    @After
    public void tearDown() {
        output.close();
        errorStream.close();
    }

    @Test
    public void testNoUrl() {

        // Should throw an exception and print out a help message

        try {
            tested.run(new String[] { "--listAll" });
        } catch (ClientException e) {
            assertEquals("Unexpected exception message", Main.MISSING_URL, e.getMessage());
            String outputString = baos.toString();
            assertTrue("The expected help output wasn't produced, was:\n" + outputString, TestUtils.checkForHelpMessage(outputString));

            String errorOutput = ebaos.toString();
            assertEquals("No output was expected to stderr", "", errorOutput);
            return;
        }
        fail("The expected client exception was not thrown");
    }

    @Test
    public void testFooUrl() {

        // Should throw an exception but no help message

        try {
            tested.run(new String[] { "--listAll", "--url=foobar" });
        } catch (ClientException e) {
            assertEquals("Unexpected exception message", Main.INVALID_URL + "foobar", e.getMessage());
            String outputString = baos.toString();
            assertTrue("The expected output wasn't produced, was:\n" + outputString, outputString.contains(Main.INVALID_URL));
            String errorOutput = ebaos.toString();
            assertEquals("No output was expected to stderr", "", errorOutput);
            return;
        }
        fail("The expected client exception was not thrown");
    }

    @Test
    public <RC extends RepositoryConnection> void testEmptyRepository() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ClientException {

        new MockUp<RC>() {
            @Mock
            public Collection<RepositoryResource> getAllResources() throws RepositoryBackendException {
                return Collections.emptySet();
            }
        };

        tested.run(new String[] { "--listAll", "--url=http://foobar.baz" });
        String output = baos.toString();
        assertTrue("The test output didn't contain the expected string, was: " + output,
                   output.contains("No assets found in repository"));
        assertEquals("The error output wasn't empty", "", ebaos.toString());
    }

    @Test
    public <RC extends RepositoryConnection> void testResultsFormat() throws ClientException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final List<RepositoryResource> getAllList = new ArrayList<>();
        getAllList.add(getTestEsa("1", "A name", "A short description", "productVersion=8.5.5.4;"));
        getAllList.add(getTestEsa("2", "A name", null, null));
        getAllList.add(getTestEsa("3", "A quoted version", "A shortish description", "productVersion=\"8.5.5.7\";"));

        new MockUp<RC>() {
            @Mock
            public Collection<RepositoryResource> getAllResources() throws RepositoryBackendException {
                return getAllList;
            }
        };

        tested.run(new String[] { "--listAll", "--url=http://foobar.baz" });
        String output = baos.toString();

        Pattern pat = Pattern.compile("1\\s\\+|\\s+Feature\\s+\\|\\s+8\\.5\\.5\\.4\\s+\\|\\s+A name\\s+\\(A short description\\)");
        Matcher match = pat.matcher(output);
        assertTrue("The output didn't contain the expected asset description:\n" + output, match.find());

        Pattern pat2 = Pattern.compile("2\\s+\\|\\s+Feature\\s+\\|\\s+\\|\\s+A name");
        Matcher match2 = pat2.matcher(output);
        assertTrue("The output didn't contain the expected asset description:\n" + output, match2.find());

        Pattern pat3 = Pattern.compile("1\\s\\+|\\s+Feature\\s+\\|\\s+8\\.5\\.5\\.7\\s+\\|\\s+A quoted version\\s+\\(A shortish description\\)");
        Matcher match3 = pat3.matcher(output);
        assertTrue("The output didn't contain the expected asset description:\n" + output, match3.find());

        assertEquals("The error output wasn't empty", "", ebaos.toString());
    }

    /**
     * Creates an EsaResource with the specified fields. These are normally created from the
     * repository or read in from a real Esa, so a number of fields have to be set reflectively.
     */
    private EsaResource getTestEsa(String id, String name, String shortName, String appliesTo) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Asset asset = new Asset();
        asset.set_id(id);
        asset.setName(name);
        asset.setType(ResourceType.FEATURE);
        WlpInformation info = new WlpInformation();
        if (shortName != null) {
            info.setShortName(shortName);
        }
        info.setAppliesTo(appliesTo);
        asset.setWlpInformation(info);

        EsaResource er = new EsaResourceImpl(null);
        Field assetField = RepositoryResourceImpl.class.getDeclaredField("_asset");
        assetField.setAccessible(true);
        assetField.set(er, asset);

        return er;
    }

}

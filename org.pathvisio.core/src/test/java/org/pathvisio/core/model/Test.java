/*******************************************************************************
 * PathVisio, a tool for data visualization and analysis using biological pathways
 * Copyright 2006-2021 BiGCaT Bioinformatics, WikiPathways
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package org.pathvisio.core.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.bridgedb.DataSource;
import org.bridgedb.Xref;
import org.pathvisio.core.data.XrefWithSymbol;
import org.pathvisio.core.preferences.PreferenceManager;
import org.pathvisio.core.view.model.DefaultTemplates;
import org.pathvisio.event.PathwayModelEvent;
import org.pathvisio.event.PathwayModelListener;
import org.pathvisio.event.PathwayObjectEvent;
import org.pathvisio.event.PathwayObjectListener;
import org.pathvisio.io.ConverterException;
import org.pathvisio.model.DataNode;
import org.pathvisio.model.Interaction;
import org.pathvisio.model.Label;
import org.pathvisio.model.Pathway;
import org.pathvisio.model.PathwayElement;
import org.pathvisio.model.PathwayModel;
import org.pathvisio.model.PathwayObject;
import org.pathvisio.model.type.DataNodeType;
import org.pathvisio.prop.StaticProperty;
import org.pathvisio.util.Utils;

/**
 * 
 * @author unknown
 */
public class Test extends TestCase implements PathwayModelListener, PathwayObjectListener {

	PathwayModel data;
	DataNode o;
	List<PathwayModelEvent> received;
	List<PathwayObjectEvent> receivedElementEvents;
	Interaction l;

	private static final File PATHVISIO_BASEDIR = new File("../..");

	public void setUp() {
		PreferenceManager.init();
		data = new PathwayModel();
		data.addListener(this);
		o = new DataNode("", null);
		received = new ArrayList<PathwayModelEvent>();
		receivedElementEvents = new ArrayList<PathwayObjectEvent>();
		o.addListener(this);
		data.add(o);
		l = new Interaction();
		data.add(l);
		received.clear();
		receivedElementEvents.clear();
	}

	public void testFields() {
		o.setCenterX(1.0);

		assertEquals("test set/get CenterX", 1.0, o.getCenterX(), 0.0001);

		assertEquals("Setting CenterX should generate single event", receivedElementEvents.size(), 1);
		assertEquals("test getProperty()", 1.0, (Double) o.getStaticProperty(StaticProperty.CENTERX), 0.0001);

		try {
			o.setStaticProperty(StaticProperty.CENTERX, null);
			fail("Setting centerx property to null should generate exception");
		} catch (Exception e) {
		}

		// however, you should be able to set graphRef to null

		assertNull("graphref null by default", l.getStartElementRef());
		l.setStartElementRef(null);
		assertNull("can set graphRef to null", l.getStartElementRef());
	}

	public void testProperties() throws IOException, ConverterException {
		// set 10 dynamic properties in two ways
		for (int i = 0; i < 5; ++i) {
			o.setDynamicProperty("Hello" + i, "World" + i);
		}
		for (int i = 5; i < 10; ++i) {
			o.setPropertyEx("Hello" + i, "World" + i);
		}

		// check contents of dynamic properties
		assertEquals("World0", o.getDynamicProperty("Hello0"));
		for (int i = 0; i < 10; ++i) {
			assertEquals("World" + i, o.getDynamicProperty("Hello" + i));
		}

		// check non-existing dynamic property

		assertNull(o.getDynamicProperty("NonExistingProperty"));

		// check that we have 10 dynamic properties, no more, no less.
		Set<String> dynamicKeys = o.getDynamicPropertyKeys();
		assertEquals(10, dynamicKeys.size());

		// check that superset dynamic + static also contains dynamic properties
		assertEquals("World0", o.getPropertyEx("Hello0"));
		for (int i = 0; i < 10; ++i) {
			assertEquals("World" + i, o.getPropertyEx("Hello" + i));
		}

		// check setting null property
		try {
			o.setStaticProperty(null, new Object());
			fail("Setting null property should generate exception");
		} catch (NullPointerException e) {
		}

		// check setting non string / StaticProperty property
		try {
			o.setPropertyEx(new Object(), new Object());
			fail("Using key that is not String or StaticProperty should generate exception");
		} catch (IllegalArgumentException e) {
		}

		// test storage of dynamic properties
		File temp = File.createTempFile("dynaprops.test", ".gpml");
		temp.deleteOnExit();

		// set an id on this element so we can find it back easily
		String id = o.setGeneratedElementId();

		// store
		data.writeToXml(temp, false);

		// and read back
		PathwayModel p2 = new PathwayModel();
		p2.readFromXml(temp, true);

		// get same datanode back
		DataNode o2 = (DataNode) p2.getPathwayObject(id);
		// check that it still has the dynamic properties after storing / reading
		assertEquals("World5", o2.getDynamicProperty("Hello5"));
		assertEquals("World3", o2.getPropertyEx("Hello3"));
		// sanity check: no non-existing properties
		assertNull(o2.getDynamicProperty("NonExistingProperty"));
		assertNull(o2.getPropertyEx("NonExistingProperty"));

		// check that dynamic properties are copied.
		PathwayElement o3 = o2.copy();
		assertEquals("World7", o3.getPropertyEx("Hello7"));

		// check that it's a deep copy
		o2.setDynamicProperty("Hello7", "Something other than 'World7'");
		assertEquals("World7", o3.getPropertyEx("Hello7"));
		assertEquals("Something other than 'World7'", o2.getPropertyEx("Hello7"));
	}

	public void testColor() {
		try {
			o.setTextColor(null);
			o.setBorderColor(null);
			fail("Shouldn't be able to set color null");
		} catch (Exception e) {
		}
	}

	public void testParent() {
		// remove
		data.remove(o);
		assertNull("removing object set parents null", o.getPathwayModel());
		assertEquals(received.size(), 1);
		assertEquals("Event type should be DELETED", received.get(0).getType(), PathwayModelEvent.DELETED);

		// re-add
		data.add(o);
		assertEquals("adding sets parent", o.getPathwayModel(), data);
		assertEquals(received.size(), 2);
		assertEquals("Event type should be ADDED", received.get(1).getType(), PathwayModelEvent.ADDED);
	}

	/**
	 * Test graphRef's and graphId's
	 *
	 */
	public void testRef() {
		assertTrue("query non-existing list of ref", data.getReferringObjects("abcde").size() == 0);

		// create link
		o.setElementId("1");
		l.setStartElementRef("1");
		assertTrue("reference created", data.getReferringObjects("1").contains(l.getStartLinePoint()));

		l.setStartElementRef("2");
		assertTrue("reference removed", data.getReferringObjects("1").size() == 0);

		DataNode o2 = new DataNode("", DataNodeType.UNDEFINED);
		data.add(o2);

		// create link in opposite order
		o.setElementId("2");
		l.setEndElementRef("2");
		assertTrue("reference created (2)", data.getReferringObjects("2").contains(l.getEndLinePoint()));
	}

	/**
	 * test that Xref and XrefWithSymbol obey the equals contract
	 */
	public void testXRefEquals() {
		Object[] testList = new Object[] { new Xref("1007_at", DataSource.getExistingByFullName("Affy")),
				new Xref("3456", DataSource.getExistingByFullName("Affy")),
				new Xref("1007_at", DataSource.getExistingByFullName("Entrez Gene")),
				new Xref("3456", DataSource.getExistingByFullName("Entrez Gene")),
				new Xref("3456", DataSource.getExistingByFullName("Entrez Gene")),
				new XrefWithSymbol("3456", DataSource.getExistingByFullName("Entrez Gene"), "INSR"),
				new XrefWithSymbol("3456", DataSource.getExistingByFullName("Entrez Gene"), "Insulin Receptor"), };

		for (int i = 0; i < testList.length; ++i) {
			Object refi = testList[i];
			// equals must be reflexive
			assertTrue(refi.equals(refi));
			// never equal to null
			assertFalse(refi.equals(null));
		}
		for (int i = 1; i < testList.length; ++i)
			for (int j = 0; j < i; ++j) {
				// equals must be symmetric
				Object refi = testList[i];
				Object refj = testList[j];
				assertEquals("Symmetry fails for " + refj + " and " + refi, refi.equals(refj), refj.equals(refi));

				// hashcode contract
				if (refi.equals(refj)) {
					assertEquals(refi.hashCode(), refj.hashCode());
				}
			}
		// equals must be transitive
		for (int i = 2; i < testList.length; ++i)
			for (int j = 1; j < i; ++j)
				for (int k = 0; k < j; ++k) {
					Object refi = testList[i];
					Object refj = testList[j];
					Object refk = testList[k];
					if (refi.equals(refj) && refj.equals(refk)) {
						assertTrue(refk.equals(refi));
					}
					if (refj.equals(refk) && refk.equals(refi)) {
						assertTrue(refi.equals(refj));
					}
					if (refk.equals(refi) && refi.equals(refj)) {
						assertTrue(refk.equals(refj));
					}
				}
	}

	/**
	 * Test for maintaining list of unique id's per Pathway.
	 *
	 */
	public void testRefUniq() {
		String src = "123123";
		String s1 = src.substring(3, 6);
		String s2 = src.substring(0, 3);
		assertFalse("s1 should not be the same reference as s2", s1 == s2);
		assertTrue("s1 should be equal to s2", s1.equals(s2));

		// test for uniqueness
		o.setElementId(s1);

		PathwayElement o2 = new DataNode("", DataNodeType.UNDEFINED);
		data.add(o2);
		assertSame(o.getPathwayModel(), o2.getPathwayModel());
		assertEquals("Setting graphId on first element", o.getElementId(), "123");
		try {
			o2.setElementId(s2);
			// try setting the same id again
			fail("shouldn't be able to set the same id twice");
		} catch (IllegalArgumentException e) {
		}

		// test random id
		String x = data.getUniqueElementId();
		try {
			// test that we can use it as unique id
			o.setElementId(x);
			assertEquals(x, o.getElementId());
			// test that we can't use the same id twice
			o2.setElementId(x);
			fail("shouldn't be able to set the same id twice");
		} catch (IllegalArgumentException e) {
		}

		// test that a second random id is unique again
		x = data.getUniqueElementId();
		o2.setElementId(x);
		assertEquals(x, o2.getElementId());

		// test setting id first, then parent
		PathwayElement o3 = new DataNode("", DataNodeType.UNDEFINED);
		x = data.getUniqueElementId();
		o3.setElementId(x);
		data.add(o3);
		assertEquals(o3.getElementId(), x);

		try {
			PathwayElement o4 = new DataNode("", DataNodeType.UNDEFINED);
			// try setting the same id again
			o4.setElementId(x);
			data.add(o4);
			fail("shouldn't be able to set the same id twice");
		} catch (IllegalArgumentException e) {
		}
	}

	public void testRef2() {
		o.setElementId("1");

		PathwayElement o2 = new DataNode("", DataNodeType.UNDEFINED);
		// note: parent not set yet!
		o2.setElementId("3");
		data.add(o2); // reference should now be created

		assertNull("default endGraphRef is null", l.getEndGraphRef());

		l.setEndGraphRef("3");

		assertTrue("reference created through adding", data.getReferringObjects("3").contains(l.getEndLinePoint()));
	}

	public void testXml2007() throws IOException, ConverterException {
		File testFile = new File(PATHVISIO_BASEDIR, "testData/test.gpml");
		assertTrue(testFile.exists());
		data.readFromXml(testFile, false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);

		File temp = File.createTempFile("data.test", ".gpml");
		temp.deleteOnExit();
		data.writeToXml(temp, false);
	}

	public void testWrongFormat() {
		try {
			data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.mapp"), false);
			fail("Loading wrong format, Exception expected");
		} catch (Exception e) {
		}
	}

	public void testXml2008a() throws IOException, ConverterException {
		File testFile = new File(PATHVISIO_BASEDIR, "testData/test2.gpml");
		assertTrue(testFile.exists());
		data.readFromXml(testFile, false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".gpml");
		temp.deleteOnExit();
		data.writeToXml(temp, false);
	}

	// bug 440: valid gpml file is rejected
	// because it doesn't contain Pathway.Graphics
	public void testBug440() throws ConverterException {
		try {
			data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/nographics-test.gpml"), false);
		} catch (ConverterException e) {
			fail("No converter exception expected");
		}
	}

	/**
	 * test exporting of .svg
	 */
	public void testSvg() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".svg");
		temp.deleteOnExit();

		data.writeToSvg(temp);
	}

	/**
	 * test exporting of .png
	 */
	public void testPng() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".png");
		temp.deleteOnExit();

		BatikImageExporter exporter = new BatikImageExporter(BatikImageExporter.TYPE_PNG);
		exporter.doExport(temp, data);
	}

	/**
	 * test exporting of .png
	 */
	public void testPng2() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".png");
		temp.deleteOnExit();

		RasterImageExporter exporter = new RasterImageExporter(BatikImageExporter.TYPE_PNG);
		exporter.doExport(temp, data);
	}

	/**
	 * test exporting of .pdf
	 */
	public void testPdf() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".pdf");
		temp.deleteOnExit();

		BatikImageExporter exporter = new BatikImageExporter(BatikImageExporter.TYPE_PDF);
		exporter.doExport(temp, data);
	}

	/**
	 * test exporting of .txt
	 */
	public void testTxt() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".txt");
		temp.deleteOnExit();

		DataNodeListExporter exporter = new DataNodeListExporter();
		exporter.doExport(temp, data);
	}

	/**
	 * test exporting of .pwf
	 */
	public void testPwf() throws IOException, ConverterException {
		data.readFromXml(new File(PATHVISIO_BASEDIR, "testData/test.gpml"), false);
		assertTrue("Loaded a bunch of objects from xml", data.getPathwayObjects().size() > 20);
		File temp = File.createTempFile("data.test", ".pwf");
		temp.deleteOnExit();

		EUGeneExporter exporter = new EUGeneExporter();
		exporter.doExport(temp, data);
	}

	/**
	 * Test that there is one and only one Pathway object
	 *
	 */
	public void testMappInfo() {
		Pathway mi;

		mi = data.getPathway();
		assertTrue(data.getPathwayObjects().contains(mi));
		assertNotNull(mi);

		// test that adding a new mappinfo object replaces the old one.
		Pathway mi2 = new Pathway();
		data.add(mi2);
		assertSame("MappInfo should be replaced", data.getPathway(), mi2);
		assertNotSame("Old MappInfo should be gone", data.getPathway(), mi);
		assertNull("Old MappInfo should not have a parent anymore", mi.getPathwayModel());
		assertSame("New MappInfo should now have a parent", mi2.getPathwayModel(), data);

		mi = data.getPathway();
		try {
			data.remove(mi);
			fail("Shouldn't be able to remove mappinfo object!");
		} catch (IllegalArgumentException e) {
		}
	}

	public void testValidator() throws IOException {
		File tmp = File.createTempFile("test", ".gpml");
		o.setCenterX(50.0);
		o.setCenterY(50.0);
		DefaultTemplates.setInitialSize(o);
		o.setElementId(data.getUniqueElementId());
		Interaction o2 = new Interaction();
		o2.setStartLinePointX(10.0);
		o2.setStartLinePointY(10.0);
		DefaultTemplates.setInitialSize(o2);
		data.add(o2);
		Label o3 = new Label("");
		o3.setCenterX(100.0);
		o3.setCenterY(50);
		o3.setElementId(data.getUniqueElementId());
		data.add(o3);
		PathwayElement mi;

		mi = data.getPathway();
		assertTrue("Mi shouldn't be null", mi != null);
		try {
			data.writeToXml(tmp, false);
		} catch (ConverterException e) {
			e.printStackTrace();
			fail("Exception while writing newly created pathway");
		}
	}

	// event listener
	// receives events generated on objects o and data
	public void gmmlObjectModified(PathwayModelEvent e) {
		// store all received events
		received.add(e);
	}

	public void gmmlObjectModified(PathwayObjectEvent e) {
		receivedElementEvents.add(e);
	}

	public void pathwayModified(PathwayModelEvent e) {
		gmmlObjectModified(e);
	}

	/**
	 * Dangling references, pointing to nothing, can occur in theory. These should
	 * be removed.
	 */
	public void testDanglingRef() throws IOException, ConverterException {
		// create a dangling ref
		assertEquals(data.fixReferences(), 0);

		l.setStartElementRef("dangle");

		File temp = File.createTempFile("data.test", ".gpml");
		temp.deleteOnExit();

		assertEquals(data.fixReferences(), 1);
		assertEquals(data.fixReferences(), 0);

		// should still validate
		try {
			data.writeToXml(temp, true);
		} catch (ConverterException e) {
			e.printStackTrace();
			fail("Dangling reference should have been removed");
		}
	}
}

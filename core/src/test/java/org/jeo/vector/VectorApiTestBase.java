/* Copyright 2013 The jeo project. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jeo.vector;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Set;

import org.jeo.TestData;
import org.jeo.data.Cursor;
import org.jeo.geom.Envelopes;
import org.jeo.geom.Geom;
import org.jeo.proj.Proj;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Point;

/**
 * Abstract test case that exercises all aspects of the {@link org.jeo.vector.VectorDataset} interface.
 * <p>
 * This test uses the {@link TestData#states()} dataset as a basis for testing and test implementors
 * must override {@link #createVectorData()} and return an instance backed by the states data.
 * </p>
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public abstract class VectorApiTestBase {

    VectorDataset data;

    @Before
    public final void setUp() throws Exception {
        init();
        data = createVectorData();
    }

    protected void init() throws Exception {
    }

    protected abstract VectorDataset createVectorData() throws Exception;

    @Test
    public void testGetName() {
        assertEquals("states", data.name());
    }

    @Test
    public void testSchema() throws IOException {
        Schema schema = data.schema();
        assertNotNull(schema);

        assertNotNull(schema.geometry());
    }

    @Test
    public void testBounds() throws IOException {
        Envelope bbox = data.bounds();
        assertNotNull(bbox);

        assertEquals(-124.73, bbox.getMinX(), 0.01);
        assertEquals(24.96, bbox.getMinY(), 0.01);
        assertEquals(-66.96, bbox.getMaxX(), 0.01);
        assertEquals(49.37, bbox.getMaxY(), 0.01);
    }

    @Test
    public void testCRS() throws IOException {
        CoordinateReferenceSystem crs = data.crs();
        assertNotNull(crs);

        CoordinateReferenceSystem geo = Proj.EPSG_4326;
        Point p = Proj.reproject(Geom.point(115.37, 51.08), crs, geo);
        assertEquals(115.37, p.getX(), 0.01);
        assertEquals(51.08, p.getY(), 0.01);
    }

    @Test
    public void testCount() throws IOException {
        // count all
        assertEquals(49, data.count(new VectorQuery()));

        // count within bounds
        Set<String> abbrs = Sets.newHashSet("MO", "OK", "TX", "NM", "AR", "LA"); 

        Envelope bbox = new Envelope(-106.649513, -93.507217, 25.845198, 36.493877);
        assertEquals(abbrs.size(), data.count(new VectorQuery().bounds(bbox)));

        // count with spatial filters
        assertEquals(abbrs.size(), data.count(new VectorQuery().filter(String.format("INTERSECTS(%s, %s)",
            data.schema().geometry().name(), Envelopes.toPolygon(bbox)))));

        // count with attribute filters
        assertEquals(1, data.count(new VectorQuery().filter("STATE_NAME = 'Texas'")));
        assertEquals(48, data.count(new VectorQuery().filter("STATE_NAME <> 'Texas'")));
        assertEquals(2, data.count(new VectorQuery().filter("P_MALE > P_FEMALE")));
        assertEquals(3, data.count(new VectorQuery().filter("P_MALE >= P_FEMALE")));

        // count with logical filters
        assertEquals(1, data.count(new VectorQuery().filter("P_MALE > P_FEMALE AND SAMP_POP > 200000")));
        assertEquals(5, data.count(new VectorQuery().filter("P_MALE > P_FEMALE OR SAMP_POP > 2000000")));
        assertEquals(1, data.count(new VectorQuery().filter("P_MALE > P_FEMALE AND NOT SAMP_POP > 200000")));

        // count with id filters
        String fid = fidFor(data, "STATE_NAME = 'Texas'");
        assertEquals(1, data.count(new VectorQuery().filter(String.format("IN ('%s')", fid))));
    }

    @Test
    public void testCursorRead() throws Exception {
        // all
        Assert.assertEquals(49, data.cursor(new VectorQuery()).count());

        // limit offset
        assertEquals(39, data.cursor(new VectorQuery().offset(10)).count());
        assertEquals(10, data.cursor(new VectorQuery().limit(10)).count());

        // bounds
        Envelope bbox = new Envelope(-106.649513, -93.507217, 25.845198, 36.493877);
        assertCovered(data.cursor(new VectorQuery().bounds(bbox)), "MO", "OK", "TX", "NM", "AR", "LA");

        // spatial filter
        assertCovered(data.cursor(new VectorQuery().filter(String.format("INTERSECTS(%s, %s)",
            data.schema().geometry().name(), Envelopes.toPolygon(bbox)))),
            "MO", "OK", "TX", "NM", "AR", "LA");

        // comparison filter
        assertCovered(data.cursor(new VectorQuery().filter("STATE_NAME = 'Texas'")), "TX");
        assertNotCovered(data.cursor(new VectorQuery().filter("STATE_NAME <> 'Texas'")), "TX");
        assertCovered(data.cursor(new VectorQuery().filter("P_MALE > P_FEMALE")), "NV", "CA");
        assertCovered(data.cursor(new VectorQuery().filter("P_MALE >= P_FEMALE")), "NV", "CA", "WY");

        // logic filters
        assertCovered(
            data.cursor(new VectorQuery().filter("P_MALE > P_FEMALE AND SAMP_POP > 200000")), "CA");
        assertCovered(data.cursor(new VectorQuery().filter("P_MALE > P_FEMALE OR SAMP_POP > 2000000")),
            "TX", "NY", "PA", "NV", "CA");
        assertCovered(
            data.cursor(new VectorQuery().filter("P_MALE > P_FEMALE AND NOT SAMP_POP > 200000")), "NV");

        // id filter
        String fid = fidFor(data, "STATE_NAME = 'Texas'");
        assertCovered(data.cursor(new VectorQuery().filter(String.format("IN ('%s')", fid))), "TX");

        // in filter
        assertCovered(data.cursor(new VectorQuery().filter("STATE_NAME IN ('Texas','Iowa')")), "TX", "IA");

        // between
        assertCovered(
             data.cursor(new VectorQuery().filter(String.format("SAMP_POP BETWEEN %s AND %s", 70000, 80000))), "DC");

        // math
        assertCovered(data.cursor(new VectorQuery().filter("SAMP_POP / 2 = 36348")), "DC");
        assertCovered(data.cursor(new VectorQuery().filter("(P_FEMALE - P_MALE) > .05")), "DC");

        // like
        assertCovered(data.cursor(new VectorQuery().filter("STATE_NAME LIKE 'Calif%'")), "CA");

        // null
        assertCount(49, data, "P_MALE IS NOT NULL");
        assertCount(0, data, "P_MALE IS NULL");

        // missing properties
        assertCount(0, data, "MISSING IS NULL");
        assertCount(0, data, "MISSING > 5");
        assertCount(0, data, "MISSING + 5 > 5");
        assertCount(0, data, "EQUALS(MISSING, POINT(0 0))");
        assertCount(49, data, "MISSING > 5 OR P_MALE IS NOT NULL");
    }

    @Test
    public void testFeature() throws Exception {
        Cursor<Feature> cursor = data.cursor(new VectorQuery());
        Feature next;
        try {
            assertTrue(cursor.hasNext());
            next = cursor.next();
        } finally {
            cursor.close();
        }
        assertTrue(next.has(next.schema().geometry().name()));
        assertTrue(next.has("STATE_NAME"));
        assertFalse(next.has("NOT THERE AT ALL"));

        // add a query and check all fields returned.
        // ensures attribute filtering operations don't remove erroneously
        cursor = data.cursor(new VectorQuery().filter("STATE_ABBR = 'CA'"));
        assertTrue(cursor.hasNext());
        Feature feature = cursor.next();
        assertEquals(data.schema().getFields().size(), feature.schema().size());
        for (Field f: data.schema().getFields()) {
            assertNotNull(feature.schema().field(f.name()));
        }
    }

    @Test
    public void testFeatureFields() throws Exception {
        // reverse the order as they appear in gpkg/postgis schema
        Cursor<Feature> cursor = data.cursor(new VectorQuery().fields("STATE_NAME", "SAMP_POP"));
        Feature next;
        try {
            assertTrue(cursor.hasNext());
            next = cursor.next();
        } finally {
            cursor.close();
        }
        assertTrue(next.map().size() == 2);
        // make sure query reduction doesn't result in missing id but hard to
        // test specifics across drivers as they return differing values now
        assertTrue(next.id() != null && next.id().length() > 0);
        // this should be here but hard to test specific value as order varies
        assertTrue(next.get("STATE_NAME") != null);
        assertTrue(next.get("SAMP_POP") != null);


        // reduced fields but filter referencing other field
        cursor = data.cursor(new VectorQuery().fields("STATE_NAME", "SAMP_POP").filter("STATE_ABBR = 'CA'"));
        try {
            assertTrue(cursor.hasNext());
            next = cursor.next();
        } finally {
            cursor.close();
        }
        assertTrue(next.map().size() == 2);
        assertEquals("California", next.get("STATE_NAME"));
        assertEquals(3792553, ((Number)next.get("SAMP_POP")).intValue());
    }

    void assertNotCovered(Cursor<Feature> cursor, String... abbrs) throws IOException {
        final Set<String> set = Sets.newHashSet(abbrs);
        try {
            Iterables.find(cursor, new Predicate<Feature>() {
                @Override
                public boolean apply(Feature input) {
                    return set.contains(input.get("STATE_ABBR"));
                }
            });
            fail();
        }
        catch(NoSuchElementException expected) {}
    }

    void assertCovered(Cursor<Feature> cursor, String... abbrs) throws IOException {
        Set<String> set = Sets.newHashSet(abbrs);
        int count = 0;
        while(cursor.hasNext()) {
            set.remove(cursor.next().get("STATE_ABBR"));
            count++;
        }
        assertTrue("expected empty set, found " + set, set.isEmpty());
        assertEquals(abbrs.length, count);
    }

    void assertCount(int expected, VectorDataset dataSet, String filter) throws IOException {
        VectorQuery q = new VectorQuery().filter(filter);
        assertEquals(expected, dataSet.count(q));
        assertEquals(expected, dataSet.cursor(q).count());
    }

    String fidFor(VectorDataset dataset, String filter) throws IOException {
        Cursor<Feature> c = dataset.cursor(new VectorQuery().filter(filter));
        try {
            assertTrue(c.hasNext());
            return c.next().id();
        }
        finally {
            c.close();
        }
    }
}

/* Copyright 2014 The jeo project. All rights reserved.
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
package org.jeo.geopkg;

import static org.jeo.Tests.unzip;

import java.io.File;

import org.jeo.Tests;
import org.jeo.tile.TileApiTestBase;
import org.jeo.tile.TileDataset;
import org.junit.After;

public class GeoPkgTileApiTest extends TileApiTestBase {

    GeoPkgWorkspace gpkg;

    @Override
    protected TileDataset createTileData() throws Exception {
        File dir = unzip(getClass().getResourceAsStream("ne.gpkg.zip"), Tests.newTmpDir());
        gpkg = GeoPackage.open(new File(dir, "ne.gpkg"));
        return (TileDataset) gpkg.get("tiles");
    }

    @After
    public void tearDown() {
        gpkg.close();
    }

}

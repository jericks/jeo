/* Copyright 2015 The jeo project. All rights reserved.
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
package org.jeo.protobuf;

import java.io.IOException;

import org.jeo.data.Cursor;
import org.jeo.vector.Feature;
import org.jeo.vector.FeatureCursor;
import org.jeo.vector.Schema;

public class ProtobufCursor extends FeatureCursor {

    ProtobufReader pbr;
    Schema schema;
    Feature next;

    public ProtobufCursor(ProtobufDataset data) throws IOException {
        this(data.reader());
    }

    public ProtobufCursor(ProtobufReader pbr) throws IOException {
        this.pbr = pbr;

        // skip over the schema
        schema = pbr.schema();
    }

    @Override
    public boolean hasNext() throws IOException {
        if (next == null) {
            next = pbr.feature(schema);
        }
        return next != null;
    }

    @Override
    public Feature next() throws IOException {
        try {
            return next;
        }
        finally {
            next = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (pbr != null) {
            pbr.close();
        }
        pbr = null;
    }
}

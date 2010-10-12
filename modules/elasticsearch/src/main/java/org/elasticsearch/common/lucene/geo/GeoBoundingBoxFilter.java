/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.lucene.geo;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.elasticsearch.common.lucene.docset.GetDocSet;
import org.elasticsearch.index.cache.field.data.FieldDataCache;
import org.elasticsearch.index.mapper.xcontent.geo.GeoPoint;
import org.elasticsearch.index.mapper.xcontent.geo.GeoPointFieldData;
import org.elasticsearch.index.mapper.xcontent.geo.GeoPointFieldDataType;

import java.io.IOException;

/**
 * @author kimchy (shay.banon)
 */
public class GeoBoundingBoxFilter extends Filter {

    private final Point topLeft;

    private final Point bottomRight;

    private final String fieldName;

    private final FieldDataCache fieldDataCache;

    public GeoBoundingBoxFilter(Point topLeft, Point bottomRight, String fieldName, FieldDataCache fieldDataCache) {
        this.topLeft = topLeft;
        this.bottomRight = bottomRight;
        this.fieldName = fieldName;
        this.fieldDataCache = fieldDataCache;
    }

    public Point topLeft() {
        return topLeft;
    }

    public Point bottomRight() {
        return bottomRight;
    }

    public String fieldName() {
        return fieldName;
    }

    @Override public DocIdSet getDocIdSet(IndexReader reader) throws IOException {
        final GeoPointFieldData fieldData = (GeoPointFieldData) fieldDataCache.cache(GeoPointFieldDataType.TYPE, reader, fieldName);

        //checks to see if bounding box crosses 180 degrees
        if (topLeft.lon > bottomRight.lon) {
            return new GetDocSet(reader.maxDoc()) {

                @Override public boolean isCacheable() {
                    // not cacheable for several reasons:
                    // 1. It is only relevant when _cache is set to true, and then, we really want to create in mem bitset
                    // 2. Its already fast without in mem bitset, since it works with field data
                    return false;
                }

                @Override public boolean get(int doc) throws IOException {
                    if (!fieldData.hasValue(doc)) {
                        return false;
                    }

                    if (fieldData.multiValued()) {
                        GeoPoint[] points = fieldData.values(doc);
                        for (GeoPoint point : points) {
                            if (point.lon() < 0) {
                                if (-180.0 <= point.lon() && bottomRight.lon >= point.lon()
                                        && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                                    return true;
                                }
                            } else {
                                if (topLeft.lon <= point.lon() && 180 >= point.lon()
                                        && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                                    return true;
                                }
                            }
                        }
                    } else {
                        GeoPoint point = fieldData.value(doc);
                        if (point.lon() < 0) {
                            if (-180.0 <= point.lon() && bottomRight.lon >= point.lon()
                                    && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                                return true;
                            }
                        } else {
                            if (topLeft.lon <= point.lon() && 180 >= point.lon()
                                    && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            };
        } else {
            return new GetDocSet(reader.maxDoc()) {

                @Override public boolean isCacheable() {
                    // not cacheable for several reasons:
                    // 1. It is only relevant when _cache is set to true, and then, we really want to create in mem bitset
                    // 2. Its already fast without in mem bitset, since it works with field data
                    return false;
                }

                @Override public boolean get(int doc) throws IOException {
                    if (!fieldData.hasValue(doc)) {
                        return false;
                    }

                    if (fieldData.multiValued()) {
                        GeoPoint[] points = fieldData.values(doc);
                        for (GeoPoint point : points) {
                            if (topLeft.lon <= point.lon() && bottomRight.lon >= point.lon()
                                    && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                                return true;
                            }
                        }
                    } else {
                        GeoPoint point = fieldData.value(doc);

                        if (topLeft.lon <= point.lon() && bottomRight.lon >= point.lon()
                                && topLeft.lat >= point.lat() && bottomRight.lat <= point.lat()) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
    }

    public static class Point {
        public double lat;
        public double lon;
    }
}

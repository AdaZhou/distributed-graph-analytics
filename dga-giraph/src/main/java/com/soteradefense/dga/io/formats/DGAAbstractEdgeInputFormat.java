/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soteradefense.dga.io.formats;

import org.apache.giraph.io.EdgeReader;
import org.apache.giraph.io.ReverseEdgeDuplicator;
import org.apache.giraph.io.formats.TextEdgeInputFormat;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import java.io.IOException;

/**
 * Abstract class that simplifies the setup of our EdgeInputFormat subclasses.
 *
 * All DGA analytics require data to be specified in roughly the same way, while some algorithms may require a 3rd column
 * with a weight and others may not care.  The only duty of this class is to set up the edge reverse duplicator, and extract
 * defaults or overrides from the GiraphConfiguration
 *
 * @param <E>
 */
public abstract class DGAAbstractEdgeInputFormat<E extends Writable> extends TextEdgeInputFormat<Text, E> {

    /**
     * Key we use in the GiraphConfiguration to denote our field delimiter
     */
    public static final String LINE_TOKENIZE_VALUE = "simple.edge.delimiter";

    /**
     * Default value used if no field delimiter is specified via the GiraphConfiguration
     */
    public static final String LINE_TOKENIZE_VALUE_DEFAULT = ",";

    /**
     * Key we use in the GiraphConfiguration to denote our default edge edgeValue
     */
    public static final String EDGE_VALUE = "simple.edge.value.default";

    /**
     * Configuration Identifier to use a reverse edge.
     */
    public static final String IO_EDGE_REVERSE_DUPLICATOR = "io.edge.reverse.duplicator";

    /**
     * Default Value for the reverse edge duplicator.
     */
    public static final String IO_EDGE_REVERSE_DUPLICATOR_DEFAULT = "false";

    /**
     * The create edge reader first determines if we should reverse each edge; some data sets are undirected graphs
     * and need the input format to reverse their connected nature.
     *
     * Calls the abstract method getEdgeReader() which will give us the appropriate EdgeReader for the subclass.
     * @param split
     * @param context
     * @return
     * @throws IOException
     */
    public EdgeReader<Text, E> createEdgeReader(InputSplit split, TaskAttemptContext context) throws IOException {
        String duplicator = getConf().get(IO_EDGE_REVERSE_DUPLICATOR, IO_EDGE_REVERSE_DUPLICATOR_DEFAULT);
        boolean useDuplicator = Boolean.parseBoolean(duplicator);
        EdgeReader<Text, E> reader = useDuplicator ? new ReverseEdgeDuplicator<Text, E>(getEdgeReader()) : getEdgeReader();
        return reader;
    }

    /**
     * Will be implemented in each subclass to call the appropriate instantiation of a DGAAbstractEdgeReader
     * Our EdgeReaders ultimately take the first field as sourceId, second field as targetId, and the third field, if applicable, as a weight
     *
     * Some algorithms require the third field as a weight, and other algorithms will ignore it.
     * @return
     */
    public abstract DGAAbstractEdgeReader getEdgeReader();

    /**
     * Simple implementation that offloads work of parsing to the RawEdge class and the work of casting our edgeValue as
     * a Writable class of choice to the implementing subclasses.
     * @param <E> Writable class to be stated in the implementing class.
     */
    public abstract class DGAAbstractEdgeReader<E extends Writable> extends TextEdgeReaderFromEachLineProcessed<RawEdge> {

        private String delimiter;

        private String defaultEdgeValue;

        @Override
        public void initialize(InputSplit inputSplit, TaskAttemptContext context) throws IOException, InterruptedException {
            super.initialize(inputSplit, context);
            delimiter = getConf().get(LINE_TOKENIZE_VALUE, LINE_TOKENIZE_VALUE_DEFAULT);
            defaultEdgeValue = getConf().get(EDGE_VALUE, getDefaultEdgeValue());
        }

        @Override
        protected RawEdge preprocessLine(Text line) throws IOException {
            RawEdge edge = new RawEdge(delimiter, getDefaultEdgeValue());
            edge.fromText(line);
            validateEdgeValue(edge);
            return edge;
        }

        protected abstract String getDefaultEdgeValue();

        protected abstract void validateEdgeValue(RawEdge edge) throws IOException;

        @Override
        protected Text getTargetVertexId(RawEdge edge) throws IOException {
            return new Text(edge.getTargetId());
        }

        @Override
        protected Text getSourceVertexId(RawEdge edge) throws IOException {
            return new Text(edge.getSourceId());
        }

    }

}
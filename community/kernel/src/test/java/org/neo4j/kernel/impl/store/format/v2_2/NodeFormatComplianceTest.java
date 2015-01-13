/**
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.store.format.v2_2;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.neo4j.io.pagecache.PageCache;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.io.pagecache.impl.muninn.MuninnPageCache;
import org.neo4j.kernel.DefaultIdGeneratorFactory;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.store.NeoStore;
import org.neo4j.kernel.impl.store.NodeStore;
import org.neo4j.kernel.impl.store.StoreFactory;
import org.neo4j.kernel.impl.store.format.Store;
import org.neo4j.kernel.impl.store.format.v2_2.NodeStoreFormat_v2_2.NodeRecordCursor;
import org.neo4j.kernel.impl.store.impl.TestStoreIdGenerator;
import org.neo4j.kernel.impl.store.record.NodeRecord;
import org.neo4j.kernel.impl.store.standard.StandardStore;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.kernel.monitoring.Monitors;
import org.neo4j.test.EphemeralFileSystemRule;

import static java.util.Arrays.asList;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import static org.neo4j.kernel.impl.store.NeoStore.DEFAULT_NAME;
import static org.neo4j.kernel.impl.store.StoreFactory.NODE_STORE_NAME;
import static org.neo4j.kernel.impl.store.impl.StoreMatchers.records;

/**
 * Test that the node store format can read and write the format generated by
 * {@link org.neo4j.kernel.impl.store.NodeStore}.
 */
public class NodeFormatComplianceTest
{
    @Rule
    public EphemeralFileSystemRule fsRule = new EphemeralFileSystemRule();
    private PageCache pageCache;
    private StoreFactory storeFactory;
    private final File storeDir = new File( "dir" ).getAbsoluteFile();

    @Before
    public void setup()
    {
        pageCache = new MuninnPageCache( fsRule.get(), 1024, 1024, PageCacheTracer.NULL );
        storeFactory = new StoreFactory( StoreFactory.configForStoreDir( new Config(), storeDir ),
                new DefaultIdGeneratorFactory(), pageCache, fsRule.get(), StringLogger.DEV_NULL, new Monitors() );
    }

    @Test
    public void readsRecords() throws Throwable
    {
        // Given
        NeoStore neoStore = storeFactory.createNeoStore();
        NodeStore nodeStore = neoStore.getNodeStore();

        NodeRecord expectedRecord = new NodeRecord( nodeStore.nextId(), true, false, 1, 2, 42 );
        nodeStore.updateRecord( expectedRecord );
        neoStore.close();

        // When
        StandardStore<NodeRecord, NodeRecordCursor> store = new
                StandardStore<>( new NodeStoreFormat_v2_2(), new File( storeDir, DEFAULT_NAME + NODE_STORE_NAME ),
                new TestStoreIdGenerator(), pageCache, fsRule.get(),
                StringLogger.DEV_NULL );
        store.init();
        store.start();

        // Then
        assertThat( records( store ), equalTo( asList( expectedRecord ) ) );
    }

    @Test
    public void writesRecords() throws Throwable
    {
        // Given
        storeFactory.createNeoStore().close(); // NodeStore wont start unless it's child stores exist, so creat those

        StandardStore<NodeRecord, NodeRecordCursor> store = new
                StandardStore<>( new NodeStoreFormat_v2_2(), new File( storeDir, DEFAULT_NAME + NODE_STORE_NAME ),
                new TestStoreIdGenerator(), pageCache, fsRule.get(),
                StringLogger.DEV_NULL );
        store.init();
        store.start();

        NodeRecord expectedRecord = new NodeRecord( store.allocate(), true, false, 1, 2, 42 );

        // When
        store.write( expectedRecord );
        store.stop();
        store.shutdown();

        // Then
        NodeStore nodeStore = storeFactory.newNodeStore();
        NodeRecord record = nodeStore.getRecord( expectedRecord.getId() );
        assertThat( record, equalTo( expectedRecord ) );
    }

    @Test
    public void customCursorShouldReadNextRel() throws Throwable
    {
        // Given
        StandardStore<NodeRecord, NodeRecordCursor> store = new StandardStore<>( new NodeStoreFormat_v2_2(),
                new File( storeDir, DEFAULT_NAME + NODE_STORE_NAME ),
                new TestStoreIdGenerator(), pageCache, fsRule.get(),
                StringLogger.DEV_NULL );
        store.init();
        store.start();

        long nextRel = 1337;
        NodeRecord record = new NodeRecord( store.allocate(), true, false, nextRel, 2, 42 );

        store.write( record );

        // Given I have a cursor positioned at the record I want
        NodeRecordCursor cursor = store.cursor( Store.SF_NO_FLAGS );
        cursor.position( record.getId() );

        // When
        long firstRelId = cursor.firstRelationship();

        // Then
        assertThat( firstRelId, equalTo( nextRel ) );
    }
}

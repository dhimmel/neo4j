/*
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
package org.neo4j.kernel.impl.api;

import org.neo4j.kernel.impl.api.index.ValidatedIndexUpdates;
import org.neo4j.kernel.impl.transaction.TransactionRepresentation;
import org.neo4j.kernel.impl.transaction.log.Commitment;
import org.neo4j.kernel.impl.transaction.tracing.CommitEvent;

/**
 * A chain of transactions to apply. Transactions form a linked list, each pointing to the {@link #next()}
 * or {@code null}. This design chosen for less garbage and convenience, i.e. that we pass in a number of transactions
 * while also expecting some results for each and every one of those transactions back. The results are
 * written directly into each instance instead of creating another data structure which is then returned.
 * This is an internal class so even if it mixes arguments with results it's easier to work with,
 * requires less code... and less objects.
 *
 * State and methods are divided up into two parts, one part being the responsibility of the user to manage,
 * the other part up to the commit process to manage.
 *
 * There's also currently a work-around ({@link #TRANSACTION_ID_NOT_SPECIFIED}) for up front
 * {@link ValidatedIndexUpdates index updates}, that topic is discussed elsewhere in the commit process code,
 * but basically index updates validation will go away when busting the lucene id limits so that this
 * work-around goes away.
 *
 * The access pattern looks like:
 * <ol>
 * <li>=== USER ===</li>
 * <li>Construct instances</li>
 * <li>Form the linked list using {@link #next(TransactionToApply)}</li>
 * <li>Pass into {@link TransactionCommitProcess#commit(TransactionToApply, CommitEvent, TransactionApplicationMode)}</li>
 * <li>=== COMMIT PROCESS ===</li>
 * <li>Commit, where {@link #commitment(Commitment, long)} is called to store the {@link Commitment} and transaction id</li>
 * <li>Apply, where f.ex {@link #validatedIndexUpdates(ValidatedIndexUpdates)}, {@link #commitment()},
 * {@link #transactionRepresentation()} and {@link #next()} are called</li>
 * <li>=== USER ===</li>
 * <li>Data about the commit can now also be accessed using f.ex {@link #commitment()} or {@link #transactionId()}</li>
 * </ol>
 */
public class TransactionToApply
{
    public static final long TRANSACTION_ID_NOT_SPECIFIED = 0;

    // These fields are provided by user
    private final TransactionRepresentation transactionRepresentation;
    private long transactionId;
    private TransactionToApply nextTransactionInBatch;

    // These fields are provided by commit process/storage engine
    private ValidatedIndexUpdates validatedIndexUpdates;
    private Commitment commitment;

    /**
     * Used when committing a transaction that hasn't already gotten a transaction id assigned.
     */
    public TransactionToApply( TransactionRepresentation transactionRepresentation )
    {
        this( transactionRepresentation, TRANSACTION_ID_NOT_SPECIFIED );
    }

    /**
     * Used as convenience when committing a transaction that has already gotten a transaction id assigned,
     * i.e. when replicating a transaction.
     */
    public TransactionToApply( TransactionRepresentation transactionRepresentation, long transactionId )
    {
        this.transactionRepresentation = transactionRepresentation;
        this.transactionId = transactionId;
    }

    // These methods are called by the user when building a batch
    public void next( TransactionToApply next )
    {
        nextTransactionInBatch = next;
    }

    // These methods are called by the commit process
    public Commitment commitment()
    {
        return commitment;
    }

    public long transactionId()
    {
        return transactionId;
    }

    public TransactionRepresentation transactionRepresentation()
    {
        return transactionRepresentation;
    }

    public void validatedIndexUpdates( ValidatedIndexUpdates validatedIndexUpdates )
    {
        this.validatedIndexUpdates = validatedIndexUpdates;
    }

    public ValidatedIndexUpdates validatedIndexUpdates()
    {
        return validatedIndexUpdates;
    }

    public void commitment( Commitment commitment, long transactionId )
    {
        this.commitment = commitment;
        this.transactionId = transactionId;
    }

    public TransactionToApply next()
    {
        return nextTransactionInBatch;
    }
}

/**
 * Copyright 2014 yangming.liu<liuyangming@gmail.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.openjtcc.supports.logger;

import java.util.HashSet;
import java.util.Set;

import org.bytesoft.openjtcc.archive.CompensableArchive;
import org.bytesoft.openjtcc.archive.TerminatorArchive;
import org.bytesoft.openjtcc.archive.TransactionArchive;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.supports.TransactionLogger;

/**
 * 暂不支持
 */
public class FileTransactionLoggerImpl implements TransactionLogger {

	public void beginTransaction(TransactionArchive archive) {
	}

	public void cancelService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void cleanupTerminator(TransactionContext transactionContext, TerminatorArchive archive) {
	}

	public void cleanupTransaction(TransactionArchive archive) {
	}

	public void commitService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void commitTerminator(TransactionContext transactionContext, TerminatorArchive archive) {
	}

	public void completeTransaction(TransactionArchive archive) {
	}

	public void confirmService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void delistService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void enlistService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public Set<TransactionArchive> getLoggedTransactionSet() {
		return new HashSet<TransactionArchive>();
	}

	public void prepareTerminator(TransactionContext transactionContext, TerminatorArchive archive) {
	}

	public void prepareTransaction(TransactionArchive archive) {
	}

	public void registerTerminator(TransactionContext transactionContext, TerminatorArchive archive) {
	}

	public void rollbackService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void rollbackTerminator(TransactionContext transactionContext, TerminatorArchive archive) {
	}

	public void updateService(TransactionContext transactionContext, CompensableArchive archive) {
	}

	public void updateTransaction(TransactionArchive archive) {
	}

}

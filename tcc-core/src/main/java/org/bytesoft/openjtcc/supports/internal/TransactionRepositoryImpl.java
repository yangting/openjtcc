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
package org.bytesoft.openjtcc.supports.internal;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bytesoft.openjtcc.TransactionImpl;
import org.bytesoft.openjtcc.supports.TransactionLogger;
import org.bytesoft.openjtcc.supports.TransactionRepository;
import org.bytesoft.openjtcc.xa.XidImpl;

public class TransactionRepositoryImpl implements TransactionRepository {
	private final Map<XidImpl, TransactionImpl> xidToTxMap = new ConcurrentHashMap<XidImpl, TransactionImpl>();
	private final Map<XidImpl, TransactionImpl> xidToErrTxMap = new ConcurrentHashMap<XidImpl, TransactionImpl>();
	private final TransactionLoggerImpl transactionLoggerWrapper = new TransactionLoggerImpl();

	public void putTransaction(XidImpl globalXid, TransactionImpl transaction) {
		this.xidToTxMap.put(globalXid, transaction);
	}

	public TransactionImpl getTransaction(XidImpl globalXid) {
		return this.xidToTxMap.get(globalXid);
	}

	public TransactionImpl removeTransaction(XidImpl globalXid) {
		return this.xidToTxMap.remove(globalXid);
	}

	public TransactionLogger getTransactionLogger() {
		return this.transactionLoggerWrapper;
	}

	public void setTransactionLogger(TransactionLogger transactionLogger) {
		if (transactionLogger == null) {
			this.transactionLoggerWrapper.setDelegate(TransactionLogger.defaultTransactionLogger);
		} else {
			this.transactionLoggerWrapper.setDelegate(transactionLogger);
		}
	}

	public void putErrorTransaction(XidImpl globalXid, TransactionImpl transaction) {
		this.xidToErrTxMap.put(globalXid, transaction);
	}

	public TransactionImpl getErrorTransaction(XidImpl globalXid) {
		return this.xidToErrTxMap.get(globalXid);
	}

	public TransactionImpl removeErrorTransaction(XidImpl globalXid) {
		return this.xidToErrTxMap.remove(globalXid);
	}

	public Set<TransactionImpl> getErrorTransactionSet() {
		return new HashSet<TransactionImpl>(this.xidToErrTxMap.values());
	}

	public Set<TransactionImpl> getActiveTransactionSet() {
		return new HashSet<TransactionImpl>(this.xidToTxMap.values());
	}

}

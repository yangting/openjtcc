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
package org.bytesoft.openjtcc.archive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.xa.XidImpl;

public class TransactionArchive {
	protected TransactionStatus transactionStatus;
	protected TransactionContext transactionContext;

	protected final Map<XidImpl, CompensableArchive> xidToNativeSvcMap = new ConcurrentHashMap<XidImpl, CompensableArchive>();
	protected final Map<String, TerminatorArchive> appToTerminatorMap = new ConcurrentHashMap<String, TerminatorArchive>();

	public TransactionContext getTransactionContext() {
		return transactionContext;
	}

	public void setTransactionContext(TransactionContext transactionContext) {
		this.transactionContext = transactionContext;
	}

	public TransactionStatus getTransactionStatus() {
		return transactionStatus;
	}

	public void setTransactionStatus(TransactionStatus transactionStatus) {
		this.transactionStatus = transactionStatus;
	}

	public Map<XidImpl, CompensableArchive> getXidToNativeSvrMap() {
		return xidToNativeSvcMap;
	}

	public Map<String, TerminatorArchive> getAppToTerminatorMap() {
		return appToTerminatorMap;
	}

}

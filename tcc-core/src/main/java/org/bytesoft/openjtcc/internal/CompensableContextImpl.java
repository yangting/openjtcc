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
package org.bytesoft.openjtcc.internal;

import java.io.Serializable;

import org.bytesoft.openjtcc.CompensableContext;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.ByteUtils;

public class CompensableContextImpl<T extends Serializable> implements CompensableContext<T> {
	private TransactionContext transactionContext;
	private TransactionStatus transactionStatus;
	private XidImpl branchXid;
	private T compensableVariable;

	public String getGlobalTransactionId() {
		return ByteUtils.byteArrayToString(this.branchXid.getGlobalTransactionId());
	}

	public T getCompensableVariable() {
		return this.compensableVariable;
	}

	public boolean isCoordinator() {
		if (transactionContext.isCoordinator()) {
			XidImpl globalXid = this.transactionContext.getGlobalXid();
			XidImpl branchXid = this.transactionContext.getCurrentXid();
			if (globalXid.equals(branchXid)) {
				// TODO
				return true;
			}
		}
		return false;
	}

	public boolean isRollbackOnly() {
		return transactionStatus.isMarkedRollbackOnly();
	}

	public void setRollbackOnly() throws IllegalStateException {
		this.transactionStatus.markStatusRollback();
	}

	public void setCompensableVariable(T variable) throws IllegalStateException {
		this.compensableVariable = variable;
	}

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

	public XidImpl getBranchXid() {
		return branchXid;
	}

	public void setBranchXid(XidImpl branchXid) {
		this.branchXid = branchXid;
	}

}

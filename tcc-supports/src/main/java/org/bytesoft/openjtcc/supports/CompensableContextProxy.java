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
package org.bytesoft.openjtcc.supports;

import java.io.Serializable;

import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.CompensableContext;
import org.bytesoft.openjtcc.TransactionImpl;
import org.bytesoft.openjtcc.TransactionManagerImpl;

public class CompensableContextProxy<T extends Serializable> implements CompensableContext<T> {
	private TransactionManager transactionManager;

	public String getGlobalTransactionId() {
		CompensableContext<T> context = this.getCompensableContext();
		return context.getGlobalTransactionId();
	}

	public T getCompensableVariable() {
		CompensableContext<T> context = this.getCompensableContext();
		return context.getCompensableVariable();
	}

	public boolean isCoordinator() {
		CompensableContext<T> context = this.getCompensableContext();
		return context.isCoordinator();
	}

	public boolean isRollbackOnly() {
		CompensableContext<T> context = this.getCompensableContext();
		return context.isRollbackOnly();
	}

	public void setRollbackOnly() throws IllegalStateException {
		CompensableContext<T> context = this.getCompensableContext();
		context.setRollbackOnly();
	}

	public void setCompensableVariable(T arg0) throws IllegalStateException {
		TransactionImpl transaction = this.getTransaction();
		if (transaction == null) {
			throw new IllegalStateException();
		}

		CompensableContext<T> context = this.getCompensableContext();
		context.setCompensableVariable(arg0);
	}

	@SuppressWarnings("unchecked")
	public CompensableContext<T> getCompensableContext() throws IllegalStateException {
		TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
		TransactionImpl transaction = txManager.getCurrentTransaction();
		if (transaction == null) {
			throw new IllegalStateException();
		} else {
			return (CompensableContext<T>) transaction.getCompensableContext();
		}
	}

	protected TransactionImpl getTransaction() {
		try {
			TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
			TransactionImpl transaction = txManager.getCurrentTransaction();
			return transaction;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

}

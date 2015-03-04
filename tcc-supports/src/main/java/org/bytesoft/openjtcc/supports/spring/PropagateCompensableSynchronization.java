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
package org.bytesoft.openjtcc.supports.spring;

import java.io.Serializable;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.Compensable;
import org.bytesoft.openjtcc.TransactionImpl;
import org.bytesoft.openjtcc.TransactionManagerImpl;
import org.bytesoft.openjtcc.supports.AbstractSynchronization;
import org.bytesoft.openjtcc.xa.XidImpl;

public class PropagateCompensableSynchronization<T extends Serializable> extends AbstractSynchronization {
	private TransactionManager transactionManager;
	private Compensable<T> compensable;

	public PropagateCompensableSynchronization(Compensable<T> service) {
		this.compensable = service;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void afterCreation(XidImpl xid) {

		try {
			TransactionManagerImpl txManager = (TransactionManagerImpl) this.transactionManager;
			TransactionImpl transaction = txManager.getCurrentTransaction();

			transaction.enlistService((Compensable<Serializable>) this.compensable);
			transaction.registerSynchronization(this);
		} catch (IllegalStateException ex) {
		} catch (SystemException ex) {
		} catch (RuntimeException ex) {
		} catch (RollbackException ex) {
		}
	}

	@Override
	public void beforeCompletion(XidImpl xid) {
	}

	@Override
	public void afterCompletion(XidImpl xid, int status) {
	}

	public void setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

}

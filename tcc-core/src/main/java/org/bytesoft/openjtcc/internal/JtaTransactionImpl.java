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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.xa.XidFactory;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.CommonUtils;

public class JtaTransactionImpl implements Transaction {
	private static final Logger logger = Logger.getLogger("openjtcc");
	private transient final Set<JtaSynchronizationImpl> synchronizations = new HashSet<JtaSynchronizationImpl>();

	private TransactionStatus transactionStatus = new TransactionStatus();
	private boolean markedRollbackOnly = false;
	private final transient TransactionContext transactionContext;
	private transient JtaResourceCoordinator resourceCoordinator;
	private transient XidFactory xidFactory;

	public JtaTransactionImpl(TransactionContext context) {
		this.transactionContext = context;
	}

	public JtaTransactionImpl(TransactionContext context, TransactionStatus txStatus) {
		this(context);
		this.transactionStatus = txStatus;
	}

	public void initialize() {
		JtaResourceCoordinator rc = new JtaResourceCoordinator();
		rc.setTransactionContext(this.transactionContext);
		rc.setXidFactory(this.xidFactory);
		this.resourceCoordinator = rc;
	}

	private void beforeCompletion() {
		Iterator<JtaSynchronizationImpl> itr = this.synchronizations.iterator();
		while (itr.hasNext()) {
			JtaSynchronizationImpl sync = itr.next();
			try {
				sync.beforeCompletion();
			} catch (Exception ex) {
				logger.log(Level.WARNING,
						String.format("error occurred in beforeCompletion, synchronization: %s.", sync), ex);
			}
		}
	}

	private void afterCompletion() {
		int status = this.transactionStatus.getTransactionStatus();
		Iterator<JtaSynchronizationImpl> itr = this.synchronizations.iterator();
		while (itr.hasNext()) {
			JtaSynchronizationImpl sync = itr.next();
			try {
				sync.afterCompletion(status);
			} catch (Exception ex) {
				logger.log(Level.WARNING, String.format(
						"error occurred in afterCompletion, status: %s, synchronization: %s.", status, sync), ex);
			}
		}
	}

	public synchronized void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
			SecurityException, SystemException {
		if (this.transactionStatus.isRolledBack()) {
			throw new RollbackException();
		} else if (this.transactionStatus.isRollingBack()) {
			this.rollback();
			throw new HeuristicRollbackException();
		} else if (this.transactionStatus.isMarkedRollbackOnly()) {
			this.rollback();
			throw new HeuristicRollbackException();
		} else if (this.transactionStatus.isCommitted()) {
			return;
		}

		logger.info(String.format("\t[%s] internal-tx-commit-start", this.transactionContext.getGlobalXid()));
		try {
			this.beforeCompletion();
			this.delistParticipantXAResources();
			this.transactionStatus.setStatusPreparing();

			boolean rollbackRequired = this.resourceCoordinator.xaPrepare();

			this.transactionStatus.setStatusPrepared();

			this.transactionStatus.setStatusCommiting();

			if (rollbackRequired) {
				try {
					this.rollback();
				} catch (IllegalStateException ex) {
					SystemException sysex = new SystemException();
					sysex.initCause(ex);
					throw sysex;
				} catch (SystemException ex) {
					throw ex;
				}
				throw new HeuristicRollbackException();
			} else {
				this.resourceCoordinator.xaCommit();
				this.transactionStatus.setStatusCommitted();
			}
		} finally {
			this.afterCompletion();
			logger.info(String.format("\t[%s] internal-tx-commit-end", this.transactionContext.getGlobalXid()));
		}
	}

	public synchronized void suspendAllResource() throws IllegalStateException, SystemException {
		this.resourceCoordinator.suspendAllResource();
	}

	public synchronized void resumeAllResource() throws IllegalStateException, SystemException {
		this.resourceCoordinator.resumeAllResource();
	}

	public synchronized boolean delistResource(XAResource xares, int flags) throws IllegalStateException,
			SystemException {
		return this.resourceCoordinator.delistResource(xares, flags);
	}

	public synchronized boolean enlistResource(XAResource xares) throws IllegalStateException, RollbackException,
			SystemException {
		try {
			long expired = this.transactionContext.getExpiredTime();
			long created = this.transactionContext.getCreatedTime();
			int timeout = (int) ((expired - created) / 1000L);
			xares.setTransactionTimeout(timeout);
		} catch (XAException e) {
		} catch (RuntimeException e) {
		}
		return this.resourceCoordinator.enlistResource(xares);
	}

	public synchronized void delistParticipantXAResources() {
		this.resourceCoordinator.delistParticipantXAResources();
	}

	public int getStatus() throws SystemException {
		return this.transactionStatus.getTransactionStatus();
	}

	public void registerSynchronization(Synchronization sync) throws IllegalStateException, RollbackException,
			SystemException {
		if (sync == null) {
			throw new SystemException("Synchronization cannot be null!");
		} else if (this.markedRollbackOnly) {
			throw new RollbackException();
		} else if (this.transactionStatus.isActive() == false) {
			throw new IllegalStateException();
		}
		this.synchronizations.add(new JtaSynchronizationImpl(this.transactionContext.getGlobalXid(), sync));
	}

	public synchronized void rollback() throws IllegalStateException, SystemException {
		if (this.transactionStatus.isRolledBack()) {
			return;
		} else if (this.transactionStatus.isRollingBack()) {
			// ignore
		} else if (this.transactionStatus.isMarkedRollbackOnly()) {
			// ignore
		} else if (this.transactionStatus.isCommitted()) {
			throw new IllegalStateException();
		}

		try {
			this.beforeCompletion();
			this.delistParticipantXAResources();
			this.transactionStatus.setStatusRollingback();

			this.resourceCoordinator.xaRollback();
			this.transactionStatus.setStatusRolledback();
		} finally {
			this.afterCompletion();
		}
	}

	public synchronized void setRollbackOnly() throws IllegalStateException, SystemException {
		this.transactionStatus.markStatusRollback();
		this.markedRollbackOnly = true;
	}

	public void setXidFactory(XidFactory xidFactory) {
		this.xidFactory = xidFactory;
	}

	@Override
	public int hashCode() {
		int hash = 13;
		XidImpl branchXid = this.transactionContext == null ? null : this.transactionContext.getGlobalXid();
		hash += 17 * (branchXid == null ? 0 : 17 * branchXid.hashCode());
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (this.getClass().equals(obj.getClass()) == false) {
			return false;
		}
		JtaTransactionImpl that = (JtaTransactionImpl) obj;
		XidImpl thisXid = this.transactionContext == null ? null : this.transactionContext.getGlobalXid();
		XidImpl thatXid = that.transactionContext == null ? null : that.transactionContext.getGlobalXid();
		boolean xidEquals = CommonUtils.equals(thisXid, thatXid);
		return xidEquals;
	}

	public String toString() {
		return String.format("InternalTransactionImpl[xid: %s, status: %s]", this.transactionContext.getCurrentXid(),
				this.transactionStatus.getStatusCode());
	}

}

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
package org.bytesoft.openjtcc;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.transaction.HeuristicCommitException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.xa.XAResource;

import org.bytesoft.openjtcc.archive.CompensableArchive;
import org.bytesoft.openjtcc.archive.TerminatorArchive;
import org.bytesoft.openjtcc.archive.TransactionArchive;
import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.internal.CompensableContextImpl;
import org.bytesoft.openjtcc.internal.JtaTransactionImpl;
import org.bytesoft.openjtcc.remote.RemoteTerminator;
import org.bytesoft.openjtcc.supports.AbstractSynchronization;
import org.bytesoft.openjtcc.supports.TransactionLogger;
import org.bytesoft.openjtcc.supports.TransactionStatistic;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.CommonUtils;

public class TransactionImpl extends TransactionArchive implements Transaction {
	private static final Logger logger = Logger.getLogger("openjtcc");

	private final TerminatorSkeleton terminatorSkeleton = new TerminatorSkeleton();
	private final CompensableContextImpl<Serializable> compensableContext = new CompensableContextImpl<Serializable>();
	private final Map<XidImpl, JtaTransactionImpl> internalTxnMap = new ConcurrentHashMap<XidImpl, JtaTransactionImpl>();
	private final Set<AbstractSynchronization> synchronizations = new HashSet<AbstractSynchronization>();

	private TransactionLogger transactionLogger;
	private TransactionManagerImpl transactionManager;
	private TransactionStatistic transactionStatistic;

	private boolean expireActivated;
	private boolean transactionCompleting;
	/* statistic-flags */
	private transient long transientFlags;

	public synchronized void prepareCoordinator() throws SystemException {

		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive holder = this.xidToNativeSvcMap.get(branchXid);
		JtaTransactionImpl internalTransaction = this.internalTxnMap.get(branchXid);

		boolean rollbackRequired = false;
		if (holder.tryCommitted) {
			// ignore
		} else if (internalTransaction != null) {
			try {
				this.delistCurrentService();

				internalTransaction.commit();
				holder.tryCommitted = true;
			} catch (Exception ex) {
				rollbackRequired = true;
				holder.tryCommitted = false;
			} finally {
				if (this.transactionStatus.isActive() || this.transactionStatus.isMarkedRollbackOnly()) {
					this.transactionStatus.setStatusPreparing();
					this.transactionStatistic.firePreparingTransaction(this);
				}
				this.internalTxnMap.remove(branchXid);
				this.transactionLogger.updateService(this.transactionContext, holder);
				this.transactionLogger.updateTransaction(this);
			}
		} else {
			rollbackRequired = true;
		}

		boolean prepareSuccess = false;
		try {
			this.prepareParticipant();
			prepareSuccess = true;
		} catch (SystemException ex) {
			rollbackRequired = true;
		} catch (RuntimeException ex) {
			rollbackRequired = true;
		} finally {
			if (this.transactionStatus.isPreparing()) {
				if (prepareSuccess) {
					this.transactionStatus.setStatusPrepared();
					this.transactionStatistic.firePreparedTransaction(this);
				} else {
					this.transactionStatus.setStatusPrepareFail();
				}
				this.transactionLogger.prepareTransaction(this);
			}
		}

		if (rollbackRequired) {
			throw new SystemException();
		}

	}

	public synchronized void prepareParticipant() throws IllegalStateException, SystemException {
		boolean errorExists = false;

		Iterator<Map.Entry<String, TerminatorArchive>> itr = this.appToTerminatorMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, TerminatorArchive> entry = itr.next();
			TerminatorArchive holder = entry.getValue();
			if (holder.prepared) {
				// ignore
			} else {
				RemoteTerminator terminator = holder.terminator;
				try {
					terminator.prepare();
					holder.prepared = true;
				} catch (RemoteException ex) {
					errorExists = true;

					// ex.printStackTrace();
				} catch (NoSuchTransactionException nstex) {
					errorExists = true;

					holder.prepared = true;
					holder.rolledback = true;
					holder.cleanup = true;

					// ex.printStackTrace();
				} catch (RuntimeException ex) {
					errorExists = true;
					// ex.printStackTrace();
				} finally {
					this.transactionLogger.prepareTerminator(this.transactionContext, holder);
				}
			}
		}

		if (this.expireActivated) {
			throw new IllegalStateException();
		} else if (errorExists) {
			throw new SystemException();
		}

	}

	private boolean confirmParticipantTerminator() throws HeuristicMixedException, HeuristicRollbackException,
			SystemException {
		boolean committedExists = false;
		boolean rolledbackExists = false;
		boolean mixedExists = false;
		boolean errorExists = false;
		Iterator<Map.Entry<String, TerminatorArchive>> itr = this.appToTerminatorMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, TerminatorArchive> entry = itr.next();
			TerminatorArchive holder = entry.getValue();
			boolean commitRequired = true;
			if (holder.committed) {
				committedExists = true;
				commitRequired = false;
			} else if (holder.rolledback) {
				rolledbackExists = true;
				commitRequired = false;
			}
			if (commitRequired) {
				RemoteTerminator terminator = holder.terminator;
				try {
					terminator.commit();
					holder.committed = true;
					committedExists = true;
				} catch (HeuristicMixedException ex) {
					mixedExists = false;
				} catch (HeuristicRollbackException ex) {
					rolledbackExists = true;
				} catch (SystemException ex) {
					errorExists = true;
				} catch (RemoteException ex) {
					errorExists = true;
				} catch (RuntimeException ex) {
					errorExists = true;
				} finally {
					this.transactionLogger.commitTerminator(this.transactionContext, holder);
				}
			}
		}

		if (mixedExists) {
			throw new HeuristicMixedException();
		} else if (committedExists && rolledbackExists) {
			throw new HeuristicMixedException();
		} else if (errorExists) {
			if (rolledbackExists) {
				throw new HeuristicMixedException();
			} else {
				throw new SystemException();
			}
		} else if (rolledbackExists) {
			throw new HeuristicRollbackException();
		}

		return committedExists;
	}

	public synchronized void commit() throws HeuristicMixedException, HeuristicRollbackException, RollbackException,
			SecurityException, SystemException {
		if (this.transactionStatus.isRolledBack()) {
			throw new RollbackException();
		} else if (this.transactionStatus.isRollingBack()) {
			try {
				this.rollback();
				throw new HeuristicRollbackException();
			} catch (IllegalStateException ex) {
				SystemException exception = new SystemException();
				exception.initCause(ex);
				throw exception;
			} catch (SystemException ex) {
				Throwable cause = ex.getCause();
				if (HeuristicCommitException.class.isInstance(cause)) {
					// ignore
				} else {
					throw ex;
				}
			}
		} else if (this.transactionStatus.isMarkedRollbackOnly()) {
			try {
				this.rollback();
				throw new HeuristicRollbackException();
			} catch (IllegalStateException ex) {
				SystemException exception = new SystemException();
				exception.initCause(ex);
				throw exception;
			} catch (SystemException ex) {
				Throwable cause = ex.getCause();
				if (HeuristicCommitException.class.isInstance(cause)) {
					// ignore
				} else {
					throw ex;
				}
			}
		} else if (this.transactionStatus.isCommitted()) {
			return;
		}

		this.transactionCompleting = true;

		boolean rollbackRequired = false;
		try {
			this.beforeCompletion();

			this.prepareCoordinator();
		} catch (SystemException ex) {
			rollbackRequired = true;
		} catch (RuntimeException ex) {
			rollbackRequired = true;
		}

		if (rollbackRequired) {
			try {
				this.rollback();
				throw new HeuristicRollbackException();
			} catch (IllegalStateException ex) {
				SystemException exception = new SystemException();
				exception.initCause(ex);
				throw exception;
			} catch (SystemException ex) {
				Throwable cause = ex.getCause();
				if (HeuristicCommitException.class.isInstance(cause)) {
					// ignore
				} else {
					throw ex;
				}
			}
		}

		HeuristicRollbackException thrown = null;
		boolean completed = false;
		try {
			if (this.transactionStatus.isPrepared()) {
				this.transactionStatus.setStatusCommiting();
				this.transactionLogger.updateTransaction(this);
				this.transactionStatistic.fireCommittingTransaction(this);
			} else if (this.transactionStatus.isCommitting()) {
				// ignore
			} else {
				throw new IllegalStateException();
			}

			this.coordinatorCommit();

			this.participantCommit();

			if (this.transactionStatus.isCommitting()) {
				this.transactionStatus.setStatusCommitted();
				this.transactionStatistic.fireCommittedTransaction(this);
			} else if (this.transactionStatus.isCommitted()) {
				// ignore
			} else {
				throw new IllegalStateException();
			}

			completed = true;
		} catch (HeuristicRollbackException ex) {
			completed = true;

			this.transactionStatus.setStatusRollingback();
			this.transactionStatus.setStatusRolledback();

			this.transactionStatistic.fireRolledbackTransaction(this);

			thrown = ex;
		} finally {
			this.afterCompletion();
		}

		if (completed) {
			this.transactionLogger.completeTransaction(this);
			this.coordinatorCleanup();
		}

		if (thrown != null) {
			throw thrown;
		}
	}

	private boolean rollbackParticipant() throws HeuristicMixedException, HeuristicCommitException, SystemException {
		boolean committedExists = false;
		boolean rolledbackExists = false;
		boolean mixedExists = false;
		boolean errorExists = false;
		Iterator<Map.Entry<String, TerminatorArchive>> itr = this.appToTerminatorMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<String, TerminatorArchive> entry = itr.next();
			TerminatorArchive holder = entry.getValue();
			boolean rolledbackRequired = true;
			if (holder.committed) {
				committedExists = true;
				rolledbackRequired = false;
			} else if (holder.rolledback) {
				rolledbackExists = true;
				rolledbackRequired = false;
			}
			if (rolledbackRequired) {
				RemoteTerminator terminator = holder.terminator;
				try {
					terminator.rollback();
					holder.rolledback = true;
					rolledbackExists = true;
				} catch (HeuristicMixedException ex) {
					mixedExists = false;
				} catch (HeuristicCommitException ex) {
					committedExists = true;
				} catch (SystemException ex) {
					errorExists = true;
				} catch (RemoteException ex) {
					errorExists = true;
				} catch (RuntimeException ex) {
					errorExists = true;
				} finally {
					this.transactionLogger.rollbackTerminator(this.transactionContext, holder);
				}
			}
		}

		if (mixedExists) {
			throw new HeuristicMixedException();
		} else if (committedExists && rolledbackExists) {
			throw new HeuristicMixedException();
		} else if (errorExists) {
			if (committedExists) {
				throw new HeuristicMixedException();
			} else {
				throw new SystemException();
			}
		} else if (committedExists) {
			throw new HeuristicCommitException();
		}

		return rolledbackExists;
	}

	public synchronized void timingRollback() throws HeuristicMixedException, SystemException {

		boolean firstCompletion = false;
		if (this.transactionCompleting == false
				&& (this.transactionStatus.isActive() || this.transactionStatus.isMarkedRollbackOnly())) {
			firstCompletion = true;
			this.transactionCompleting = true;
			this.expireActivated = true;
		}

		try {
			this.rollback();

			if (firstCompletion) {
				// transactionLogger.completeTransaction(this);
				// this.coordinatorCleanup();
			} else {
				throw new HeuristicMixedException();
			}

		} catch (IllegalStateException ex) {
			throw ex;
		} catch (SystemException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw ex;
		}
	}

	public synchronized void rollback() throws IllegalStateException, SystemException {
		if (this.transactionStatus.isRolledBack()) {
			return;
		} else if (this.transactionStatus.isCommitted()) {
			throw new IllegalStateException();
		}

		this.transactionCompleting = true;

		this.beforeCompletion();

		try {
			this.prepareCoordinator();
		} catch (SystemException ignore) {
		} catch (RuntimeException ignore) {
		}

		SystemException thrown = null;
		boolean completed = false;
		try {
			if (this.transactionStatus.isActive() || this.transactionStatus.isMarkedRollbackOnly()) {
				this.transactionStatus.setStatusRollingback();
				this.transactionLogger.updateTransaction(this);

				this.transactionStatistic.fireRollingBackTransaction(this);
			} else if (this.transactionStatus.isPrepareFail() || this.transactionStatus.isPrepared()) {
				this.transactionStatus.setStatusRollingback();
				this.transactionLogger.updateTransaction(this);

				this.transactionStatistic.fireRollingBackTransaction(this);
			} else if (this.transactionStatus.isRollingBack()) {
				// ignore
			} else if (this.transactionStatus.isCommitting() || this.transactionStatus.isCommitFail()) {
				this.transactionStatus.setStatusRollingback();
				this.transactionLogger.updateTransaction(this);

				this.transactionStatistic.fireRollingBackTransaction(this);
			} else {
				throw new IllegalStateException();
			}

			this.coordinatorRollback();

			this.participantRollback();

			if (this.transactionStatus.isRollingBack()) {
				this.transactionStatus.setStatusRolledback();
				this.transactionStatistic.fireRolledbackTransaction(this);
			} else if (this.transactionStatus.isRolledBack()) {
				// ignore
			} else {
				throw new IllegalStateException();
			}

			completed = true;

		} catch (HeuristicMixedException ex) {
			SystemException exception = new SystemException();
			exception.initCause(ex);
			throw exception;
		} catch (HeuristicCommitException ex) {
			completed = true;

			this.transactionStatus.changeStatusToCommiting();
			this.transactionStatus.setStatusCommitted();

			this.transactionStatistic.fireCommittedTransaction(this);

			thrown = new SystemException();
			thrown.initCause(ex);
		} finally {
			this.afterCompletion();
		}

		if (completed) {
			this.transactionLogger.completeTransaction(this);
			this.coordinatorCleanup();
		}

		if (thrown != null) {
			throw thrown;
		}

	}

	public void rollbackTryingTransaction() throws IllegalStateException, SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive holder = this.xidToNativeSvcMap.get(branchXid);

		if (holder == null) {
			throw new IllegalStateException();
		} else if (this.transactionContext.isCoordinator()) {
			if (this.transactionStatus.isActive()) {
				throw new IllegalStateException();
			} else if (this.transactionStatus.isMarkedRollbackOnly()) {
				throw new IllegalStateException();
			}
		}

		JtaTransactionImpl internal = this.internalTxnMap.get(branchXid);

		logger.info(String.format("[rollback-branch] global: %s, inner: %s", this, internal));

		try {
			this.delistCurrentService();

			internal.rollback();
			holder.tryCommitted = false;
		} catch (SystemException ex) {
			holder.tryCommitted = false;
		} catch (RuntimeException ex) {
			holder.tryCommitted = false;
		} finally {
			this.transactionLogger.updateService(transactionContext, holder);
			this.unassociateInternalTransaction();
		}
	}

	public void rollbackCompleteTransaction() throws IllegalStateException, SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive holder = this.xidToNativeSvcMap.get(branchXid);

		if (holder == null) {
			throw new IllegalStateException();
		} else if (this.transactionContext.isCoordinator()) {
			if (this.transactionStatus.isActive()) {
				throw new IllegalStateException();
			} else if (this.transactionStatus.isMarkedRollbackOnly()) {
				throw new IllegalStateException();
			}
		}

		JtaTransactionImpl internal = this.internalTxnMap.get(branchXid);

		logger.info(String.format("[rollback-branch] global: %s, inner: %s", this, internal));

		try {
			internal.rollback();
			holder.committed = false;
			holder.rolledback = true;
		} catch (SystemException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} catch (RuntimeException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} finally {
			transactionLogger.updateService(this.transactionContext, holder);
			this.unassociateInternalTransaction();
		}
	}

	public void commitTryingTransaction() throws HeuristicMixedException, HeuristicRollbackException,
			IllegalStateException, RollbackException, SecurityException, SystemException {

		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive holder = this.xidToNativeSvcMap.get(branchXid);

		if (holder == null) {
			throw new IllegalStateException();
		} else if (this.transactionContext.isCoordinator()) {
			if (this.transactionStatus.isActive()) {
				throw new IllegalStateException();
			} else if (this.transactionStatus.isMarkedRollbackOnly()) {
				throw new IllegalStateException();
			}
		}

		JtaTransactionImpl internal = this.internalTxnMap.get(branchXid);

		logger.info(String.format("[commit-branch] global: %s, inner: %s", this, internal));

		try {
			this.delistCurrentService();

			internal.commit();
			holder.tryCommitted = true;
		} catch (HeuristicMixedException ex) {
			holder.tryCommitted = false;
		} catch (HeuristicRollbackException ex) {
			holder.tryCommitted = false;
		} catch (RollbackException ex) {
			holder.tryCommitted = false;
		} catch (SecurityException ex) {
			holder.tryCommitted = false;
		} catch (SystemException ex) {
			holder.tryCommitted = false;
		} catch (RuntimeException ex) {
			holder.tryCommitted = false;
		} finally {
			this.transactionLogger.updateService(this.transactionContext, holder);
			this.unassociateInternalTransaction();
		}
	}

	public void commitCompleteTransaction() throws HeuristicMixedException, HeuristicRollbackException,
			IllegalStateException, RollbackException, SecurityException, SystemException {

		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive holder = this.xidToNativeSvcMap.get(branchXid);

		if (holder == null) {
			throw new IllegalStateException();
		} else if (this.transactionContext.isCoordinator()) {
			if (this.transactionStatus.isActive()) {
				throw new IllegalStateException();
			} else if (this.transactionStatus.isMarkedRollbackOnly()) {
				throw new IllegalStateException();
			}
		}

		JtaTransactionImpl internal = this.internalTxnMap.get(branchXid);

		logger.info(String.format("[commit-branch] global: %s, inner: %s", this, internal));

		try {
			internal.commit();
			holder.committed = true;
			holder.rolledback = false;
		} catch (HeuristicMixedException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} catch (HeuristicRollbackException ex) {
			holder.committed = false;
			holder.rolledback = true;
		} catch (RollbackException ex) {
			holder.committed = false;
			holder.rolledback = true;
		} catch (SecurityException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} catch (SystemException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} catch (RuntimeException ex) {
			holder.committed = false;
			holder.rolledback = false;
		} finally {
			transactionLogger.updateService(this.transactionContext, holder);
			this.unassociateInternalTransaction();
		}
	}

	public synchronized void coordinatorCommit() {

		Iterator<Map.Entry<XidImpl, CompensableArchive>> itr = this.xidToNativeSvcMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<XidImpl, CompensableArchive> entry = itr.next();
			CompensableArchive holder = entry.getValue();
			if (holder.launchSvc) {
				this.confirmLaunchCompensable(holder);
				break;
			}

		}

	}

	private void confirmLaunchCompensable(CompensableArchive launchHolder) {

		boolean confirmRequired = true;
		if (launchHolder.confirmed && launchHolder.committed) {
			confirmRequired = false;
		} else if (launchHolder.confirmed && launchHolder.rolledback) {
			confirmRequired = true;
		} else if (launchHolder.cancelled && launchHolder.committed) {
			confirmRequired = false;
		} else if (launchHolder.cancelled && launchHolder.rolledback) {
			confirmRequired = true;
		} else if (launchHolder.tryCommitted == false && launchHolder.variable == null) {
			confirmRequired = false;
		}

		if (confirmRequired) {
			Compensable<Serializable> service = launchHolder.service;
			Serializable variable = launchHolder.variable;
			XidImpl originBranchXid = this.transactionContext.getCurrentXid();
			try {
				this.transactionContext.setCurrentXid(launchHolder.branchXid);
				launchHolder.confirmed = true;
				this.transactionLogger.confirmService(this.transactionContext, launchHolder);
				service.confirm(variable);
				launchHolder.committed = true;// TODO 如果confirm为Never等传播属性，标记该方法已执行
			} catch (CompensableException ex) {
				ex.printStackTrace();
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			} finally {
				this.transactionContext.setCurrentXid(originBranchXid);
				this.transactionLogger.confirmService(this.transactionContext, launchHolder);
			}

		}// end-if (confirmRequired)
	}

	public synchronized void participantCommit() throws HeuristicMixedException, HeuristicRollbackException,
			SystemException {
		boolean committedExists = false;
		boolean rolledbackExists = false;
		boolean mixedExists = false;
		boolean errorExists = false;
		Iterator<Map.Entry<XidImpl, CompensableArchive>> itr = this.xidToNativeSvcMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<XidImpl, CompensableArchive> entry = itr.next();
			CompensableArchive holder = entry.getValue();
			if (holder.launchSvc) {
				if (holder.confirmed && holder.committed) {
					committedExists = true;
				} else if (holder.cancelled && holder.committed) {
					rolledbackExists = true;
				} else {
					errorExists = true;
				}
			} else {
				try {
					this.confirmParticipantCompensable(holder);
				} catch (HeuristicCommitException hcex) {
					committedExists = true;
				} catch (HeuristicRollbackException hrex) {
					rolledbackExists = true;
				} catch (SystemException ex) {
					errorExists = true;
				} catch (RuntimeException rex) {
					errorExists = true;
				}
			}
		}

		try {
			boolean flags = this.confirmParticipantTerminator();
			if (flags) {
				committedExists = true;
			}
		} catch (HeuristicMixedException ex) {
			mixedExists = true;
		} catch (HeuristicRollbackException ex) {
			rolledbackExists = true;
		} catch (SystemException ex) {
			errorExists = true;
		} catch (RuntimeException ex) {
			errorExists = true;
		}

		if (mixedExists) {
			throw new HeuristicMixedException();
		} else if (committedExists && rolledbackExists) {
			throw new HeuristicMixedException();
		} else if (errorExists) {
			if (rolledbackExists) {
				throw new HeuristicMixedException();
			} else {
				throw new SystemException();
			}
		} else if (rolledbackExists) {
			throw new HeuristicRollbackException();
		}

	}

	private void confirmParticipantCompensable(CompensableArchive holder) throws HeuristicCommitException,
			HeuristicRollbackException, SystemException {
		if (holder.confirmed && holder.committed) {
			throw new HeuristicCommitException();

		} else if (holder.confirmed && holder.rolledback) {
			// ignore
		} else if (holder.cancelled && holder.committed) {
			throw new HeuristicRollbackException();
		} else if (holder.cancelled && holder.rolledback) {
			// ignore
		} else if (holder.tryCommitted == false && holder.variable == null) {
			return;
		}

		Compensable<Serializable> service = holder.service;
		Serializable variable = holder.variable;
		XidImpl originBranchXid = this.transactionContext.getCurrentXid();
		try {
			this.transactionContext.setCurrentXid(holder.branchXid);
			holder.confirmed = true;
			this.transactionLogger.confirmService(this.transactionContext, holder);
			service.confirm(variable);
			holder.committed = true;// TODO 如果confirm为Never等传播属性，标记该方法已执行
		} catch (CompensableException ex) {
			ex.printStackTrace();
		} catch (RuntimeException ex) {
			ex.printStackTrace();
		}

		this.transactionContext.setCurrentXid(originBranchXid);
		this.transactionLogger.commitService(this.transactionContext, holder);
		if (holder.committed) {
			throw new HeuristicCommitException();
		} else if (holder.rolledback) {
			// TODO
			throw new SystemException();
		} else {
			throw new SystemException();
		}

	}

	public synchronized void coordinatorRollback() {

		Iterator<Map.Entry<XidImpl, CompensableArchive>> itr = this.xidToNativeSvcMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<XidImpl, CompensableArchive> entry = itr.next();
			CompensableArchive holder = entry.getValue();
			if (holder.launchSvc) {
				this.cancelLaunchCompensable(holder);
				break;
			}
		}

	}

	private void cancelLaunchCompensable(CompensableArchive launchHolder) {
		boolean cancelRequired = true;
		if (launchHolder.confirmed && launchHolder.committed) {
			cancelRequired = false;
		} else if (launchHolder.confirmed && launchHolder.rolledback) {
			cancelRequired = true;
		} else if (launchHolder.cancelled && launchHolder.committed) {
			cancelRequired = false;
		} else if (launchHolder.cancelled && launchHolder.rolledback) {
			cancelRequired = true;
		} else if (launchHolder.tryCommitted == false && launchHolder.variable == null) {
			cancelRequired = false;
		}

		if (cancelRequired) {
			Compensable<Serializable> service = launchHolder.service;
			Serializable variable = launchHolder.variable;
			XidImpl originBranchXid = this.transactionContext.getCurrentXid();
			try {
				this.transactionContext.setCurrentXid(launchHolder.branchXid);
				launchHolder.cancelled = true;
				this.transactionLogger.cancelService(this.transactionContext, launchHolder);
				service.cancel(variable);
				launchHolder.committed = true;// TODO 如果cancel为Never等传播属性，标记该方法已执行
			} catch (CompensableException ex) {
				ex.printStackTrace();
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			} finally {
				this.transactionContext.setCurrentXid(originBranchXid);
				this.transactionLogger.cancelService(this.transactionContext, launchHolder);
			}

		}// end-if (cancelRequired)
	}

	public synchronized void participantRollback() throws HeuristicMixedException, HeuristicCommitException,
			SystemException {
		boolean committedExists = false;
		boolean rolledbackExists = false;
		boolean mixedExists = false;
		boolean errorExists = false;
		Iterator<Map.Entry<XidImpl, CompensableArchive>> itr = this.xidToNativeSvcMap.entrySet().iterator();
		while (itr.hasNext()) {
			Map.Entry<XidImpl, CompensableArchive> entry = itr.next();
			CompensableArchive holder = entry.getValue();
			if (holder.launchSvc) {
				if (holder.confirmed && holder.committed) {
					committedExists = true;
				} else if (holder.cancelled && holder.committed) {
					rolledbackExists = true;
				} else {
					errorExists = true;
				}
			} else {
				try {
					this.cancelParticipantCompensable(holder);
				} catch (HeuristicCommitException hcex) {
					committedExists = true;
				} catch (HeuristicRollbackException hrex) {
					rolledbackExists = true;
				} catch (SystemException ex) {
					errorExists = true;
				} catch (RuntimeException rex) {
					errorExists = true;
				}
			}
		}

		try {
			boolean flags = this.rollbackParticipant();
			if (flags) {
				rolledbackExists = true;
			}
		} catch (HeuristicMixedException ex) {
			mixedExists = true;
		} catch (HeuristicCommitException ex) {
			committedExists = true;
		} catch (SystemException ex) {
			errorExists = true;
		} catch (RuntimeException ex) {
			errorExists = true;
		}

		if (mixedExists) {
			throw new HeuristicMixedException();
		} else if (committedExists && rolledbackExists) {
			throw new HeuristicMixedException();
		} else if (errorExists) {
			if (committedExists) {
				throw new HeuristicMixedException();
			} else {
				throw new SystemException();
			}
		} else if (committedExists) {
			throw new HeuristicCommitException();
		}

	}

	private void cancelParticipantCompensable(CompensableArchive holder) throws HeuristicCommitException,
			HeuristicRollbackException, SystemException {
		if (holder.confirmed && holder.committed) {
			throw new HeuristicCommitException();
		} else if (holder.confirmed && holder.rolledback) {
			// ignore
		} else if (holder.cancelled && holder.committed) {
			throw new HeuristicRollbackException();
		} else if (holder.cancelled && holder.rolledback) {
			// ignore
		} else if (holder.tryCommitted == false && holder.variable == null) {
			return;
		}

		Compensable<Serializable> service = holder.service;
		Serializable variable = holder.variable;
		// boolean launch = holder.launchSvc;
		XidImpl originBranchXid = this.transactionContext.getCurrentXid();
		try {
			this.transactionContext.setCurrentXid(holder.branchXid);
			holder.cancelled = true;
			this.transactionLogger.cancelService(this.transactionContext, holder);
			service.cancel(variable);
			holder.committed = true;// TODO 如果cancel为Never等传播属性，标记该方法已执行
		} catch (CompensableException ex) {
			ex.printStackTrace();
		} catch (RuntimeException ex) {
			ex.printStackTrace();
		}

		this.transactionContext.setCurrentXid(originBranchXid);
		this.transactionLogger.rollbackService(this.transactionContext, holder);

		if (holder.committed) {
			throw new HeuristicRollbackException();
		} else if (holder.rolledback) {
			throw new SystemException();
		} else {
			throw new SystemException();
		}

	}

	public synchronized void cleanup() throws RemoteException {
		logger.info(String.format("[%s] clean transaction", this.transactionContext.getGlobalXid()));

		boolean errorExists = false;
		Iterator<Map.Entry<String, TerminatorArchive>> iter = this.appToTerminatorMap.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<String, TerminatorArchive> entry = iter.next();
			TerminatorArchive holder = entry.getValue();
			if (holder.cleanup) {
				// ignore
			} else {
				RemoteTerminator terminator = holder.terminator;
				try {
					terminator.cleanup();
					holder.cleanup = true;
					this.transactionLogger.cleanupTerminator(this.transactionContext, holder);
				} catch (RemoteException thrown) {
					errorExists = true;
				} catch (RuntimeException thrown) {
					errorExists = true;
				}
			}
		}// end-while

		if (errorExists) {
			throw new RuntimeException();
		}

		this.transactionManager.completeTransaction(this.transactionContext.getGlobalXid());

		this.transactionStatistic.fireCleanupTransaction(this);

		this.transactionLogger.cleanupTransaction(this);
	}

	public synchronized void setRollbackOnly() throws IllegalStateException, SystemException {
		this.transactionStatus.markStatusRollback();
	}

	public synchronized boolean enlistService(Compensable<Serializable> service) throws IllegalStateException,
			SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive svcHolder = this.xidToNativeSvcMap.get(branchXid);
		if (svcHolder == null) {
			compensableContext.setTransactionContext(this.transactionContext);
			compensableContext.setTransactionStatus(this.transactionStatus);
			compensableContext.setBranchXid(branchXid);

			logger.info(String.format("[%s] enlist-service", this.transactionContext.getGlobalXid()));

			XidImpl globalXid = this.transactionContext.getGlobalXid();

			svcHolder = new CompensableArchive();
			svcHolder.branchXid = branchXid;
			svcHolder.service = service;
			svcHolder.launchSvc = globalXid.equals(branchXid);

			this.transactionContext.setCompensable(true);

			this.transactionLogger.enlistService(this.transactionContext, svcHolder);

			this.xidToNativeSvcMap.put(branchXid, svcHolder);

			return true;
		} else {
			return false;
		}
	}

	public synchronized boolean delistCurrentService() throws IllegalStateException, SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		CompensableArchive svcHolder = this.xidToNativeSvcMap.get(branchXid);
		if (svcHolder == null) {
			return false;
		} else {
			return this.delistService(svcHolder);
		}
	}

	public synchronized boolean delistService(CompensableArchive holder) throws IllegalStateException, SystemException {
		Serializable variable = this.compensableContext.getCompensableVariable();
		holder.variable = variable;
		logger.info(String.format("[%s] delist-service: %s", this.transactionContext.getGlobalXid(), variable));
		this.transactionLogger.delistService(this.transactionContext, holder);
		return true;
	}

	public synchronized boolean registerTerminator(RemoteTerminator terminator) throws IllegalStateException,
			SystemException {
		TerminalKey terminalKey = terminator.getTerminalKey();
		String application = terminalKey.getApplication();
		TerminatorArchive holder = this.appToTerminatorMap.get(application);
		if (holder == null) {
			holder = new TerminatorArchive();
			holder.terminator = terminator;
			this.appToTerminatorMap.put(application, holder);
			this.transactionLogger.registerTerminator(this.transactionContext, holder);
		}
		return true;
	}

	public synchronized TerminalKey getTerminalKey(String application) {
		RemoteTerminator terminator = this.getTerminator(application);
		if (terminator == null) {
			return null;
		} else {
			return terminator.getTerminalKey();
		}
	}

	public RemoteTerminator getTerminator(String application) {
		TerminatorArchive holder = this.appToTerminatorMap.get(application);
		return holder == null ? null : holder.terminator;
	}

	public synchronized void coordinatorCleanup() {
		if (this.transactionContext.isCoordinator()) {
			this.transactionManager.notifyCompleteTransaction(this);
		} else if (this.expireActivated) {
			this.transactionManager.notifyCompleteTransaction(this);
		}
	}

	public synchronized void suspendAllResource() throws IllegalStateException, SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		JtaTransactionImpl internalTransaction = this.internalTxnMap.get(branchXid);
		internalTransaction.suspendAllResource();
	}

	public synchronized void resumeAllResource() throws IllegalStateException, SystemException {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		JtaTransactionImpl internalTransaction = this.internalTxnMap.get(branchXid);
		internalTransaction.resumeAllResource();
	}

	public synchronized boolean delistResource(XAResource xares, int flags) throws IllegalStateException,
			SystemException {
		throw new SystemException();
	}

	public synchronized boolean enlistResource(XAResource xares) throws IllegalStateException, RollbackException,
			SystemException {
		throw new SystemException();
	}

	public synchronized int getStatus() throws SystemException {
		throw new SystemException();
	}

	public synchronized void registerSynchronization(Synchronization sync) throws IllegalStateException,
			RollbackException, SystemException {
		if (AbstractSynchronization.class.isInstance(sync)) {
			AbstractSynchronization synchronization = (AbstractSynchronization) sync;
			this.synchronizations.add(synchronization);
		} else {
			throw new IllegalStateException();
		}
	}

	public synchronized void beforeCompletion() {
		Iterator<AbstractSynchronization> itr = this.synchronizations.iterator();
		while (itr.hasNext()) {
			AbstractSynchronization sync = itr.next();
			try {
				sync.beforeCompletion();
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			}
		}
	}

	public synchronized void afterCompletion() {
		Iterator<AbstractSynchronization> itr = this.synchronizations.iterator();
		while (itr.hasNext()) {
			AbstractSynchronization sync = itr.next();
			try {
				sync.afterCompletion(this.transactionStatus.getTransactionStatus());
			} catch (RuntimeException ex) {
				ex.printStackTrace();
			}
		}
	}

	public JtaTransactionImpl associateInternalTransaction() {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		JtaTransactionImpl internal = new JtaTransactionImpl(this.transactionContext);
		internal.setXidFactory(this.transactionManager.getXidFactory());
		internal.initialize();
		this.internalTxnMap.put(branchXid, internal);
		return internal;
	}

	public JtaTransactionImpl unassociateInternalTransaction() {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		return this.internalTxnMap.remove(branchXid);
	}

	public JtaTransactionImpl getInternalTransaction() {
		XidImpl branchXid = this.transactionContext.getCurrentXid();
		return this.internalTxnMap.get(branchXid);
	}

	public TransactionManagerImpl getTransactionManager() {
		return transactionManager;
	}

	public void setTransactionManager(TransactionManagerImpl transactionManager) {
		this.transactionManager = transactionManager;
	}

	public TransactionLogger getTransactionLogger() {
		return transactionLogger;
	}

	public void setTransactionLogger(TransactionLogger transactionLogger) {
		this.transactionLogger = transactionLogger;
	}

	public TerminatorSkeleton getTerminatorSkeleton() {
		return terminatorSkeleton;
	}

	public boolean isTransactionCompleting() {
		return transactionCompleting;
	}

	public void setTransactionCompleting(boolean transactionCompleting) {
		this.transactionCompleting = transactionCompleting;
	}

	public CompensableContextImpl<Serializable> getCompensableContext() {
		return compensableContext;
	}

	public TransactionStatistic getTransactionStatistic() {
		return transactionStatistic;
	}

	public void setTransactionStatistic(TransactionStatistic transactionStatistic) {
		this.transactionStatistic = transactionStatistic;
	}

	public boolean containTransientFlags(long flags) {
		return (this.transientFlags & flags) == flags;
	}

	public void affixTransientFlags(long flags) {
		this.transientFlags = (this.transientFlags | flags);
	}

	public void resetTransientFlags(long flags) {
		this.transientFlags = this.transientFlags ^ flags;
	}

	@Override
	public int hashCode() {
		int hash = 23;
		if (this.transactionContext == null) {
			return hash;
		}
		XidImpl branchXid = this.transactionContext.getGlobalXid();
		hash += 31 * ((branchXid == null) ? 0 : branchXid.hashCode());
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		} else if (this.getClass().equals(obj.getClass()) == false) {
			return false;
		}
		TransactionImpl that = (TransactionImpl) obj;
		TransactionContext thisTransactionContext = this.getTransactionContext();
		TransactionContext thatTransactionContext = that.getTransactionContext();
		if (thisTransactionContext == null && thatTransactionContext == null) {
			return true;
		} else if (thisTransactionContext == null || thatTransactionContext == null) {
			return false;
		}
		XidImpl thisBranchXid = thisTransactionContext.getGlobalXid();
		XidImpl thatBranchXid = thatTransactionContext.getGlobalXid();
		return CommonUtils.equals(thisBranchXid, thatBranchXid);
	}

	public class TerminatorSkeleton implements RemoteTerminator {

		public void cleanup() throws RemoteException {
			TransactionImpl transaction = TransactionImpl.this;
			synchronized (transaction) {
				transaction.cleanup();
			}
		}

		public void prepare() throws SystemException, RemoteException, NoSuchTransactionException {
			TransactionImpl transaction = TransactionImpl.this;

			synchronized (transaction) {
				transaction.beforeCompletion();
				transaction.setTransactionCompleting(true);

				TransactionStatus transactionStatus = transaction.getTransactionStatus();
				if (TransactionImpl.class.equals(transaction.getClass()) == false) {
					throw new SystemException();
				} else if (transactionStatus.isActive() || transactionStatus.isMarkedRollbackOnly()) {
					transactionStatus.setStatusPreparing();
					transactionStatistic.firePreparingTransaction(transaction);
				}

				boolean prepareSuccess = false;
				try {
					transaction.prepareParticipant();
					prepareSuccess = true;
				} catch (IllegalStateException ex) {
					NoSuchTransactionException nstex = new NoSuchTransactionException();
					nstex.initCause(ex);
					throw nstex;
				} catch (SystemException ex) {
					throw ex;
				} catch (RuntimeException rex) {
					SystemException exception = new SystemException();
					exception.initCause(rex);
					throw exception;
				} finally {
					if (transactionStatus.isPreparing()) {
						if (prepareSuccess) {
							transactionStatus.setStatusPrepared();
							transactionStatistic.firePreparedTransaction(transaction);
						} else {
							transactionStatus.setStatusPrepareFail();
						}
						transactionLogger.prepareTransaction(transaction);
					}
				}
			}
		}

		public void commit() throws HeuristicMixedException, HeuristicRollbackException, SystemException,
				RemoteException {
			TransactionImpl transaction = TransactionImpl.this;
			synchronized (transaction) {
				TransactionStatus transactionStatus = transaction.transactionStatus;
				if (transactionStatus.isActive()) {
					throw new IllegalStateException();
				} else if (transactionStatus.isMarkedRollbackOnly()) {
					throw new IllegalStateException();
				} else if (transactionStatus.isRolledBack()) {
					throw new HeuristicRollbackException();
				} else if (transactionStatus.isRollingBack()) {
					try {
						this.rollback();
						throw new HeuristicRollbackException();
					} catch (IllegalStateException ex) {
						SystemException exception = new SystemException();
						exception.initCause(ex);
						throw exception;
					} catch (HeuristicCommitException e) {
						return;
					}
				} else if (transactionStatus.isCommitted()) {
					return;
				}

				transaction.setTransactionCompleting(true);

				try {
					if (transactionStatus.isPrepared()) {
						transactionStatus.setStatusCommiting();
						transactionStatistic.fireCommittingTransaction(transaction);
						transactionLogger.updateTransaction(transaction);
					} else if (transactionStatus.isCommitting()) {
						// ignore
					} else {
						throw new IllegalStateException();
					}

					transaction.participantCommit();

					if (transactionStatus.isCommitting()) {
						transactionStatus.setStatusCommitted();
						transactionStatistic.fireCommittedTransaction(transaction);
						transactionLogger.completeTransaction(transaction);
					} else if (transactionStatus.isCommitted()) {
						// ignore
					} else {
						throw new IllegalStateException();
					}

				} finally {
					transaction.afterCompletion();
				}
			}
		}

		public void rollback() throws HeuristicMixedException, HeuristicCommitException, SystemException,
				RemoteException {

			TransactionImpl transaction = TransactionImpl.this;
			synchronized (transaction) {
				TransactionStatus transactionStatus = transaction.transactionStatus;
				if (transactionStatus.isActive()) {
					throw new IllegalStateException();
				} else if (transactionStatus.isMarkedRollbackOnly()) {
					throw new IllegalStateException();
				} else if (transactionStatus.isCommitted()) {
					throw new IllegalStateException();
				} else if (transactionStatus.isRolledBack()) {
					return;
				}

				try {
					if (transactionStatus.isActive() || transactionStatus.isMarkedRollbackOnly()) {
						transactionStatus.setStatusRollingback();
						transactionStatistic.fireRollingBackTransaction(transaction);
						transactionLogger.updateTransaction(transaction);
					} else if (transactionStatus.isPrepareFail() || transactionStatus.isPrepared()) {
						transactionStatus.setStatusRollingback();
						transactionStatistic.fireRollingBackTransaction(transaction);
						transactionLogger.updateTransaction(transaction);
					} else if (transactionStatus.isRollingBack()) {
						// ignore
					} else if (transactionStatus.isCommitting() || transactionStatus.isCommitFail()) {
						transactionStatus.setStatusRollingback();
						transactionStatistic.fireRollingBackTransaction(transaction);
						transactionLogger.updateTransaction(transaction);
					} else {
						throw new IllegalStateException();
					}

					transaction.setTransactionCompleting(true);

					transaction.participantRollback();

					if (transactionStatus.isRollingBack()) {
						transactionStatus.setStatusRolledback();
						transactionStatistic.fireRolledbackTransaction(transaction);
					} else if (transactionStatus.isRolledBack()) {
						// ignore
					} else {
						throw new IllegalStateException();
					}

					transactionLogger.completeTransaction(transaction);

				} finally {
					transaction.afterCompletion();
				}
			}
		}

		public TerminalKey getTerminalKey() {
			throw new IllegalStateException();
		}

	}

}

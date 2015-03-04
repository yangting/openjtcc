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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.bytesoft.openjtcc.common.PropagationKey;
import org.bytesoft.openjtcc.common.TerminalKey;
import org.bytesoft.openjtcc.common.TransactionContext;
import org.bytesoft.openjtcc.common.TransactionStatus;
import org.bytesoft.openjtcc.internal.AssociatedContext;
import org.bytesoft.openjtcc.internal.JtaTransactionImpl;
import org.bytesoft.openjtcc.supports.AbstractSynchronization;
import org.bytesoft.openjtcc.supports.TransactionLogger;
import org.bytesoft.openjtcc.supports.TransactionRepository;
import org.bytesoft.openjtcc.supports.TransactionStatistic;
import org.bytesoft.openjtcc.supports.internal.XidFactoryImpl;
import org.bytesoft.openjtcc.supports.schedule.CleanupProcesser;
import org.bytesoft.openjtcc.supports.schedule.TimingProcesser;
import org.bytesoft.openjtcc.xa.XidFactory;
import org.bytesoft.openjtcc.xa.XidImpl;
import org.bytesoft.utils.ByteUtils;

public class TransactionManagerImpl implements TransactionManager, TimingProcesser {
	private static final Logger logger = Logger.getLogger("openjtcc");

	private XidFactoryImpl xidFactory;
	private PropagationKey instanceKey;

	private final Map<Thread, AssociatedContext> threadToTxnMap = new ConcurrentHashMap<Thread, AssociatedContext>();
	private final Map<Thread, AbstractSynchronization> threadToSynMap = new ConcurrentHashMap<Thread, AbstractSynchronization>();
	private final Set<AssociatedContext> expiredTransactionSet = new HashSet<AssociatedContext>();

	private TransactionStatistic transactionStatistic;
	private CleanupProcesser cleanupProcesser;
	private TransactionRepository transactionRepository;

	private int transactionTimeout = 5 * 60;

	public TransactionImpl begin(TransactionContext propagationContext) throws NotSupportedException, SystemException {
		if (this.getCurrentTransaction() != null) {
			throw new NotSupportedException();
		}

		XidImpl xid = propagationContext.getGlobalXid();
		TransactionImpl transaction = this.transactionRepository.getTransaction(xid);
		if (transaction == null) {
			transaction = new TransactionImpl();
			transaction.setTransactionStatistic(this.transactionStatistic);

			long expired = propagationContext.getExpiredTime();
			long created = propagationContext.getCreatedTime();
			// int timeout = (int) ((expired - created) / 1000L);
			// transaction.setTransactionTimeout(timeout);

			TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();

			TransactionContext context = propagationContext.clone();
			context.setTerminalKey(this.getTerminalKey());
			context.setCreationXid(propagationContext.getCurrentXid());

			context.setCompensable(false);
			transaction.setTransactionContext(context);
			transaction.setTransactionStatus(new TransactionStatus());
			transaction.setTransactionManager(this);
			transaction.setTransactionLogger(transactionLogger);
			long now = System.currentTimeMillis();
			context.setCreatedTime(now);
			context.setExpiredTime(now + expired - created);

			this.transactionRepository.putTransaction(xid, transaction);
			this.transactionStatistic.fireBeginTransaction(transaction);

			transactionLogger.beginTransaction(transaction);
		}

		logger.info(String.format("[begin-branch] global: %s, inner: %s", transaction, this.getTransaction()));

		this.associateTransaction(transaction);
		return transaction;
	}

	public void begin() throws NotSupportedException, SystemException {
		TransactionImpl global = this.getCurrentTransaction();
		if (global == null) {
			this.beginGlobalTransaction();
			logger.info(String.format("[begin-global] global: %s, inner: %s"//
					, this.getCurrentTransaction(), this.getTransaction()));
		} else {
			this.beginInternalTransaction();
		}
	}

	private void beginInternalTransaction() throws SystemException, NotSupportedException {
		TransactionImpl global = this.getCurrentTransaction();
		JtaTransactionImpl internal = this.getTransaction();
		if (internal == null) {
			internal = global.associateInternalTransaction();
			logger.info(String.format("[begin-native] global: %s, inner: %s", global, internal));

			if (global.isTransactionCompleting() == false) {
				AbstractSynchronization sync = this.threadToSynMap.get(Thread.currentThread());
				if (sync != null) {
					try {
						TransactionContext transactionContext = global.getTransactionContext();
						sync.afterCreation(transactionContext.getCurrentXid());
					} finally {
						this.unRegisterSynchronization(sync);
					}
				}
			}
		} else {
			throw new NotSupportedException();
		}
	}

	private void beginGlobalTransaction() throws SystemException {
		int timeoutSeconds = this.transactionTimeout;

		TransactionContext transactionContext = new TransactionContext();
		transactionContext.setCoordinator(true);
		long createdTime = System.currentTimeMillis();
		long expiredTime = createdTime + (timeoutSeconds * 1000L);
		transactionContext.setCreatedTime(createdTime);
		transactionContext.setExpiredTime(expiredTime);
		transactionContext.setTerminalKey(this.getTerminalKey());
		XidImpl globalXid = this.xidFactory.createGlobalXid();
		// transactionContext.setGlobalXid(globalXid);
		transactionContext.setCreationXid(globalXid);
		transactionContext.setCurrentXid(globalXid);

		TransactionImpl transaction = new TransactionImpl();
		transaction.setTransactionStatistic(this.transactionStatistic);

		// transaction.setTransactionTimeout(timeoutSeconds);
		transaction.setTransactionContext(transactionContext);
		transaction.setTransactionStatus(new TransactionStatus());
		transaction.setTransactionManager(this);
		transaction.setTransactionLogger(this.transactionRepository.getTransactionLogger());

		transaction.associateInternalTransaction();

		AssociatedContext actx = new AssociatedContext();
		actx.setTransaction(transaction);
		actx.setThread(Thread.currentThread());

		this.threadToTxnMap.put(Thread.currentThread(), actx);
		this.transactionRepository.putTransaction(transactionContext.getGlobalXid(), transaction);
		this.transactionStatistic.fireBeginTransaction(transaction);

		TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();
		transactionLogger.beginTransaction(transaction);

		AbstractSynchronization sync = this.threadToSynMap.get(Thread.currentThread());
		if (sync != null) {
			try {
				sync.afterCreation(transactionContext.getCurrentXid());
			} finally {
				this.unRegisterSynchronization(sync);
			}
		}

	}

	public void commit() throws HeuristicMixedException, HeuristicRollbackException, IllegalStateException,
			RollbackException, SecurityException, SystemException {
		TransactionImpl global = this.getCurrentTransaction();
		TransactionContext transactionContext = global.getTransactionContext();
		boolean compensable = transactionContext.isCompensable();
		boolean coordinator = transactionContext.isCoordinator();
		if (compensable == false) {
			this.commitRegularTransaction(global);
		} else if (coordinator) {
			if (global.isTransactionCompleting()) {
				global.commitCompleteTransaction();
			} else {
				this.commitGlobalTransaction(global);
			}
		} else if (global.isTransactionCompleting()) {
			global.commitCompleteTransaction();
		} else {
			global.commitTryingTransaction();
		}
	}

	private void commitGlobalTransaction(TransactionImpl transaction) throws HeuristicMixedException,
			HeuristicRollbackException, IllegalStateException, RollbackException, SecurityException, SystemException {
		try {
			this.handleCommitGlobalTransaction();
		} catch (Exception ex) {
			TransactionStatus transactionStatus = transaction.getTransactionStatus();
			if (transactionStatus.isCommitting() || transactionStatus.isCommitFail()) {
				CompensableCommittingException scex = new CompensableCommittingException();
				scex.initCause(ex);
				throw scex;
			} else {
				if (ex instanceof SecurityException) {
					throw (SecurityException) ex;
				} else if (ex instanceof HeuristicMixedException) {
					throw (HeuristicMixedException) ex;
				} else if (ex instanceof HeuristicRollbackException) {
					throw (HeuristicRollbackException) ex;
				} else if (ex instanceof RollbackException) {
					throw (RollbackException) ex;
				} else if (ex instanceof SystemException) {
					throw (SystemException) ex;
				} else if (ex instanceof RuntimeException) {
					throw (RuntimeException) ex;
				} else {
					RuntimeException rex = new RuntimeException();
					rex.initCause(ex);
					throw rex;
				}
			}
		}
	}

	private void commitRegularTransaction(TransactionImpl transaction) throws HeuristicMixedException,
			HeuristicRollbackException, IllegalStateException, RollbackException, SecurityException, SystemException {
		TransactionContext transactionContext = transaction.getTransactionContext();
		try {
			JtaTransactionImpl internalTransaction = transaction.getInternalTransaction();
			internalTransaction.commit();
		} finally {
			this.completeTransaction(transactionContext.getGlobalXid());
			this.unassociateTransaction();
		}
	}

	private void handleCommitGlobalTransaction() throws HeuristicMixedException, HeuristicRollbackException,
			IllegalStateException, RollbackException, SecurityException, SystemException {
		TransactionImpl global = this.getCurrentTransaction();
		JtaTransactionImpl internal = this.getTransaction();
		logger.info(String.format("[commit-global] global: %s, inner: %s", global, internal));
		try {
			global.commit();
			TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();
			transactionLogger.completeTransaction(global);
		} catch (HeuristicMixedException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (HeuristicRollbackException ex) {
			TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();
			transactionLogger.completeTransaction(global);

			throw ex;
		} catch (IllegalStateException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (RollbackException ex) {
			TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();
			transactionLogger.completeTransaction(global);

			throw ex;
		} catch (SecurityException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (SystemException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (RuntimeException ex) {
			registerErrorTransaction(global);
			throw ex;
		} finally {
			this.unassociateTransaction();
		}
	}

	public int getStatus() throws SystemException {
		JtaTransactionImpl tx = this.getTransaction();
		return tx == null ? Status.STATUS_NO_TRANSACTION : tx.getStatus();
	}

	public JtaTransactionImpl getTransaction() throws SystemException {
		Thread current = Thread.currentThread();
		AssociatedContext bctx = this.threadToTxnMap.get(current);
		if (bctx == null) {
			return null;
		} else if (bctx.getTransaction() == null) {
			return null;
		} else {
			return bctx.getTransaction().getInternalTransaction();
		}
	}

	public JtaTransactionImpl getInternalTransaction() {
		try {
			return this.getTransaction();
		} catch (SystemException ex) {
			return null;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public TransactionImpl getCurrentTransaction() {
		Thread current = Thread.currentThread();
		AssociatedContext bctx = this.threadToTxnMap.get(current);
		return bctx == null ? null : bctx.getTransaction();
	}

	public TransactionImpl getTransaction(XidImpl xid) {
		return this.transactionRepository.getTransaction(xid);
	}

	public void resume(Transaction tx) throws IllegalStateException, InvalidTransactionException, SystemException {
		if (TransactionImpl.class.isInstance(tx)) {
			TransactionImpl transaction = (TransactionImpl) tx;
			transaction.resumeAllResource();
			AssociatedContext bctx = new AssociatedContext();
			bctx.setThread(Thread.currentThread());
			this.threadToTxnMap.put(Thread.currentThread(), bctx);
		} else {
			throw new InvalidTransactionException();
		}
	}

	public void rollback() throws IllegalStateException, SystemException {
		TransactionImpl global = this.getCurrentTransaction();
		boolean coordinator = global.getTransactionContext().isCoordinator();

		if (coordinator) {
			if (global.isTransactionCompleting()) {
				global.rollbackCompleteTransaction();
			} else {
				this.rollbackGlobalTransaction();
			}
		} else if (global.isTransactionCompleting()) {
			global.rollbackCompleteTransaction();
		} else {
			global.rollbackTryingTransaction();
		}

	}

	private void rollbackGlobalTransaction() throws IllegalStateException, SystemException {
		TransactionImpl global = this.getCurrentTransaction();
		JtaTransactionImpl internal = this.getTransaction();
		logger.info(String.format("[rollback-global] global: %s, inner: %s", global, internal));
		try {
			global.rollback();
			TransactionLogger transactionLogger = this.transactionRepository.getTransactionLogger();
			transactionLogger.completeTransaction(global);
		} catch (IllegalStateException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (SystemException ex) {
			registerErrorTransaction(global);
			throw ex;
		} catch (RuntimeException ex) {
			registerErrorTransaction(global);
			throw ex;
		} finally {
			this.unassociateTransaction();
		}
	}

	public void setRollbackOnly() throws IllegalStateException, SystemException {
		TransactionImpl transaction = this.getCurrentTransaction();
		if (transaction == null) {
			throw new IllegalStateException();
		} else {
			transaction.setRollbackOnly();
		}
	}

	public void registerErrorTransaction(TransactionImpl tx) {
		TransactionContext context = tx.getTransactionContext();
		if (context.isCompensable()) {
			this.transactionRepository.putErrorTransaction(tx.getTransactionContext().getGlobalXid(), tx);
			this.transactionStatistic.fireCompleteFailure(tx);
		}
	}

	public void unregisterErrorTransaction(XidImpl globalXid) {
		this.transactionRepository.removeErrorTransaction(globalXid);
	}

	public TransactionImpl removeTransaction(XidImpl globalXid) {
		return this.transactionRepository.removeTransaction(globalXid);
	}

	public int getTransactionTimeout() {
		return this.transactionTimeout;
	}

	public void setTransactionTimeout(int timeout) throws SystemException {
		this.transactionTimeout = timeout;
	}

	public Transaction suspend() throws SystemException {
		AssociatedContext bctx = this.threadToTxnMap.remove(Thread.currentThread());
		if (bctx == null || bctx.getTransaction() == null) {
			throw new SystemException();
		}
		TransactionImpl transaction = bctx.getTransaction();
		transaction.suspendAllResource();
		return transaction;
	}

	public synchronized void registerSynchronization(AbstractSynchronization synchronization) {
		this.threadToSynMap.put(Thread.currentThread(), synchronization);
	}

	public synchronized void unRegisterSynchronization(AbstractSynchronization synchronization) {
		AbstractSynchronization sync = this.threadToSynMap.get(Thread.currentThread());
		if (synchronization.equals(sync)) {
			this.threadToSynMap.remove(Thread.currentThread());
		}
	}

	public void associateTransaction(TransactionImpl transaction) {
		AssociatedContext bctx = new AssociatedContext();
		bctx.setTransaction(transaction);
		bctx.setThread(Thread.currentThread());
		this.threadToTxnMap.put(bctx.getThread(), bctx);
	}

	public TransactionImpl unassociateTransaction() {
		AssociatedContext bctx = this.threadToTxnMap.remove(Thread.currentThread());
		return bctx == null ? null : bctx.getTransaction();
	}

	public TransactionImpl completeTransaction(XidImpl xid) {
		this.unregisterErrorTransaction(xid);
		return this.removeTransaction(xid);
	}

	public void processTimingTransaction() {
		// ConcurrentHashMap do not throw ConcurrentModificationException, and
		// its iterators are designed to be used by only one thread at a time.
		Iterator<AssociatedContext> itr = this.threadToTxnMap.values().iterator();
		while (itr.hasNext()) {
			AssociatedContext actx = itr.next();
			TransactionImpl transaction = actx.getTransaction();
			TransactionContext transactionContext = transaction.getTransactionContext();
			boolean recovery = transactionContext.isRecovery();
			if (transactionContext.isCoordinator()) {
				// ignore
			} else if (recovery) {
				// ignore
			} else if (actx.isExpired()) {
				TransactionStatus transactionStatus = transaction.getTransactionStatus();
				if (transactionStatus.isActive() || transactionStatus.isMarkedRollbackOnly()) {
					// ignore
				} else {
					synchronized (this.expiredTransactionSet) {
						this.expiredTransactionSet.remove(actx);
					}
				}
			} else {
				long expiredMillis = transactionContext.getExpiredTime();
				if (System.currentTimeMillis() >= expiredMillis) {
					actx.setExpired(true);
					synchronized (this.expiredTransactionSet) {
						this.expiredTransactionSet.add(actx);
					}
				}
			}
		}// end-while
	}

	public void processExpireTransaction() {
		synchronized (this.expiredTransactionSet) {
			Iterator<AssociatedContext> itr = this.expiredTransactionSet.iterator();
			while (itr.hasNext()) {
				AssociatedContext actx = itr.next();
				itr.remove();
				TransactionImpl transaction = actx.getTransaction();
				TransactionStatus transactionStatus = transaction.getTransactionStatus();
				TransactionContext transactionContext = transaction.getTransactionContext();
				XidImpl globalXid = transactionContext.getGlobalXid();
				if (transactionStatus.isActive() || transactionStatus.isMarkedRollbackOnly()) {
					try {
						this.associateTransaction(transaction);
						transaction.timingRollback();

						logger.info(String.format("[%s] rollback expired transaction: %s",
								ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()), true));
					} catch (HeuristicMixedException ex) {
						// ignore
					} catch (SystemException ex) {
						this.registerErrorTransaction(transaction);

						// ex.printStackTrace();
						logger.info(String.format("[%s] rollback expired transaction: %s",
								ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()), false));
					} catch (RuntimeException ex) {
						this.registerErrorTransaction(transaction);

						// ex.printStackTrace();
						logger.info(String.format("[%s] rollback expired transaction: %s",
								ByteUtils.byteArrayToString(globalXid.getGlobalTransactionId()), false));

					} finally {
						this.unassociateTransaction();
					}
				} // end-if
			}// end-while
		}// end-synchronized
	}

	public synchronized void notifyCompleteTransaction(TransactionImpl transaction) {
		this.cleanupProcesser.registerTransaction(transaction);
	}

	public PropagationKey getInstanceKey() {
		if (this.instanceKey == null) {
			this.initializeInstanceKey();
		}
		return this.instanceKey;
	}

	public synchronized void initializeInstanceKey() {
		if (this.instanceKey == null) {
			XidImpl xid = this.xidFactory.createGlobalXid();
			byte[] token = xid.getGlobalTransactionId();
			this.instanceKey = new PropagationKey(token);
		}
	}

	public XidFactory getXidFactory() {
		return xidFactory;
	}

	public void setXidFactory(XidFactoryImpl xidFactory) {
		this.xidFactory = xidFactory;
	}

	public TransactionRepository getTransactionRepository() {
		return transactionRepository;
	}

	public void setTransactionRepository(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
	}

	public CleanupProcesser getCleanupProcesser() {
		return cleanupProcesser;
	}

	public void setCleanupProcesser(CleanupProcesser cleanupProcesser) {
		this.cleanupProcesser = cleanupProcesser;
	}

	public TransactionStatistic getTransactionStatistic() {
		return transactionStatistic;
	}

	public void setTransactionStatistic(TransactionStatistic transactionStatistic) {
		this.transactionStatistic = transactionStatistic;
	}

	public TerminalKey getTerminalKey() {
		return this.xidFactory.getTerminalKey();
	}

}

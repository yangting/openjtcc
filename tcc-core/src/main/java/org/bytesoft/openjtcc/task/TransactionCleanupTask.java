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
package org.bytesoft.openjtcc.task;

import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bytesoft.openjtcc.TransactionImpl;
import org.bytesoft.openjtcc.TransactionManagerImpl;
import org.bytesoft.openjtcc.supports.schedule.CleanupProcesser;

public class TransactionCleanupTask implements Runnable, CleanupProcesser {
	public static final long SLEEP_UNIT_MILLIS = 100L;

	private boolean released;
	private final Lock lock = new ReentrantLock();
	private final Condition emptyCondition = this.lock.newCondition();
	private final Set<TransactionImpl> transactions = new HashSet<TransactionImpl>();

	public void run() {
		while (this.isActive()) {
			this.routineCleanup();
		}
	}

	public void registerTransaction(TransactionImpl transaction) {
		try {
			this.lock.lock();
			this.transactions.add(transaction);
			this.emptyCondition.signal();
		} finally {
			this.lock.unlock();
		}
	}

	public void routineCleanup() {
		processCleanup();
	}

	private void processCleanup() {
		Set<TransactionImpl> handles = new HashSet<TransactionImpl>();
		try {
			this.lock.lock();
			while (this.isActive() && this.transactions.isEmpty()) {
				this.await(SLEEP_UNIT_MILLIS);
			}
			if (this.isActive()) {
				Iterator<TransactionImpl> itr = this.transactions.iterator();
				for (int i = 0; i < 5000 && itr.hasNext(); i++) {
					TransactionImpl tx = itr.next();
					itr.remove();
					handles.add(tx);
				}
			}
		} finally {
			this.lock.unlock();
		}

		if (this.isActive()) {
			this.handleTransactionBatch(handles);
		}
	}

	private void handleTransactionBatch(Set<TransactionImpl> handles) {
		Iterator<TransactionImpl> itr = handles.iterator();
		while (itr.hasNext()) {
			TransactionImpl transaction = itr.next();
			itr.remove();
			boolean failure = true;
			TransactionManagerImpl transactionManager = transaction.getTransactionManager();
			try {
				transactionManager.associateTransaction(transaction);
				transaction.cleanup();
				failure = false;
			} catch (RemoteException ex) {
				// ex.printStackTrace();
			} catch (RuntimeException ex) {
				// ex.printStackTrace();
			} finally {
				transactionManager.unassociateTransaction();
				if (failure) {
					transactionManager.registerErrorTransaction(transaction);
				}// end-if(failure)
			}// end-finally
		}// end-while
	}

	private void await(long millis) {
		try {
			this.emptyCondition.await(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// ignore
		}
	}

	public void release() {
		this.released = true;
	}

	public boolean isActive() {
		return this.released == false;
	}

}

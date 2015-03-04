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

import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

import org.bytesoft.openjtcc.recovery.RecoveryManager;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionCleanupWork;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionRecoveryWork;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionTimingWork;

/**
 * 用于支持JSR-236 (Concurrency Utilities For JavaEE)。
 * 
 * 使用Spring时，若AppServer不支持JSR-236，则优先使用TransactionResourceAdapter。
 */
public class TransactionExecutionDeamon {
	private static final Logger logger = Logger.getLogger("openjtcc");
	private static final long SECOND_MILLIS = 1000L;
	private static final long MAX_WAIT_MILLIS = SECOND_MILLIS * 30;

	private ExecutorService executorService;

	private RecoveryManager recoveryManager;

	private TransactionRecoveryWork recoveryWork;
	private TransactionTimingWork timingWork;
	private TransactionCleanupWork cleanupWork;

	public void start() throws IllegalStateException {
		this.recoveryManager.reconstruct();

		boolean recoverySuccess = false;

		try {
			this.executorService.submit(this.recoveryWork);
			recoverySuccess = true;
		} catch (RuntimeException ex) {
			this.recoveryWork.setCompleted(true);
			ex.printStackTrace();
		}

		boolean timingSuccess = false;
		try {
			this.executorService.submit(this.timingWork);
			timingSuccess = true;
		} catch (RuntimeException ex) {
			this.timingWork.setCompleted(true);
			ex.printStackTrace();
		}

		boolean cleanupSuccess = false;
		try {
			this.executorService.submit(this.cleanupWork);
			cleanupSuccess = true;
		} catch (RuntimeException ex) {
			this.cleanupWork.setCompleted(true);
			ex.printStackTrace();
		}

		if ((recoverySuccess && timingSuccess && cleanupSuccess) == false) {
			throw new IllegalStateException();
		}
		logger.info("[ResourceAdapter] start successful");
	}

	public void stop() {
		logger.info("[ResourceAdapter] stop");
		boolean success = this.processStop();
		if (success) {
			// ignore
		} else {
			throw new RuntimeException();
		}
	}

	private boolean processStop() {
		boolean success = false;
		this.recoveryWork.release();
		this.timingWork.release();
		this.cleanupWork.release();

		long begin = System.currentTimeMillis();
		boolean executing = this.recoveryWork.isCompleted() == false || this.timingWork.isCompleted() == false
				|| this.cleanupWork.isCompleted() == false;
		while (executing) {
			this.sleepMillis(SECOND_MILLIS);
			if (this.recoveryWork.isCompleted() && this.timingWork.isCompleted() && this.cleanupWork.isCompleted()) {
				executing = false;
				success = true;
			} else if ((System.currentTimeMillis() - begin) > MAX_WAIT_MILLIS) {
				executing = false;
			}
		}
		logger.info(String.format("[ResourceAdapter] stop cost: %s", System.currentTimeMillis() - begin));
		return success;
	}

	private void sleepMillis(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// ignore
		}
	}

	public RecoveryManager getRecoveryManager() {
		return recoveryManager;
	}

	public void setRecoveryManager(RecoveryManager recoveryManager) {
		this.recoveryManager = recoveryManager;
	}

	public TransactionRecoveryWork getRecoveryWork() {
		return recoveryWork;
	}

	public void setRecoveryWork(TransactionRecoveryWork recoveryWork) {
		this.recoveryWork = recoveryWork;
	}

	public TransactionTimingWork getTimingWork() {
		return timingWork;
	}

	public void setTimingWork(TransactionTimingWork timingWork) {
		this.timingWork = timingWork;
	}

	public TransactionCleanupWork getCleanupWork() {
		return cleanupWork;
	}

	public void setCleanupWork(TransactionCleanupWork cleanupWork) {
		this.cleanupWork = cleanupWork;
	}

	public void setExecutorService(ExecutorService executorService) {
		this.executorService = executorService;
	}

}

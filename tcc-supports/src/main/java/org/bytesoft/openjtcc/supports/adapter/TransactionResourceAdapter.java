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
package org.bytesoft.openjtcc.supports.adapter;

import java.util.logging.Logger;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.WorkException;
import javax.resource.spi.work.WorkListener;
import javax.resource.spi.work.WorkManager;
import javax.transaction.xa.XAResource;

import org.bytesoft.openjtcc.recovery.RecoveryManager;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionCleanupWork;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionRecoveryWork;
import org.bytesoft.openjtcc.supports.adapter.work.TransactionTimingWork;

public class TransactionResourceAdapter implements ResourceAdapter {
	private static final Logger logger = Logger.getLogger("openjtcc");
	private static final long SECOND_MILLIS = 1000L;
	private static final long MAX_WAIT_MILLIS = SECOND_MILLIS * 30;

	private BootstrapContext bootstrapContext;
	private RecoveryManager recoveryManager;

	private TransactionRecoveryWork recoveryWork;
	private TransactionTimingWork timingWork;
	private TransactionCleanupWork cleanupWork;

	public void endpointActivation(MessageEndpointFactory msgEndpointFactory, ActivationSpec activation)
			throws ResourceException {
	}

	public void endpointDeactivation(MessageEndpointFactory msgEndpointFactory, ActivationSpec activation) {
	}

	public XAResource[] getXAResources(ActivationSpec[] activation) throws ResourceException {
		throw new NotSupportedException();
	}

	public void start(BootstrapContext context) throws ResourceAdapterInternalException {
		this.bootstrapContext = context;
		this.recoveryManager.reconstruct();

		WorkManager workManager = this.bootstrapContext.getWorkManager();
		boolean recoverySuccess = false;
		try {
			WorkListener recoveryListener = (WorkListener) this.recoveryWork;
			workManager.startWork(this.recoveryWork, 0, null, recoveryListener);
			recoverySuccess = true;
		} catch (WorkException ex) {
			this.recoveryWork.setCompleted(true);
			ex.printStackTrace();
		}

		boolean timingSuccess = false;
		try {
			WorkListener timingListener = (WorkListener) this.timingWork;
			workManager.startWork(this.timingWork, 0, null, timingListener);
			timingSuccess = true;
		} catch (WorkException ex) {
			this.timingWork.setCompleted(true);
			ex.printStackTrace();
		}

		boolean cleanupSuccess = false;
		try {
			WorkListener cleanupListener = (WorkListener) this.cleanupWork;
			workManager.startWork(this.cleanupWork, 0, null, cleanupListener);
			cleanupSuccess = true;
		} catch (WorkException ex) {
			this.cleanupWork.setCompleted(true);
			ex.printStackTrace();
		}

		if ((recoverySuccess && timingSuccess && cleanupSuccess) == false) {
			throw new ResourceAdapterInternalException();
		}
		logger.info("[ResourceAdapter] start successful");
	}

	public void stop() {
		boolean success = false;
		try {
			success = this.processStop();
		} catch (RuntimeException rex) {
			rex.printStackTrace();
		}

		if (success) {
			// ignore
		} else {
			logger.warning("[ResourceAdapter] stop failure!");
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

		long costMillis = System.currentTimeMillis() - begin;
		logger.info(String.format("[ResourceAdapter] stop successful. cost-millis: %s", costMillis));
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

}
